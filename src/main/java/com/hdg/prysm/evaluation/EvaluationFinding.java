package com.hdg.prysm.evaluation;

/**
 * Minimal, review-tool-neutral finding used by the offline evaluation dataset.
 */
public record EvaluationFinding(String issueKey, String filePath, Integer line, String category) {

    public EvaluationFinding {
        if (issueKey == null || issueKey.isBlank()) {
            throw new IllegalArgumentException("Evaluation finding issue key must not be blank");
        }
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("Evaluation finding file path must not be blank");
        }
        if (line != null && line <= 0) {
            throw new IllegalArgumentException("Evaluation finding line must be positive");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Evaluation finding category must not be blank");
        }
        issueKey = issueKey.trim().toLowerCase(java.util.Locale.ROOT);
        filePath = normalize(filePath);
        category = category.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalize(String path) {
        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }
}
