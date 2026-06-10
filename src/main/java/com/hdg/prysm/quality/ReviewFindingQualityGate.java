package com.hdg.prysm.quality;

import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.execution.LlmReviewResult;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewFinding;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Applies display quality gates for fast and deep model findings.
 */
@Component
public class ReviewFindingQualityGate {

    private static final int MAX_FAST_LLM_FINDINGS = 2;
    private static final Set<String> FAST_ALLOWED_CATEGORIES = Set.of("SECRET", "WORKFLOW", "CONFIG");
    private static final Set<String> FAST_ALLOWED_RULE_IDS = Set.of(
            "LLM_SECRET_LEAK",
            "LLM_WORKFLOW_PERMISSION",
            "LLM_CONFIG_REMOVAL"
    );

    public LlmReviewResult filterFastReview(ReviewExecutionInput input, LlmReviewResult result) {
        if (result == null) {
            throw new IllegalArgumentException("LLM review result must not be null");
        }
        if (isDocumentationOnly(input)) {
            return new LlmReviewResult(
                    List.of(),
                    "快速初筛完成：文档类变更未发现阻塞问题。深度审查仍在进行，稍后会更新本评论。",
                    result.getRawResponse(),
                    result.getTokenUsage()
            );
        }

        List<ReviewFinding> findings = result.getFindings().stream()
                .filter(this::isHighConfidence)
                .filter(this::isFastAllowed)
                .limit(MAX_FAST_LLM_FINDINGS)
                .toList();
        return new LlmReviewResult(
                findings,
                fastSummary(findings),
                result.getRawResponse(),
                result.getTokenUsage()
        );
    }

    public LlmReviewResult filterDeepReview(ReviewExecutionInput input, LlmReviewResult result) {
        if (result == null) {
            throw new IllegalArgumentException("LLM review result must not be null");
        }
        List<ReviewFinding> findings = result.getFindings().stream()
                .filter(this::hasActionableSeverity)
                .filter(this::hasLocation)
                .filter(finding -> !isDocumentationNoise(input, finding))
                .toList();
        return new LlmReviewResult(
                findings,
                findings.isEmpty() ? "未发现需要在最终评论中展示的明确问题。" : result.getSummary(),
                result.getRawResponse(),
                result.getTokenUsage()
        );
    }

    private String fastSummary(List<ReviewFinding> findings) {
        if (findings.isEmpty()) {
            return "快速初筛完成：未发现明显阻塞问题。深度审查仍在进行，稍后会更新本评论。";
        }
        return "快速初筛完成：发现 " + findings.size() + " 个高置信明显问题。以下结果可能被深度审查修正。";
    }

    private boolean isHighConfidence(ReviewFinding finding) {
        return "HIGH".equalsIgnoreCase(finding.getConfidence());
    }

    private boolean isFastAllowed(ReviewFinding finding) {
        String category = normalize(finding.getCategory());
        String ruleId = normalize(finding.getRuleId());
        return FAST_ALLOWED_CATEGORIES.contains(category)
                || FAST_ALLOWED_RULE_IDS.contains(ruleId);
    }

    private boolean hasActionableSeverity(ReviewFinding finding) {
        String severity = normalize(finding.getSeverity());
        return "CRITICAL".equals(severity)
                || "HIGH".equals(severity)
                || "MEDIUM".equals(severity)
                || "LOW".equals(severity)
                || "ERROR".equals(severity)
                || "WARNING".equals(severity);
    }

    private boolean hasLocation(ReviewFinding finding) {
        return finding.getFilePath() != null
                && !finding.getFilePath().isBlank()
                && (finding.getLine() != null || finding.getStartLine() != null || finding.getEndLine() != null);
    }

    private boolean isDocumentationNoise(ReviewExecutionInput input, ReviewFinding finding) {
        return isDocumentationOnly(input)
                && ("INFO".equals(normalize(finding.getSeverity()))
                || "DOCUMENTATION".equals(normalize(finding.getCategory()))
                || normalize(finding.getTitle()).contains("SMOKE"));
    }

    private boolean isDocumentationOnly(ReviewExecutionInput input) {
        if (input == null || input.getDiff().getChangedFiles().isEmpty()) {
            return false;
        }
        return input.getDiff().getChangedFiles().stream()
                .map(PrChangedFile::getFilename)
                .allMatch(this::isDocumentationPath);
    }

    private boolean isDocumentationPath(String path) {
        String normalized = normalizePath(path);
        return normalized.endsWith(".md")
                || normalized.startsWith("docs/")
                || "readme".equals(normalized)
                || normalized.endsWith("/readme");
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizePath(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
