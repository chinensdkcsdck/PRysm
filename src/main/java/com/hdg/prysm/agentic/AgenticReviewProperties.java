package com.hdg.prysm.agentic;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AgenticReviewProperties {

    private final boolean enabled;
    private final int maxIterations;
    private final int maxToolCalls;
    private final int maxTokenBudget;
    private final int toolTimeoutSeconds;
    private final int toolMaxAttempts;

    public AgenticReviewProperties(
            @Value("${prysm.agentic.enabled:true}") boolean enabled,
            @Value("${prysm.agentic.max-iterations:12}") int maxIterations,
            @Value("${prysm.agentic.max-tool-calls:30}") int maxToolCalls,
            @Value("${prysm.agentic.max-token-budget:131072}") int maxTokenBudget,
            @Value("${prysm.agentic.tool-timeout-seconds:20}") int toolTimeoutSeconds,
            @Value("${prysm.agentic.tool-max-attempts:3}") int toolMaxAttempts
    ) {
        if (maxIterations < 1 || maxToolCalls < 1 || maxTokenBudget < 1
                || toolTimeoutSeconds < 1 || toolMaxAttempts < 1) {
            throw new IllegalArgumentException("Agentic review limits must be positive");
        }
        this.enabled = enabled;
        this.maxIterations = maxIterations;
        this.maxToolCalls = maxToolCalls;
        this.maxTokenBudget = maxTokenBudget;
        this.toolTimeoutSeconds = toolTimeoutSeconds;
        this.toolMaxAttempts = toolMaxAttempts;
    }

    public boolean isEnabled() { return enabled; }
    public int getMaxIterations() { return maxIterations; }
    public int getMaxToolCalls() { return maxToolCalls; }
    public int getMaxTokenBudget() { return maxTokenBudget; }
    public int getToolTimeoutSeconds() { return toolTimeoutSeconds; }
    public int getToolMaxAttempts() { return toolMaxAttempts; }
}
