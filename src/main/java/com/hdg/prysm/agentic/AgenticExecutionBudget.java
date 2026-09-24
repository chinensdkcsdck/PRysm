package com.hdg.prysm.agentic;

import java.util.concurrent.atomic.AtomicInteger;

public class AgenticExecutionBudget implements java.io.Serializable {

    private final int maxToolCalls;
    private final int maxTokens;
    private final AtomicInteger toolCalls = new AtomicInteger();
    private final AtomicInteger usedTokens = new AtomicInteger();

    public AgenticExecutionBudget(int maxToolCalls, int maxTokens) {
        this.maxToolCalls = maxToolCalls;
        this.maxTokens = maxTokens;
    }

    public void consumeToolCall() {
        if (toolCalls.incrementAndGet() > maxToolCalls) {
            throw new AgenticBudgetExceededException("MCP tool call limit exceeded");
        }
    }

    public void consumeTokens(int tokens) {
        if (tokens > 0 && usedTokens.addAndGet(tokens) > maxTokens) {
            throw new AgenticBudgetExceededException("Token budget exceeded");
        }
    }

    public int toolCalls() { return toolCalls.get(); }
    public int usedTokens() { return usedTokens.get(); }
}
