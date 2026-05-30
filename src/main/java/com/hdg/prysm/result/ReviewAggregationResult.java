package com.hdg.prysm.result;

import com.hdg.prysm.execution.ReviewFinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregated review result ready to be rendered and written back to GitHub.
 */
public class ReviewAggregationResult {

    private final List<ReviewFinding> findings;
    private final int ruleFindingCount;
    private final int llmFindingCount;
    private final int duplicateCount;
    private final String ruleSummary;
    private final String llmSummary;

    public ReviewAggregationResult(
            List<ReviewFinding> findings,
            int ruleFindingCount,
            int llmFindingCount,
            int duplicateCount,
            String ruleSummary,
            String llmSummary
    ) {
        if (findings == null) {
            throw new IllegalArgumentException("Aggregated findings must not be null");
        }
        if (findings.stream().anyMatch(finding -> finding == null)) {
            throw new IllegalArgumentException("Aggregated findings must not contain null values");
        }
        if (ruleFindingCount < 0) {
            throw new IllegalArgumentException("Rule finding count must not be negative");
        }
        if (llmFindingCount < 0) {
            throw new IllegalArgumentException("LLM finding count must not be negative");
        }
        if (duplicateCount < 0) {
            throw new IllegalArgumentException("Duplicate count must not be negative");
        }

        this.findings = Collections.unmodifiableList(new ArrayList<>(findings));
        this.ruleFindingCount = ruleFindingCount;
        this.llmFindingCount = llmFindingCount;
        this.duplicateCount = duplicateCount;
        this.ruleSummary = ruleSummary;
        this.llmSummary = llmSummary;
    }

    public List<ReviewFinding> getFindings() {
        return findings;
    }

    public int getRuleFindingCount() {
        return ruleFindingCount;
    }

    public int getLlmFindingCount() {
        return llmFindingCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public String getRuleSummary() {
        return ruleSummary;
    }

    public String getLlmSummary() {
        return llmSummary;
    }

    public boolean hasFindings() {
        return !findings.isEmpty();
    }
}
