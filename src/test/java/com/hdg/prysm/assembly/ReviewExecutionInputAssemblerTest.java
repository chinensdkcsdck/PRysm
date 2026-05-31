package com.hdg.prysm.assembly;

import com.hdg.prysm.budget.ReviewContextBudgetFile;
import com.hdg.prysm.budget.ReviewContextBudgetResult;
import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.diff.PrChangedFileStatus;
import com.hdg.prysm.diff.PrDiff;
import com.hdg.prysm.execution.ContextStatusCode;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewTargetFile;
import com.hdg.prysm.optimization.LlmOptimizationContext;
import com.hdg.prysm.optimization.LlmOptimizationProperties;
import com.hdg.prysm.review.PrReviewContext;
import com.hdg.prysm.review.PrReviewFileContext;
import com.hdg.prysm.selection.ReviewFileSelection;
import com.hdg.prysm.selection.ReviewFileSelectionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewExecutionInputAssemblerTest {

    private final LlmOptimizationContext optimizationContext = new LlmOptimizationContext();
    private final ReviewExecutionInputAssembler assembler = new ReviewExecutionInputAssembler(
            optimizationProperties(false),
            optimizationContext
    );

    /**
     * 组装器应把 PR7 预算结果转换成 B 线可消费的 ReviewExecutionInput。
     */
    @Test
    void shouldAssembleReviewExecutionInput() {
        ReviewContextBudgetResult budgetResult = budgetResult(List.of(selectedBudgetFile(
                "src/main/java/App.java",
                "@@ -1,1 +1,2 @@\n+class App {}",
                "class App {}",
                false,
                null
        )));

        ReviewExecutionInput input = assembler.assemble(budgetResult);

        assertEquals(ContextStatusCode.FULL, input.getContextStatus().getCode());
        assertEquals(1, input.getFiles().size());
        assertEquals("src/main/java/App.java", input.getFiles().get(0).getChangedFile().getFilename());
        assertTrue(input.getPromptPayload().getSystemPrompt().contains("自动化 Pull Request 代码审查助手"));
        assertTrue(input.getPromptPayload().getSystemPrompt().contains("不要编造"));
        assertTrue(input.getPromptPayload().getOutputSchema().contains("\"findings\""));
        assertTrue(input.getPromptPayload().getOutputSchema().contains("\"ruleId\""));
        assertTrue(input.getPromptPayload().getOutputSchema().contains("问题标题"));
    }

    /**
     * user prompt 应包含 PR 元数据、文件元数据、patch、snippet 和预算信息。
     */
    @Test
    void shouldBuildUserPromptWithPatchSnippetAndMetadata() {
        ReviewContextBudgetResult budgetResult = budgetResult(List.of(selectedBudgetFile(
                "src/main/java/App.java",
                "@@ -1,1 +1,2 @@\n+class App {}",
                "class App {\n}",
                false,
                null
        )));

        String userPrompt = assembler.assemble(budgetResult).getPromptPayload().getUserPrompt();

        assertTrue(userPrompt.contains("Pull Request 基础信息"));
        assertTrue(userPrompt.contains("- 所有者: owner"));
        assertTrue(userPrompt.contains("- 仓库: repo"));
        assertTrue(userPrompt.contains("- PR 编号: 8"));
        assertTrue(userPrompt.contains("上下文预算"));
        assertTrue(userPrompt.contains("待审查文件"));
        assertTrue(userPrompt.contains("- 路径: src/main/java/App.java"));
        assertTrue(userPrompt.contains("```diff"));
        assertTrue(userPrompt.contains("+class App {}"));
        assertTrue(userPrompt.contains("片段 1 行号 1-2"));
        assertTrue(userPrompt.contains("class App {"));
    }

    /**
     * 预算裁剪后的输入应标记为 LIMITED，并在 prompt 中保留裁剪原因。
     */
    @Test
    void shouldMarkLimitedWhenBudgetResultIsTruncated() {
        ReviewContextBudgetResult budgetResult = budgetResult(List.of(selectedBudgetFile(
                "src/main/java/App.java",
                "@@ -1,1 +1,2 @@\n+class App {}",
                "class App {}",
                true,
                "context truncated by budget"
        )));

        ReviewExecutionInput input = assembler.assemble(budgetResult);

        assertEquals(ContextStatusCode.LIMITED, input.getContextStatus().getCode());
        assertTrue(input.getContextStatus().getReason().contains("上下文预算裁剪"));
        assertTrue(input.getPromptPayload().getUserPrompt().contains("- 预算原因: context truncated by budget"));
    }

    /**
     * 没有文件进入执行阶段时，应生成 SKIPPED 输入而不是抛错。
     */
    @Test
    void shouldCreateSkippedInputWhenNoFilesAreSelected() {
        ReviewContextBudgetResult budgetResult = budgetResult(List.of(skippedBudgetFile(
                "src/main/java/App.java",
                "context budget exhausted"
        )));

        ReviewExecutionInput input = assembler.assemble(budgetResult);

        assertEquals(ContextStatusCode.SKIPPED, input.getContextStatus().getCode());
        assertTrue(input.getFiles().isEmpty());
        assertTrue(input.getContextStatus().getReason().contains("没有文件进入"));
        assertTrue(input.getPromptPayload().getUserPrompt().contains("没有文件进入本次 Review 执行输入"));
        assertTrue(input.getPromptPayload().getUserPrompt().contains("未进入 Review 的文件"));
        assertTrue(input.getPromptPayload().getUserPrompt().contains("context budget exhausted"));
    }

    /**
     * prompt 中的三反引号应被转义，避免破坏 Markdown 代码块结构。
     */
    @Test
    void shouldEscapeNestedFencesInPromptContent() {
        ReviewContextBudgetResult budgetResult = budgetResult(List.of(selectedBudgetFile(
                "README.md",
                "@@ -1,1 +1,1 @@\n+```java",
                "```text",
                false,
                null
        )));

        String userPrompt = assembler.assemble(budgetResult).getPromptPayload().getUserPrompt();

        assertTrue(userPrompt.contains("'''java"));
        assertTrue(userPrompt.contains("'''text"));
    }

    /**
     * compact prompt 开启时应生成更短的 prompt 和 schema，并记录压缩指标。
     */
    @Test
    void shouldBuildCompactPromptWhenOptimizationIsEnabled() {
        LlmOptimizationContext compactContext = new LlmOptimizationContext();
        ReviewExecutionInputAssembler compactAssembler = new ReviewExecutionInputAssembler(
                optimizationProperties(true),
                compactContext
        );
        ReviewContextBudgetResult budgetResult = budgetResult(List.of(selectedBudgetFile(
                "README.md",
                "@@ -1,1 +1,2 @@\n+updated docs",
                "updated docs",
                false,
                null
        )));

        ReviewExecutionInput input = compactAssembler.assemble(budgetResult);

        assertTrue(input.getPromptPayload().getUserPrompt().contains("PR owner/repo#8"));
        assertTrue(input.getPromptPayload().getUserPrompt().contains("Budget: used="));
        assertTrue(input.getPromptPayload().getUserPrompt().contains("File 1: README.md"));
        assertTrue(input.getPromptPayload().getOutputSchema().length() < 400);
        assertTrue(input.getPromptPayload().getOutputSchema().contains("\"confidence\""));
        assertTrue(input.getPromptPayload().getOutputSchema().contains("\"category\""));
        assertTrue(compactContext.getOriginalPromptCharacters() > compactContext.getCompactPromptCharacters());
        assertTrue(compactContext.getPromptCharactersSaved() > 0);
        assertTrue(compactContext.getPromptCompactRatio() < 1);
    }

    /**
     * 空预算结果不能进入组装器。
     */
    @Test
    void shouldRejectNullBudgetResult() {
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(null));
    }

    private static LlmOptimizationProperties optimizationProperties(boolean compactPromptEnabled) {
        return new LlmOptimizationProperties(
                compactPromptEnabled ? "exp_3_compact_prompt" : "baseline",
                0,
                false,
                800,
                false,
                "fast_model",
                "qwen-turbo",
                compactPromptEnabled
        );
    }

    /**
     * 构造 PR7 的整体预算结果。
     */
    private ReviewContextBudgetResult budgetResult(List<ReviewContextBudgetFile> budgetFiles) {
        PrDiff diff = new PrDiff(
                new PrContext("owner", "repo", 8),
                budgetFiles.stream()
                        .map(file -> file.getSelection().getFileContext().getChangedFile())
                        .toList()
        );
        PrReviewContext reviewContext = new PrReviewContext(
                diff,
                budgetFiles.stream()
                        .map(file -> file.getSelection().getFileContext())
                        .toList()
        );
        ReviewFileSelectionResult selectionResult = new ReviewFileSelectionResult(
                reviewContext,
                budgetFiles.stream()
                        .map(ReviewContextBudgetFile::getSelection)
                        .toList()
        );
        return new ReviewContextBudgetResult(selectionResult, budgetFiles, 32000);
    }

    /**
     * 构造进入执行阶段的预算文件。
     */
    private ReviewContextBudgetFile selectedBudgetFile(
            String filename,
            String patch,
            String snippetContent,
            boolean truncated,
            String reason
    ) {
        PrChangedFile changedFile = new PrChangedFile(filename, PrChangedFileStatus.MODIFIED, 2, 0, patch);
        PrReviewFileContext.Snippet snippet = new PrReviewFileContext.Snippet(
                1,
                Math.max(1, snippetContent.split("\\R", -1).length),
                snippetContent
        );
        PrReviewFileContext fileContext = new PrReviewFileContext(
                changedFile,
                List.of(snippet),
                truncated,
                reason
        );
        ReviewFileSelection selection = new ReviewFileSelection(fileContext, true, 10, null);
        ReviewTargetFile targetFile = new ReviewTargetFile(
                changedFile,
                List.of(snippet),
                selection.getPriority(),
                true,
                reason
        );
        int allocatedCharacters = filename.length() + patch.length() + snippetContent.length();
        return new ReviewContextBudgetFile(
                selection,
                targetFile,
                allocatedCharacters,
                allocatedCharacters,
                truncated,
                reason
        );
    }

    /**
     * 构造被预算跳过的文件。
     */
    private ReviewContextBudgetFile skippedBudgetFile(String filename, String reason) {
        PrChangedFile changedFile = new PrChangedFile(filename, PrChangedFileStatus.MODIFIED, 2, 0, "@@ -1,1 +1,1 @@");
        PrReviewFileContext fileContext = new PrReviewFileContext(
                changedFile,
                List.of(new PrReviewFileContext.Snippet(1, 1, "class App {}")),
                false,
                null
        );
        ReviewFileSelection selection = new ReviewFileSelection(fileContext, true, 10, null);
        ReviewTargetFile targetFile = new ReviewTargetFile(
                new PrChangedFile(filename, PrChangedFileStatus.MODIFIED, 2, 0, null),
                List.of(),
                selection.getPriority(),
                false,
                reason
        );
        return new ReviewContextBudgetFile(selection, targetFile, 100, 0, true, reason);
    }
}
