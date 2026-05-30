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
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInRuleEngineTest {

    /**
     * 内置规则应识别新增行里的 Java 标准输出。
     */
    @Test
    void shouldFindSystemOutInAddedJavaLine() {
        ReviewExecutionInput input = newInput(newTargetFile(
                "src/App.java",
                """
                @@ -1,1 +1,2 @@
                 class App {
                +    System.out.println("debug");
                """,
                "class App {\n    System.out.println(\"debug\");",
                true
        ));

        RuleEngineResult result = new BuiltInRuleEngine().run(input);

        assertEquals(1, result.getFindings().size());
        ReviewFinding finding = result.getFindings().get(0);
        assertEquals("BUILTIN_SYSTEM_OUT", finding.getRuleId());
        assertEquals("src/App.java", finding.getFilePath());
        assertEquals(2, finding.getLine());
        assertEquals("RIGHT", finding.getSide());
    }

    /**
     * 内置规则应识别 snippet 中残留的合并冲突标记。
     */
    @Test
    void shouldFindConflictMarkerInSnippet() {
        ReviewExecutionInput input = newInput(newTargetFile(
                "src/App.java",
                """
                @@ -1,1 +1,1 @@
                 class App {}
                """,
                "class App {}\n<<<<<<< HEAD",
                true
        ));

        RuleEngineResult result = new BuiltInRuleEngine().run(input);

        assertEquals(1, result.getFindings().size());
        ReviewFinding finding = result.getFindings().get(0);
        assertEquals("BUILTIN_CONFLICT_MARKER", finding.getRuleId());
        assertEquals(2, finding.getLine());
        assertEquals("HIGH", finding.getSeverity());
    }

    /**
     * 未选中的文件不应被内置规则检查。
     */
    @Test
    void shouldIgnoreUnselectedFiles() {
        ReviewExecutionInput input = newInput(newTargetFile(
                "src/App.java",
                """
                @@ -1,1 +1,1 @@
                +System.out.println("debug");
                """,
                "System.out.println(\"debug\");",
                false
        ));

        RuleEngineResult result = new BuiltInRuleEngine().run(input);

        assertTrue(result.getFindings().isEmpty());
        assertEquals("Built-in rules found no issues.", result.getSummary());
    }

    /**
     * 创建一个只包含单个目标文件的执行输入。
     */
    private static ReviewExecutionInput newInput(ReviewTargetFile targetFile) {
        PrContext context = new PrContext("owner", "repo", 12);
        PrDiff diff = new PrDiff(context, List.of(targetFile.getChangedFile()));
        return new ReviewExecutionInput(
                context,
                diff,
                List.of(targetFile),
                new ContextStatus(ContextStatusCode.FULL, "ready"),
                new PromptPayload("system", "user", "{}")
        );
    }

    /**
     * 创建一个测试用目标文件。
     */
    private static ReviewTargetFile newTargetFile(
            String filename,
            String patch,
            String snippet,
            boolean selected
    ) {
        return new ReviewTargetFile(
                new PrChangedFile(filename, PrChangedFileStatus.MODIFIED, 1, 0, patch),
                List.of(new PrReviewFileContext.Snippet(1, Math.max(1, snippet.split("\\R").length), snippet)),
                0,
                selected,
                "test"
        );
    }
}
