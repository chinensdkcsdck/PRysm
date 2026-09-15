package com.hdg.prysm.evaluation;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Computes reproducible offline review metrics using one-to-one finding matching.
 */
@Component
public class ReviewEvaluationService {

    public EvaluationReport evaluate(EvaluationDataset dataset, int lineTolerance) {
        if (dataset == null) {
            throw new IllegalArgumentException("Evaluation dataset must not be null");
        }
        if (lineTolerance < 0) {
            throw new IllegalArgumentException("Line tolerance must not be negative");
        }

        int expectedCount = 0;
        int predictedCount = 0;
        int truePositives = 0;
        int exactPositions = 0;
        int duplicates = 0;
        long promptTokens = 0;
        long completionTokens = 0;

        for (EvaluationCase evaluationCase : dataset.cases()) {
            expectedCount += evaluationCase.expected().size();
            predictedCount += evaluationCase.predicted().size();
            promptTokens += evaluationCase.promptTokens();
            completionTokens += evaluationCase.completionTokens();
            duplicates += duplicateCount(evaluationCase.predicted());

            boolean[] matchedExpected = new boolean[evaluationCase.expected().size()];
            for (EvaluationFinding prediction : evaluationCase.predicted()) {
                int match = closestMatch(prediction, evaluationCase.expected(), matchedExpected, lineTolerance);
                if (match < 0) {
                    continue;
                }
                matchedExpected[match] = true;
                truePositives++;
                if (Objects.equals(prediction.line(), evaluationCase.expected().get(match).line())) {
                    exactPositions++;
                }
            }
        }

        int falsePositives = predictedCount - truePositives;
        int falseNegatives = expectedCount - truePositives;
        double precision = ratio(truePositives, predictedCount);
        double recall = ratio(truePositives, expectedCount);
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        return new EvaluationReport(
                dataset.cases().size(), expectedCount, predictedCount, truePositives, falsePositives, falseNegatives,
                exactPositions, duplicates, promptTokens, completionTokens, precision, recall, f1,
                ratio(exactPositions, truePositives), ratio(duplicates, predictedCount),
                truePositives == 0 ? 0 : (double) (promptTokens + completionTokens) / truePositives
        );
    }

    private static int closestMatch(
            EvaluationFinding prediction,
            List<EvaluationFinding> expected,
            boolean[] matched,
            int lineTolerance
    ) {
        int bestIndex = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < expected.size(); index++) {
            EvaluationFinding candidate = expected.get(index);
            if (matched[index] || !sameIssueType(prediction, candidate)) {
                continue;
            }
            int distance = lineDistance(prediction.line(), candidate.line());
            if (distance <= lineTolerance && distance < bestDistance) {
                bestIndex = index;
                bestDistance = distance;
            }
        }
        return bestIndex;
    }

    private static boolean sameIssueType(EvaluationFinding left, EvaluationFinding right) {
        return left.issueKey().equals(right.issueKey())
                && left.filePath().equals(right.filePath())
                && left.category().equals(right.category());
    }

    private static int lineDistance(Integer left, Integer right) {
        if (left == null || right == null) {
            return Objects.equals(left, right) ? 0 : Integer.MAX_VALUE;
        }
        return Math.abs(left - right);
    }

    private static int duplicateCount(List<EvaluationFinding> findings) {
        Set<EvaluationFinding> unique = new HashSet<>();
        int duplicates = 0;
        for (EvaluationFinding finding : findings) {
            if (!unique.add(finding)) {
                duplicates++;
            }
        }
        return duplicates;
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }
}
