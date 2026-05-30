package com.hdg.prysm.llm;

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
import com.hdg.prysm.execution.ReviewTargetFile;
import com.hdg.prysm.review.PrReviewFileContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmReviewRunnerTest {

    /**
     * LLM Review 关闭时不应调用模型客户端。
     */
    @Test
    void shouldReturnEmptyResultWhenLlmReviewIsDisabled() {
        LlmReviewClient client = promptPayload -> {
            throw new AssertionError("LLM client should not be called");
        };
        LlmReviewRunner runner = new LlmReviewRunner(
                client,
                new LlmReviewResponseParser(new ObjectMapper()),
                false
        );

        LlmReviewResult result = runner.run(newInput(ContextStatusCode.FULL, "ready"));

        assertTrue(result.getFindings().isEmpty());
        assertEquals("LLM review is disabled.", result.getSummary());
    }

    /**
     * 上下文不足时不应调用模型客户端。
     */
    @Test
    void shouldSkipWhenContextIsNotSufficient() {
        LlmReviewClient client = promptPayload -> {
            throw new AssertionError("LLM client should not be called");
        };
        LlmReviewRunner runner = new LlmReviewRunner(
                client,
                new LlmReviewResponseParser(new ObjectMapper()),
                true
        );

        LlmReviewResult result = runner.run(newInput(ContextStatusCode.INSUFFICIENT, "low patch coverage"));

        assertTrue(result.getFindings().isEmpty());
        assertEquals(
                "LLM review skipped because context is not sufficient: low patch coverage",
                result.getSummary()
        );
    }

    /**
     * 模型调用成功时应返回解析后的 finding。
     */
    @Test
    void shouldReturnParsedResultWhenClientSucceeds() {
        LlmReviewClient client = promptPayload -> """
                {
                  "summary": "one issue",
                  "findings": [
                    {
                      "severity": "MEDIUM",
                      "filePath": "src/App.java",
                      "line": 12,
                      "title": "Null check missing",
                      "message": "The value may be null.",
                      "suggestion": "Add a null check.",
                      "ruleId": "LLM_NULL_CHECK"
                    }
                  ]
                }
                """;
        LlmReviewRunner runner = new LlmReviewRunner(
                client,
                new LlmReviewResponseParser(new ObjectMapper()),
                true
        );

        LlmReviewResult result = runner.run(newInput(ContextStatusCode.FULL, "ready"));

        assertEquals("one issue", result.getSummary());
        assertEquals(1, result.getFindings().size());
        ReviewFinding finding = result.getFindings().get(0);
        assertEquals("llm", finding.getSource());
        assertEquals("src/App.java", finding.getFilePath());
        assertEquals(12, finding.getLine());
    }

    /**
     * 模型调用或解析失败时应降级为空 finding。
     */
    @Test
    void shouldReturnEmptyResultWhenClientFails() {
        LlmReviewClient client = promptPayload -> {
            throw new IllegalStateException("api unavailable");
        };
        LlmReviewRunner runner = new LlmReviewRunner(
                client,
                new LlmReviewResponseParser(new ObjectMapper()),
                true
        );

        LlmReviewResult result = runner.run(newInput(ContextStatusCode.FULL, "ready"));

        assertTrue(result.getFindings().isEmpty());
        assertEquals("LLM review failed: api unavailable", result.getSummary());
    }

    /**
     * 空输入应直接失败，避免隐藏调用方错误。
     */
    @Test
    void shouldRejectNullInput() {
        LlmReviewRunner runner = new LlmReviewRunner(
                promptPayload -> "{}",
                new LlmReviewResponseParser(new ObjectMapper()),
                true
        );

        assertThrows(IllegalArgumentException.class, () -> runner.run(null));
    }

    /**
     * 创建测试用 Review 执行输入。
     */
    private static ReviewExecutionInput newInput(ContextStatusCode statusCode, String reason) {
        PrContext context = new PrContext("owner", "repo", 12);
        PrChangedFile changedFile = new PrChangedFile(
                "src/App.java",
                PrChangedFileStatus.MODIFIED,
                1,
                0,
                "@@ -1,1 +1,1 @@\n+class App {}"
        );
        PrDiff diff = new PrDiff(context, List.of(changedFile));
        ReviewTargetFile targetFile = new ReviewTargetFile(
                changedFile,
                List.of(new PrReviewFileContext.Snippet(1, 1, "class App {}")),
                0,
                true,
                "selected"
        );
        return new ReviewExecutionInput(
                context,
                diff,
                List.of(targetFile),
                new ContextStatus(statusCode, reason),
                new PromptPayload("system", "user", "{}")
        );
    }
}
