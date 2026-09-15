package com.hdg.prysm.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewEvaluationServiceTest {

    @Test
    void shouldComputeOneToOneQualityPositionDuplicateAndTokenMetrics() {
        EvaluationDataset dataset = new EvaluationDataset(List.of(new EvaluationCase(
                "pr-1",
                List.of(
                        finding("null-check", "src/A.java", 10, "bug"),
                        finding("auth-bypass", "src/A.java", 20, "security"),
                        finding("missing-test", "src/B.java", 5, "test")
                ),
                List.of(
                        finding("null-check", "src/A.java", 10, "bug"),
                        finding("auth-bypass", "src/A.java", 19, "security"),
                        finding("null-check", "src/A.java", 10, "bug"),
                        finding("wrong-issue", "src/B.java", 5, "bug")
                ),
                200,
                100
        )));

        EvaluationReport report = new ReviewEvaluationService().evaluate(dataset, 2);

        assertEquals(2, report.truePositives());
        assertEquals(2, report.falsePositives());
        assertEquals(1, report.falseNegatives());
        assertEquals(1, report.exactPositionMatches());
        assertEquals(1, report.duplicatePredictions());
        assertEquals(0.5, report.precision(), 0.0001);
        assertEquals(2.0 / 3.0, report.recall(), 0.0001);
        assertEquals(4.0 / 7.0, report.f1(), 0.0001);
        assertEquals(0.5, report.positionAccuracy(), 0.0001);
        assertEquals(0.25, report.duplicateRate(), 0.0001);
        assertEquals(150.0, report.tokensPerTruePositive(), 0.0001);
    }

    private static EvaluationFinding finding(String issueKey, String path, int line, String category) {
        return new EvaluationFinding(issueKey, path, line, category);
    }
}
