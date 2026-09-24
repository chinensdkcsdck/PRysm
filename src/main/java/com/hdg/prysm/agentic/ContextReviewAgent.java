package com.hdg.prysm.agentic;

import com.hdg.prysm.mcp.AgenticRunStore;
import com.hdg.prysm.mcp.McpToolRegistry;
import com.hdg.prysm.mcp.McpToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ContextReviewAgent implements ReviewAgent {

    private final AgenticRunStore store;
    private final McpToolRegistry tools;

    public ContextReviewAgent(AgenticRunStore store, McpToolRegistry tools) {
        this.store = store;
        this.tools = tools;
    }

    @Override public ReviewAgentRole role() { return ReviewAgentRole.CONTEXT; }

    @Override
    public Map<String, Object> execute(String runId) {
        AgenticRunStore.RunContext context = store.require(runId);
        McpToolResult snapshot = tools.invoke("prysm.review.snapshot", Map.of("runId", runId), context.budget());
        if (!snapshot.success()) {
            throw new IllegalStateException(snapshot.message());
        }
        return Map.of(
                "completedAgents", List.of(role().name()),
                "toolCalls", context.budget().toolCalls(),
                "usedTokens", context.budget().usedTokens(),
                "contextReady", true
        );
    }
}
