package com.hdg.prysm.evaluation;

public record ReviewEvaluation(
        int findingCount,
        int locatedFindingCount,
        double locationRate,
        int toolCalls,
        int usedTokens,
        boolean budgetExceeded,
        JudgeEvaluation judge
) {
    public record JudgeEvaluation(int evidenceScore, int actionabilityScore, String reason) { }
}
