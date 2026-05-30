package com.hdg.prysm.optimization;

/**
 * Per-review optimization decision used by the LLM client and trace reporter.
 */
public class LlmOptimizationDecision {

    private final String originalModel;
    private final String effectiveModel;
    private final boolean fastPathMatched;
    private final String fastPathReason;

    private LlmOptimizationDecision(
            String originalModel,
            String effectiveModel,
            boolean fastPathMatched,
            String fastPathReason
    ) {
        this.originalModel = originalModel;
        this.effectiveModel = effectiveModel;
        this.fastPathMatched = fastPathMatched;
        this.fastPathReason = fastPathReason;
    }

    public static LlmOptimizationDecision baseline(String model) {
        return new LlmOptimizationDecision(model, model, false, null);
    }

    public static LlmOptimizationDecision fastPath(String originalModel, String effectiveModel, String reason) {
        return new LlmOptimizationDecision(originalModel, effectiveModel, true, reason);
    }

    public String getOriginalModel() {
        return originalModel;
    }

    public String getEffectiveModel() {
        return effectiveModel == null || effectiveModel.isBlank() ? originalModel : effectiveModel;
    }

    public boolean isFastPathMatched() {
        return fastPathMatched;
    }

    public String getFastPathReason() {
        return fastPathReason;
    }
}
