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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewFindingPositionValidatorTest {

    private final ReviewFindingPositionValidator validator = new ReviewFindingPositionValidator();

    @Test
    void shouldKeepValidTargetSideLine() {
        LlmReviewResult validated = validator.validate(input(), result(finding("src/App.java", 12, "danger();")));

        assertEquals(1, validated.getFindings().size());
        assertEquals(12, validated.getFindings().get(0).getLine());
        assertEquals("RIGHT", validated.getFindings().get(0).getSide());
    }

    @Test
    void shouldRelocateWrongLineFromExactCodeEvidence() {
        LlmReviewResult validated = validator.validate(input(), result(finding("src/App.java", 99, "danger();")));

        assertEquals(12, validated.getFindings().get(0).getLine());
        assertTrue(validated.getSummary().contains("重新定位 1 条"));
    }

    @Test
    void shouldDowngradeUnresolvedPositionToFileLevel() {
        LlmReviewResult validated = validator.validate(input(), result(finding("src/App.java", 99, "missing();")));

        assertEquals(1, validated.getFindings().size());
        assertEquals("src/App.java", validated.getFindings().get(0).getFilePath());
        assertNull(validated.getFindings().get(0).getLine());
        assertTrue(validated.getSummary().contains("降级为文件级 1 条"));
    }

    @Test
    void shouldRejectFindingForUnknownFile() {
        LlmReviewResult validated = validator.validate(input(), result(finding("src/MadeUp.java", 12, "danger();")));

        assertTrue(validated.getFindings().isEmpty());
        assertTrue(validated.getSummary().contains("拒绝未知文件 1 条"));
    }

    private static ReviewExecutionInput input() {
        PrContext context = new PrContext("owner", "repo", 1);
        PrChangedFile file = new PrChangedFile(
                "src/App.java",
                PrChangedFileStatus.MODIFIED,
                2,
                1,
                """
                @@ -10,2 +10,3 @@
                 class App {
                -    safe();
                +    prepare();
                +    danger();
                 }
                """
        );
        return new ReviewExecutionInput(
                context,
                new PrDiff(context, List.of(file)),
                List.of(),
                new ContextStatus(ContextStatusCode.FULL, "ready"),
                new PromptPayload("system", "user", "{}")
        );
    }

    private static LlmReviewResult result(ReviewFinding finding) {
        return new LlmReviewResult(List.of(finding), "model summary", "{}");
    }

    private static ReviewFinding finding(String path, Integer line, String codeSnippet) {
        return new ReviewFinding(
                "llm",
                "HIGH",
                path,
                line,
                line,
                "RIGHT",
                line,
                "RIGHT",
                "风险",
                "说明",
                "建议",
                "LLM_TEST",
                "HIGH",
                "bug",
                codeSnippet
        );
    }
}
