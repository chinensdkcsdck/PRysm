package com.hdg.prysm.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 规则引擎执行结果。
 */
public class RuleEngineResult implements java.io.Serializable {

    private final List<ReviewFinding> findings;
    private final String summary;

    public RuleEngineResult(List<ReviewFinding> findings, String summary) {
        if (findings == null) {
            throw new IllegalArgumentException("Rule findings must not be null");
        }
        if (findings.stream().anyMatch(finding -> finding == null)) {
            throw new IllegalArgumentException("Rule findings must not contain null values");
        }

        this.findings = Collections.unmodifiableList(new ArrayList<>(findings));
        this.summary = summary;
    }

    public List<ReviewFinding> getFindings() {
        return findings;
    }

    public String getSummary() {
        return summary;
    }

    public boolean hasFindings() {
        return !findings.isEmpty();
    }
}
