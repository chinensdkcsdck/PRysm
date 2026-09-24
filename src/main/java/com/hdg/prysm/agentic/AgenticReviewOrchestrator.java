package com.hdg.prysm.agentic;

import com.hdg.prysm.execution.LlmReviewResult;
import com.hdg.prysm.execution.LlmTokenUsage;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewFinding;
import com.hdg.prysm.execution.RuleEngineResult;
import com.hdg.prysm.evaluation.ReviewEvaluation;
import com.hdg.prysm.evaluation.ReviewEvaluationService;
import com.hdg.prysm.mcp.AgenticRunStore;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.FileSystemSaver;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class AgenticReviewOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgenticReviewOrchestrator.class);

    private final AgenticReviewProperties properties;
    private final ReviewSupervisor supervisor;
    private final AgenticRunStore runStore;
    private final Map<ReviewAgentRole, ReviewAgent> agents;
    private final ReviewEvaluationService evaluationService;
    private final CompiledGraph<AgenticReviewState> graph;

    public AgenticReviewOrchestrator(
            AgenticReviewProperties properties,
            ReviewSupervisor supervisor,
            AgenticRunStore runStore,
            ReviewEvaluationService evaluationService,
            Collection<ReviewAgent> agents,
            @Value("${prysm.agentic.checkpoint-directory:${java.io.tmpdir}/prysm-checkpoints}") String checkpointDirectory
    ) throws Exception {
        this.properties = properties;
        this.supervisor = supervisor;
        this.runStore = runStore;
        this.evaluationService = evaluationService;
        Map<ReviewAgentRole, ReviewAgent> indexed = new EnumMap<>(ReviewAgentRole.class);
        for (ReviewAgent agent : agents) indexed.put(agent.role(), agent);
        this.agents = Map.copyOf(indexed);
        this.graph = buildGraph(Path.of(checkpointDirectory));
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public LlmReviewResult review(ReviewExecutionInput input, RuleEngineResult rules) {
        String runId = stableRunId(input);
        AgenticExecutionBudget budget = new AgenticExecutionBudget(
                properties.getMaxToolCalls(), properties.getMaxTokenBudget());
        runStore.put(runId, new AgenticRunStore.RunContext(input, rules, budget));
        try {
            RunnableConfig config = RunnableConfig.builder().threadId(runId).build();
            AgenticReviewState finalState = graph.invoke(Map.of("runId", runId), config)
                    .orElseThrow(() -> new IllegalStateException("Agentic review graph returned no state"));
            LlmReviewResult result = combine(finalState.agentResults());
            ReviewEvaluation evaluation = evaluationService.evaluate(
                    result.getFindings(), finalState.toolCalls(), finalState.usedTokens(), finalState.budgetExceeded());
            log.info(
                    "Agentic review evaluated: findings={}, locationRate={}, toolCalls={}, usedTokens={}, budgetExceeded={}",
                    evaluation.findingCount(), evaluation.locationRate(), evaluation.toolCalls(),
                    evaluation.usedTokens(), evaluation.budgetExceeded()
            );
            return result;
        } finally {
            runStore.remove(runId);
        }
    }

    private CompiledGraph<AgenticReviewState> buildGraph(Path checkpointDirectory) throws Exception {
        Files.createDirectories(checkpointDirectory);
        Map<String, Channel<?>> channels = new LinkedHashMap<>();
        channels.put("runId", Channels.base(() -> ""));
        channels.put("selectedAgents", Channels.base((Supplier<List<String>>) List::of));
        channels.put("completedAgents", Channels.appender(ArrayList::new));
        channels.put("toolCalls", Channels.base(() -> 0));
        channels.put("usedTokens", Channels.base(() -> 0));
        channels.put("budgetExceeded", Channels.base(() -> false));
        channels.put("agentResults", Channels.appender(ArrayList::new));

        ObjectStreamStateSerializer<AgenticReviewState> serializer =
                new ObjectStreamStateSerializer<>(AgenticReviewState::new);
        FileSystemSaver saver = new FileSystemSaver(checkpointDirectory, serializer);
        StateGraph<AgenticReviewState> stateGraph = new StateGraph<>(channels, serializer);
        stateGraph.addNode("context", node(ReviewAgentRole.CONTEXT));
        stateGraph.addNode("supervisor", AsyncNodeAction.node_async(this::supervise));
        stateGraph.addNode("security", node(ReviewAgentRole.SECURITY));
        stateGraph.addNode("quality", node(ReviewAgentRole.QUALITY));
        stateGraph.addNode("test", node(ReviewAgentRole.TEST));
        stateGraph.addEdge(StateGraph.START, "context");
        stateGraph.addEdge("context", "supervisor");
        stateGraph.addEdge("supervisor", "security");
        stateGraph.addEdge("security", "quality");
        stateGraph.addEdge("quality", "test");
        stateGraph.addEdge("test", StateGraph.END);

        CompiledGraph<AgenticReviewState> compiled = stateGraph.compile(
                CompileConfig.builder().checkpointSaver(saver).releaseThread(true).build());
        compiled.setMaxIterations(properties.getMaxIterations());
        return compiled;
    }

    private AsyncNodeAction<AgenticReviewState> node(ReviewAgentRole role) {
        return AsyncNodeAction.node_async(state -> {
            if (role != ReviewAgentRole.CONTEXT && !state.selectedAgents().contains(role.name())) {
                return Map.of();
            }
            ReviewAgent agent = agents.get(role);
            if (agent == null) throw new IllegalStateException("Missing review agent: " + role);
            try {
                return agent.execute(state.runId());
            } catch (AgenticBudgetExceededException exception) {
                return Map.of("budgetExceeded", true);
            }
        });
    }

    private Map<String, Object> supervise(AgenticReviewState state) {
        AgenticRunStore.RunContext context = runStore.require(state.runId());
        List<String> selected = supervisor.plan(context.input(), context.rules()).stream().map(Enum::name).toList();
        return Map.of("selectedAgents", selected);
    }

    private static String stableRunId(ReviewExecutionInput input) {
        String owner = input.getPrContext().getOwner();
        String repository = input.getPrContext().getRepository();
        int number = input.getPrContext().getPullRequestNumber();
        String inputFingerprint = Integer.toUnsignedString(input.getPromptPayload().getUserPrompt().hashCode(), 36);
        return owner + "-" + repository + "-" + number + "-" + inputFingerprint;
    }

    private static LlmReviewResult combine(Collection<LlmReviewResult> results) {
        List<ReviewFinding> findings = results.stream().flatMap(result -> result.getFindings().stream()).toList();
        String summary = results.stream().map(LlmReviewResult::getSummary)
                .filter(value -> value != null && !value.isBlank()).distinct().reduce((a, b) -> a + " " + b).orElse("");
        int prompt = 0;
        int completion = 0;
        int total = 0;
        boolean hasUsage = false;
        for (LlmReviewResult result : results) {
            LlmTokenUsage usage = result.getTokenUsage();
            if (usage == null) continue;
            hasUsage = true;
            prompt += usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
            completion += usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
            total += usage.getTotalTokens() == null ? 0 : usage.getTotalTokens();
        }
        return new LlmReviewResult(findings, summary, null,
                hasUsage ? new LlmTokenUsage(prompt, completion, total) : null);
    }
}
