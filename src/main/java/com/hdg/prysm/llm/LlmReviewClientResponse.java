package com.hdg.prysm.llm;

import com.hdg.prysm.execution.LlmTokenUsage;

/**
 * Raw LLM response content plus provider usage metadata.
 */
public class LlmReviewClientResponse {

    private final String content;
    private final LlmTokenUsage tokenUsage;

    public LlmReviewClientResponse(String content) {
        this(content, null);
    }

    public LlmReviewClientResponse(String content, LlmTokenUsage tokenUsage) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("LLM response content must not be blank");
        }

        this.content = content;
        this.tokenUsage = tokenUsage;
    }

    public static LlmReviewClientResponse contentOnly(String content) {
        return new LlmReviewClientResponse(content);
    }

    public String getContent() {
        return content;
    }

    public LlmTokenUsage getTokenUsage() {
        return tokenUsage;
    }
}
