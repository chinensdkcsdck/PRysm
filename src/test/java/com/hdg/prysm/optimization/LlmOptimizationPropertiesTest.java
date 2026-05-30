package com.hdg.prysm.optimization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmOptimizationPropertiesTest {

    @Test
    void shouldUseBaselineDefaults() {
        LlmOptimizationProperties properties = new LlmOptimizationProperties(
                "baseline",
                0,
                false,
                800,
                false,
                "fast_model",
                "qwen-turbo",
                false
        );

        assertEquals("baseline", properties.getGroup());
        assertEquals(0, properties.getRolloutPercent());
        assertFalse(properties.isMaxOutputTokensEnabled());
        assertEquals(800, properties.getMaxOutputTokens());
        assertFalse(properties.isFastPathEnabled());
        assertEquals("fast_model", properties.getFastPathMode());
        assertEquals("qwen-turbo", properties.getFastModel());
        assertFalse(properties.isCompactPromptEnabled());
    }

    @Test
    void shouldNormalizeUnknownGroupToBaseline() {
        LlmOptimizationProperties properties = new LlmOptimizationProperties(
                "unknown",
                0,
                true,
                600,
                true,
                "fast_model",
                "qwen-turbo",
                true
        );

        assertEquals("baseline", properties.getGroup());
        assertTrue(properties.isMaxOutputTokensEnabled());
        assertTrue(properties.isFastPathEnabled());
        assertTrue(properties.isCompactPromptEnabled());
    }

    @Test
    void shouldRejectInvalidRolloutPercent() {
        assertThrows(IllegalArgumentException.class, () -> new LlmOptimizationProperties(
                "baseline",
                101,
                false,
                800,
                false,
                "fast_model",
                "qwen-turbo",
                false
        ));
    }

    @Test
    void shouldRejectInvalidMaxOutputTokens() {
        assertThrows(IllegalArgumentException.class, () -> new LlmOptimizationProperties(
                "baseline",
                0,
                false,
                0,
                false,
                "fast_model",
                "qwen-turbo",
                false
        ));
    }
}
