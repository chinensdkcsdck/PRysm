package com.hdg.prysm.evaluation;

import java.util.List;

public record EvaluationDataset(List<EvaluationCase> cases) {

    public EvaluationDataset {
        cases = cases == null ? List.of() : List.copyOf(cases);
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("Evaluation dataset must contain at least one case");
        }
    }
}
