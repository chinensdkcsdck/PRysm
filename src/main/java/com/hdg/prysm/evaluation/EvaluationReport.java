package com.hdg.prysm.evaluation;

public record EvaluationReport(
        int cases,
        int expectedFindings,
        int predictedFindings,
        int truePositives,
        int falsePositives,
        int falseNegatives,
        int exactPositionMatches,
        int duplicatePredictions,
        long promptTokens,
        long completionTokens,
        double precision,
        double recall,
        double f1,
        double positionAccuracy,
        double duplicateRate,
        double tokensPerTruePositive
) {
}
