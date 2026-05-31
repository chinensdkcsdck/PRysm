package com.hdg.prysm.quality;

import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.diff.PrChangedFileStatus;
import com.hdg.prysm.diff.PrDiff;
import com.hdg.prysm.execution.ContextStatus;
import com.hdg.prysm.execution.ContextStatusCode;
import com.hdg.prysm.execution.LlmReviewResult;
import com.hdg.prysm.execution.PromptPayload;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewFindingQualityGateTest {

    private final ReviewFindingQualityGate gate = new ReviewFindingQualityGate();

    @Test
    void shouldOnlyKeepHighConfidenceAllowlistedFastFindings() {
        ReviewExecutionInput input = input("src/main.yml");
        LlmReviewResult result = new LlmReviewResult(
                List.of(
                        finding("HIGH", "HIGH", "workflow", "LLM_WORKFLOW_PERMISSION", "src/main.yml", 3, "workflow issue"),
                        finding("HIGH", "MEDIUM", "workflow", "LLM_WORKFLOW_PERMISSION", "src/main.yml", 4, "medium issue"),
                        finding("HIGH", "HIGH", "architecture", "LLM_ARCH", "src/main.yml", 5, "arch issue")
                ),
                "raw summary",
                "{}"
        );

        LlmReviewResult filtered = gate.filterFastReview(input, result);

        assertEquals(1, filtered.getFindings().size());
        assertEquals("workflow issue", filtered.getFindings().get(0).getTitle());
        assertTrue(filtered.getSummary().contains("快速初筛完成"));
    }

    @Test
    void shouldSuppressFastLlmFindingsForDocumentationOnlyPr() {
        ReviewExecutionInput input = input("README.md");
        LlmReviewResult result = new LlmReviewResult(
                List.of(finding("HIGH", "HIGH", "workflow", "LLM_WORKFLOW_PERMISSION", "README.md", 3, "doc issue")),
                "raw summary",
                "{}"
        );

        LlmReviewResult filtered = gate.filterFastReview(input, result);

        assertTrue(filtered.getFindings().isEmpty());
        assertTrue(filtered.getSummary().contains("文档类变更未发现阻塞问题"));
    }

    @Test
    void shouldFilterDeepReviewNoise() {
        ReviewExecutionInput input = input("src/App.java");
        LlmReviewResult result = new LlmReviewResult(
                List.of(
                        finding("INFO", "HIGH", "documentation", "LLM_INFO", "src/App.java", 3, "info only"),
                        finding("HIGH", "HIGH", "bug", "LLM_BUG", null, null, "no location"),
                        finding("HIGH", "HIGH", "bug", "LLM_BUG", "src/App.java", 10, "real bug")
                ),
                "raw summary",
                "{}"
        );

        LlmReviewResult filtered = gate.filterDeepReview(input, result);

        assertEquals(1, filtered.getFindings().size());
        assertEquals("real bug", filtered.getFindings().get(0).getTitle());
    }

    private static ReviewExecutionInput input(String path) {
        PrContext context = new PrContext("owner", "repo", 1);
        PrDiff diff = new PrDiff(
                context,
                List.of(new PrChangedFile(path, PrChangedFileStatus.MODIFIED, 1, 0, "@@ -1 +1 @@"))
        );
        return new ReviewExecutionInput(
                context,
                diff,
                List.of(),
                new ContextStatus(ContextStatusCode.FULL, "full"),
                new PromptPayload("system", "user", "{}")
        );
    }

    private static ReviewFinding finding(
            String severity,
            String confidence,
            String category,
            String ruleId,
            String filePath,
            Integer line,
            String title
    ) {
        return new ReviewFinding(
                "llm",
                severity,
                filePath,
                line,
                line,
                line == null ? null : "RIGHT",
                line,
                line == null ? null : "RIGHT",
                title,
                "message",
                "suggestion",
                ruleId,
                confidence,
                category
        );
    }
}
