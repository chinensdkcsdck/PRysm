package com.hdg.prysm.optimization;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Runtime switches for LLM optimization experiments.
 *
 * All switches default to disabled so the existing review behavior remains unchanged.
 */
@Component
public class LlmOptimizationProperties {

    private final LlmOptimizationExperimentGroup group;
    private final int rolloutPercent;
    private final boolean maxOutputTokensEnabled;
    private final int maxOutputTokens;
    private final boolean fastPathEnabled;
    private final String fastPathMode;
    private final String fastModel;
    private final boolean compactPromptEnabled;

    public LlmOptimizationProperties(
            @Value("${prysm.optimization.group:baseline}") String group,
            @Value("${prysm.optimization.rollout-percent:0}") int rolloutPercent,
            @Value("${prysm.optimization.max-output-tokens.enabled:false}") boolean maxOutputTokensEnabled,
            @Value("${prysm.optimization.max-output-tokens.value:800}") int maxOutputTokens,
            @Value("${prysm.optimization.fast-path.enabled:false}") boolean fastPathEnabled,
            @Value("${prysm.optimization.fast-path.mode:fast_model}") String fastPathMode,
            @Value("${prysm.optimization.fast-path.fast-model:qwen-turbo}") String fastModel,
            @Value("${prysm.optimization.compact-prompt.enabled:false}") boolean compactPromptEnabled
    ) {
        if (rolloutPercent < 0 || rolloutPercent > 100) {
            throw new IllegalArgumentException("Optimization rollout percent must be between 0 and 100");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("Maximum output tokens must be positive");
        }
        if (fastPathMode == null || fastPathMode.isBlank()) {
            throw new IllegalArgumentException("Fast path mode must not be blank");
        }
        if (fastModel == null || fastModel.isBlank()) {
            throw new IllegalArgumentException("Fast path model must not be blank");
        }

        this.group = LlmOptimizationExperimentGroup.fromValue(group);
        this.rolloutPercent = rolloutPercent;
        this.maxOutputTokensEnabled = maxOutputTokensEnabled;
        this.maxOutputTokens = maxOutputTokens;
        this.fastPathEnabled = fastPathEnabled;
        this.fastPathMode = fastPathMode.trim();
        this.fastModel = fastModel.trim();
        this.compactPromptEnabled = compactPromptEnabled;
    }

    public String getGroup() {
        return group.getValue();
    }

    public LlmOptimizationExperimentGroup getExperimentGroup() {
        return group;
    }

    public int getRolloutPercent() {
        return rolloutPercent;
    }

    public boolean isMaxOutputTokensEnabled() {
        return maxOutputTokensEnabled;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public boolean isFastPathEnabled() {
        return fastPathEnabled;
    }

    public String getFastPathMode() {
        return fastPathMode;
    }

    public String getFastModel() {
        return fastModel;
    }

    public boolean isCompactPromptEnabled() {
        return compactPromptEnabled;
    }
}
