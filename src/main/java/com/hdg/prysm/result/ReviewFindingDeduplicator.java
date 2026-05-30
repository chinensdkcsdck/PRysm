package com.hdg.prysm.result;

import com.hdg.prysm.execution.ReviewFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Removes equivalent findings while keeping deterministic rule findings first.
 */
@Component
public class ReviewFindingDeduplicator {

    public DeduplicationResult deduplicate(List<ReviewFinding> findings) {
        if (findings == null) {
            throw new IllegalArgumentException("Findings must not be null");
        }

        Map<String, ReviewFinding> deduplicated = new LinkedHashMap<>();
        int duplicates = 0;
        for (ReviewFinding finding : findings) {
            if (finding == null) {
                throw new IllegalArgumentException("Findings must not contain null values");
            }

            String key = deduplicationKey(finding);
            ReviewFinding existing = deduplicated.get(key);
            if (existing == null) {
                deduplicated.put(key, finding);
                continue;
            }

            duplicates++;
            if (shouldReplace(existing, finding)) {
                deduplicated.put(key, finding);
            }
        }

        return new DeduplicationResult(new ArrayList<>(deduplicated.values()), duplicates);
    }

    private static boolean shouldReplace(ReviewFinding existing, ReviewFinding candidate) {
        return sourceRank(candidate) < sourceRank(existing);
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

    private static String deduplicationKey(ReviewFinding finding) {
        return String.join("|",
                normalized(finding.getFilePath()),
                String.valueOf(primaryLine(finding)),
                normalized(prefer(finding.getRuleId(), finding.getTitle())),
                normalized(finding.getTitle())
        );
    }

    private static Integer primaryLine(ReviewFinding finding) {
        if (finding.getLine() != null) {
            return finding.getLine();
        }
        return finding.getStartLine();
    }

    private static String prefer(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private static String normalized(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record DeduplicationResult(List<ReviewFinding> findings, int duplicateCount) {
        public DeduplicationResult {
            findings = List.copyOf(findings);
        }
    }
}
