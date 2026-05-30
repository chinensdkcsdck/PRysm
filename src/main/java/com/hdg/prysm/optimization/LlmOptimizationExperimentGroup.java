package com.hdg.prysm.optimization;

import java.util.Locale;

/**
 * Names the LLM optimization experiment group used for rollout comparison.
 */
public enum LlmOptimizationExperimentGroup {

    BASELINE("baseline"),
    MAX_OUTPUT_TOKENS("exp_1_max_output_tokens"),
    FAST_PATH("exp_2_fast_path"),
    COMPACT_PROMPT("exp_3_compact_prompt"),
    COMBINED("combined");

    private final String value;

    LlmOptimizationExperimentGroup(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static LlmOptimizationExperimentGroup fromValue(String value) {
        if (value == null || value.isBlank()) {
            return BASELINE;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (LlmOptimizationExperimentGroup group : values()) {
            if (group.value.equals(normalized)) {
                return group;
            }
        }
        return BASELINE;
    }
}
