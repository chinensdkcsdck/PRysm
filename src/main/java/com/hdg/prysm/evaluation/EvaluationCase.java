package com.hdg.prysm.evaluation;

import java.util.List;

public record EvaluationCase(
        String id,
        List<EvaluationFinding> expected,
        List<EvaluationFinding> predicted,
        int promptTokens,
        int completionTokens
) {

    public EvaluationCase {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Evaluation case id must not be blank");
        }
        if (promptTokens < 0 || completionTokens < 0) {
            throw new IllegalArgumentException("Evaluation token counts must not be negative");
        }
        expected = expected == null ? List.of() : List.copyOf(expected);
        predicted = predicted == null ? List.of() : List.copyOf(predicted);
    }
}
