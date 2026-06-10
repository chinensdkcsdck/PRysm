package com.hdg.prysm.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LLM Review 执行结果。
 */
public class LlmReviewResult {

    private final List<ReviewFinding> findings;
    private final String summary;
    private final String rawResponse;
    private final LlmTokenUsage tokenUsage;

    public LlmReviewResult(List<ReviewFinding> findings, String summary, String rawResponse) {
        this(findings, summary, rawResponse, null);
    }

    public LlmReviewResult(
            List<ReviewFinding> findings,
            String summary,
            String rawResponse,
            LlmTokenUsage tokenUsage
    ) {
        if (findings == null) {
            throw new IllegalArgumentException("LLM findings must not be null");
        }
        if (findings.stream().anyMatch(finding -> finding == null)) {
            throw new IllegalArgumentException("LLM findings must not contain null values");
        }

        this.findings = Collections.unmodifiableList(new ArrayList<>(findings));
        this.summary = summary;
        this.rawResponse = rawResponse;
        this.tokenUsage = tokenUsage;
    }

    public List<ReviewFinding> getFindings() {
        return findings;
    }

    public String getSummary() {
        return summary;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public LlmTokenUsage getTokenUsage() {
        return tokenUsage;
    }

    public LlmReviewResult withTokenUsage(LlmTokenUsage tokenUsage) {
        return new LlmReviewResult(findings, summary, rawResponse, tokenUsage);
    }

    public boolean hasFindings() {
        return !findings.isEmpty();
    }
}
