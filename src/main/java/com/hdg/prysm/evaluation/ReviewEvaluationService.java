package com.hdg.prysm.evaluation;

import com.hdg.prysm.execution.ReviewFinding;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReviewEvaluationService {

    private final LlmReviewJudge judge;

    public ReviewEvaluationService(LlmReviewJudge judge) { this.judge = judge; }

    public ReviewEvaluation evaluate(
            List<ReviewFinding> findings,
            int toolCalls,
            int usedTokens,
            boolean budgetExceeded
    ) {
        int located = (int) findings.stream().filter(this::hasLocation).count();
        double rate = findings.isEmpty() ? 1.0 : (double) located / findings.size();
        return new ReviewEvaluation(
                findings.size(), located, rate, toolCalls, usedTokens, budgetExceeded,
                judge.evaluate(findings)
        );
    }

    private boolean hasLocation(ReviewFinding finding) {
        return finding.getFilePath() != null && !finding.getFilePath().isBlank()
                && (finding.getLine() != null || finding.getStartLine() != null);
    }
}
