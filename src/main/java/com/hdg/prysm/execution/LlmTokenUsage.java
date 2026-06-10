package com.hdg.prysm.execution;

/**
 * Token usage reported by an LLM provider.
 */
public class LlmTokenUsage {

    private final Integer promptTokens;
    private final Integer completionTokens;
    private final Integer totalTokens;

    public LlmTokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        validateTokenCount(promptTokens, "promptTokens");
        validateTokenCount(completionTokens, "completionTokens");
        validateTokenCount(totalTokens, "totalTokens");
        if (promptTokens == null && completionTokens == null && totalTokens == null) {
            throw new IllegalArgumentException("At least one token usage value must be present");
        }

        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    private static void validateTokenCount(Integer value, String name) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
