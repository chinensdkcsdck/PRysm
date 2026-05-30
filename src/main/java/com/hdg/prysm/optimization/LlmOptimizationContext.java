package com.hdg.prysm.optimization;

import org.springframework.stereotype.Component;

/**
 * Holds per-review LLM optimization decisions for downstream components and trace reporting.
 */
@Component
public class LlmOptimizationContext {

    private LlmOptimizationDecision currentDecision = LlmOptimizationDecision.baseline(null);
    private Integer originalPromptCharacters;
    private Integer compactPromptCharacters;

    public LlmOptimizationDecision getCurrentDecision() {
        return currentDecision;
    }

    public void setCurrentDecision(LlmOptimizationDecision currentDecision) {
        if (currentDecision == null) {
            throw new IllegalArgumentException("Optimization decision must not be null");
        }
        this.currentDecision = currentDecision;
    }

    public Integer getOriginalPromptCharacters() {
        return originalPromptCharacters;
    }

    public Integer getCompactPromptCharacters() {
        return compactPromptCharacters;
    }

    public Integer getPromptCharactersSaved() {
        if (originalPromptCharacters == null || compactPromptCharacters == null) {
            return null;
        }
        return Math.max(0, originalPromptCharacters - compactPromptCharacters);
    }

    public Double getPromptCompactRatio() {
        if (originalPromptCharacters == null || compactPromptCharacters == null || originalPromptCharacters == 0) {
            return null;
        }
        return compactPromptCharacters / (double) originalPromptCharacters;
    }

    public void recordPromptCharacters(int originalPromptCharacters, int effectivePromptCharacters) {
        if (originalPromptCharacters < 0 || effectivePromptCharacters < 0) {
            throw new IllegalArgumentException("Prompt character counts must not be negative");
        }
        this.originalPromptCharacters = originalPromptCharacters;
        this.compactPromptCharacters = effectivePromptCharacters;
    }
}
