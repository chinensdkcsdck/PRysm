package com.hdg.prysm.rule;

import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.diff.PrChangedFileStatus;
import com.hdg.prysm.diff.PrDiff;
import com.hdg.prysm.execution.ContextStatus;
import com.hdg.prysm.execution.ContextStatusCode;
import com.hdg.prysm.execution.PromptPayload;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewFinding;
import com.hdg.prysm.execution.ReviewTargetFile;
import com.hdg.prysm.execution.RuleEngineResult;
import com.hdg.prysm.review.PrReviewFileContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineRunnerTest {

    /**
     * 规则引擎关闭时应返回空结果，不再调用具体规则引擎。
     */
    @Test
    void shouldReturnEmptyResultWhenRuleEngineIsDisabled() {
        RuleEngineRunner runner = new RuleEngineRunner(
                List.of(input -> {
                    throw new AssertionError("Rule engine should not be called");
                }),
                false
        );

        RuleEngineResult result = runner.run(newInput(List.of(newTargetFile("src/App.java", 0, true))));

        assertTrue(result.getFindings().isEmpty());
        assertEquals("Rule engine is disabled.", result.getSummary());
    }

    /**
     * 没有最终选中文件时应返回空结果。
     */
    @Test
    void shouldReturnEmptyResultWhenNoFilesAreSelected() {
        RuleEngineRunner runner = new RuleEngineRunner(List.of(new BuiltInRuleEngine()), true);

        RuleEngineResult result = runner.run(newInput(List.of(newTargetFile("src/App.java", 0, false))));

        assertTrue(result.getFindings().isEmpty());
        assertEquals("No selected files for rule engine review.", result.getSummary());
    }

    /**
     * 传入空输入时应直接失败，避免下游规则引擎收到非法状态。
     */
    @Test
    void shouldRejectNullInput() {
        RuleEngineRunner runner = new RuleEngineRunner(List.of(new BuiltInRuleEngine()), true);

        assertThrows(IllegalArgumentException.class, () -> runner.run(null));
    }

    /**
     * runner 应保留 PR9 已经确定的文件顺序，只过滤未选中的文件。
     */
    @Test
    void shouldKeepUpstreamFileOrder() {
        RuleEngine capturingEngine = input -> {
            List<ReviewTargetFile> files = input.getFiles();
            assertEquals("src/B.java", files.get(0).getChangedFile().getFilename());
            assertEquals("src/A.java", files.get(1).getChangedFile().getFilename());
            return new RuleEngineResult(List.of(), "captured");
        };
        RuleEngineRunner runner = new RuleEngineRunner(List.of(capturingEngine), true);

        RuleEngineResult result = runner.run(newInput(List.of(
                newTargetFile("src/B.java", 2, true),
                newTargetFile("src/Skipped.java", 0, false),
                newTargetFile("src/A.java", 1, true)
        )));

        assertTrue(result.getFindings().isEmpty());
        assertEquals("captured", result.getSummary());
    }

    /**
     * 单个规则引擎失败时应降级记录 summary，并继续返回其它引擎结果。
     */
    @Test
    void shouldKeepOtherResultsWhenOneRuleEngineFails() {
        ReviewFinding finding = new ReviewFinding(
                "test",
                "LOW",
                "src/App.java",
                1,
                1,
                "RIGHT",
                1,
                "RIGHT",
                "Title",
                "Message",
                "Suggestion",
                "TEST_RULE"
        );
        RuleEngine workingEngine = input -> new RuleEngineResult(List.of(finding), "working");
        RuleEngine failingEngine = input -> {
            throw new IllegalStateException("tool missing");
        };
        RuleEngineRunner runner = new RuleEngineRunner(List.of(failingEngine, workingEngine), true);

        RuleEngineResult result = runner.run(newInput(List.of(newTargetFile("src/App.java", 0, true))));

        assertEquals(1, result.getFindings().size());
        assertTrue(result.getSummary().contains("Rule engine failed: tool missing"));
        assertTrue(result.getSummary().contains("working"));
    }

    /**
     * 创建一个包含指定目标文件的执行输入。
     */
    private static ReviewExecutionInput newInput(List<ReviewTargetFile> targetFiles) {
        PrContext context = new PrContext("owner", "repo", 12);
        PrDiff diff = new PrDiff(context, targetFiles.stream()
                .map(ReviewTargetFile::getChangedFile)
                .toList());
        return new ReviewExecutionInput(
                context,
                diff,
                targetFiles,
                new ContextStatus(ContextStatusCode.FULL, "ready"),
                new PromptPayload("system", "user", "{}")
        );
    }

    /**
     * 创建一个测试用目标文件。
     */
    private static ReviewTargetFile newTargetFile(String filename, int priority, boolean selected) {
        return new ReviewTargetFile(
                new PrChangedFile(filename, PrChangedFileStatus.MODIFIED, 1, 0, "@@ -1,1 +1,1 @@\n+class App {}"),
                List.of(new PrReviewFileContext.Snippet(1, 1, "class App {}")),
                priority,
                selected,
                "test"
        );
    }
}
