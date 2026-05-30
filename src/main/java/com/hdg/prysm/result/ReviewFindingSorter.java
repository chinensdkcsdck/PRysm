package com.hdg.prysm.result;

import com.hdg.prysm.execution.ReviewFinding;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Applies a stable display order for aggregated findings.
 */
@Component
public class ReviewFindingSorter {

    public List<ReviewFinding> sort(List<ReviewFinding> findings) {
        if (findings == null) {
            throw new IllegalArgumentException("Findings must not be null");
        }
        if (findings.stream().anyMatch(finding -> finding == null)) {
            throw new IllegalArgumentException("Findings must not contain null values");
        }

        return findings.stream()
                .sorted(Comparator
                        .comparingInt(ReviewFindingSorter::severityRank)
                        .thenComparing(finding -> normalized(finding.getFilePath()))
                        .thenComparingInt(ReviewFindingSorter::primaryLine)
                        .thenComparingInt(ReviewFindingSorter::sourceRank)
                        .thenComparing(finding -> normalized(finding.getTitle())))
                .toList();
    }

    private static int severityRank(ReviewFinding finding) {
        return switch (normalized(finding.getSeverity())) {
            case "critical" -> 0;
            case "high" -> 1;
            case "medium" -> 2;
            case "low" -> 3;
            case "info" -> 4;
            default -> 5;
        };
    }

    private static int sourceRank(ReviewFinding finding) {
        String source = normalized(finding.getSource());
        if ("builtin".equals(source) || "rule".equals(source)) {
            return 0;
        }
        if (source.contains("rule")) {
            return 1;
        }
        if ("llm".equals(source)) {
            return 2;
        }
        return 3;
    }

    private static int primaryLine(ReviewFinding finding) {
        if (finding.getLine() != null) {
            return finding.getLine();
        }
        if (finding.getStartLine() != null) {
            return finding.getStartLine();
        }
        return Integer.MAX_VALUE;
    }

    private static String normalized(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
