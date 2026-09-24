package com.hdg.prysm.agentic;

import com.hdg.prysm.execution.LlmReviewResult;
import com.hdg.prysm.execution.LlmTokenUsage;
import com.hdg.prysm.execution.PromptPayload;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewFinding;
import com.hdg.prysm.llm.LlmReviewRunner;
import com.hdg.prysm.mcp.AgenticRunStore;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public abstract class PromptedReviewAgent implements ReviewAgent {

    private final AgenticRunStore store;
    private final LlmReviewRunner llmReviewRunner;

    protected PromptedReviewAgent(AgenticRunStore store, LlmReviewRunner llmReviewRunner) {
        this.store = store;
        this.llmReviewRunner = llmReviewRunner;
    }

    protected abstract String instruction();
    protected abstract Set<String> acceptedCategories();

    @Override
    public Map<String, Object> execute(String runId) {
        AgenticRunStore.RunContext context = store.require(runId);
        ReviewExecutionInput input = specializedInput(context.input());
        LlmReviewResult raw = llmReviewRunner.run(input);
        List<ReviewFinding> findings = raw.getFindings().stream()
                .filter(finding -> acceptedCategories().contains(normalize(finding.getCategory())))
                .toList();
        LlmReviewResult result = new LlmReviewResult(findings, raw.getSummary(), raw.getRawResponse(), raw.getTokenUsage());
        context.put(role(), result);
        consumeTokens(context, raw.getTokenUsage(), input.getPromptPayload().getUserPrompt().length());
        return Map.of(
                "completedAgents", List.of(role().name()),
                "agentResults", List.of(result),
                "toolCalls", context.budget().toolCalls(),
                "usedTokens", context.budget().usedTokens()
        );
    }

    private ReviewExecutionInput specializedInput(ReviewExecutionInput input) {
        PromptPayload original = input.getPromptPayload();
        PromptPayload prompt = new PromptPayload(
                original.getSystemPrompt() + "\n\nAgent responsibility:\n" + instruction(),
                original.getUserPrompt(),
                original.getOutputSchema()
        );
        return new ReviewExecutionInput(input.getPrContext(), input.getDiff(), input.getFiles(), input.getContextStatus(), prompt);
    }

    private static void consumeTokens(AgenticRunStore.RunContext context, LlmTokenUsage usage, int promptCharacters) {
        int tokens = usage != null && usage.getTotalTokens() != null
                ? usage.getTotalTokens()
                : (int) Math.ceil(promptCharacters / 4.0);
        context.budget().consumeTokens(tokens);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
