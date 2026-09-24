package com.hdg.prysm.execution;

/**
 * 交给 LLM Review 阶段使用的完整 prompt 载荷。
 */
public class PromptPayload implements java.io.Serializable {

    private final String systemPrompt;
    private final String userPrompt;
    private final String outputSchema;

    public PromptPayload(String systemPrompt, String userPrompt, String outputSchema) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("System prompt must not be blank");
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("User prompt must not be blank");
        }
        if (outputSchema == null || outputSchema.isBlank()) {
            throw new IllegalArgumentException("Output schema must not be blank");
        }

        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.outputSchema = outputSchema;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public String getOutputSchema() {
        return outputSchema;
    }
}
