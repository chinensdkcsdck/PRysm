package com.hdg.prysm.evaluation;

import com.hdg.prysm.execution.ReviewFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewEvaluationServiceTest {

    @Test
    void calculatesDeterministicLocationMetricsSeparatelyFromJudge() {
        LlmReviewJudge judge = mock(LlmReviewJudge.class);
        when(judge.evaluate(org.mockito.ArgumentMatchers.anyList())).thenReturn(null);
        ReviewFinding located = new ReviewFinding(
                "llm", "HIGH", "src/A.java", 10, 10, "RIGHT", 10, "RIGHT",
                "Risk", "Reason", "Fix", "RULE", "HIGH", "BUG");
        ReviewFinding fileOnly = new ReviewFinding(
                "llm", "MEDIUM", "src/B.java", null, null, null, null, null,
                "Risk 2", "Reason 2", "Fix 2", "RULE_2", "MEDIUM", "BUG");

        ReviewEvaluation result = new ReviewEvaluationService(judge)
                .evaluate(List.of(located, fileOnly), 4, 1200, false);

        assertThat(result.findingCount()).isEqualTo(2);
        assertThat(result.locatedFindingCount()).isEqualTo(1);
        assertThat(result.locationRate()).isEqualTo(0.5);
        assertThat(result.toolCalls()).isEqualTo(4);
    }
}
