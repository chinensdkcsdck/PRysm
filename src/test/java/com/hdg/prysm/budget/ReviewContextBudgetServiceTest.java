package com.hdg.prysm.budget;

import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.diff.PrChangedFileStatus;
import com.hdg.prysm.diff.PrDiff;
import com.hdg.prysm.execution.ContextStatus;
import com.hdg.prysm.execution.ContextStatusCode;
import com.hdg.prysm.execution.PromptPayload;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewTargetFile;
import com.hdg.prysm.review.PrReviewContext;
import com.hdg.prysm.review.PrReviewFileContext;
import com.hdg.prysm.selection.ReviewFileSelectionResult;
import com.hdg.prysm.selection.ReviewFileSelectionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewContextBudgetServiceTest {

    private final ReviewFileSelectionService selectionService = new ReviewFileSelectionService();

    /**
     * PR7 应保留 PR6 排好的文件优先级顺序，并生成后续 ReviewTargetFile。
     */
    @Test
    void shouldKeepSelectionOrderAndCreateTargetFiles() {
        ReviewContextBudgetService budgetService = new ReviewContextBudgetService(10, 1000, 500, 500, 2000);
        ReviewFileSelectionResult selectionResult = selectionResult(List.of(
                file("README.md", "doc", "doc context"),
                file("src/main/java/App.java", "java", "class App {}"),
                file("src/test/java/AppTest.java", "test", "class AppTest {}")
        ));

        ReviewContextBudgetResult result = budgetService.allocate(selectionResult);

        List<ReviewTargetFile> targetFiles = result.getTargetFiles();
        assertEquals(3, targetFiles.size());
        assertEquals("src/main/java/App.java", targetFiles.get(0).getChangedFile().getFilename());
        assertEquals("src/test/java/AppTest.java", targetFiles.get(1).getChangedFile().getFilename());
        assertEquals("README.md", targetFiles.get(2).getChangedFile().getFilename());
        assertFalse(result.isTruncated());
        assertTrue(result.getRemainingCharacters() > 0);
    }

    /**
     * 文件批次预算应跳过超出上限的低优先级文件。
     */
    @Test
    void shouldSkipFilesBeyondBatchLimit() {
        ReviewContextBudgetService budgetService = new ReviewContextBudgetService(1, 1000, 500, 500, 2000);
        ReviewFileSelectionResult selectionResult = selectionResult(List.of(
                file("src/main/java/App.java", "java", "class App {}"),
                file("src/test/java/AppTest.java", "test", "class AppTest {}")
        ));

        ReviewContextBudgetResult result = budgetService.allocate(selectionResult);

        assertEquals(1, result.getSelectedFiles().size());
        assertEquals(1, result.getSkippedFiles().size());
        assertEquals("batch file limit reached", result.getSkippedFiles().get(0).getReason());
        assertNull(result.getSkippedFiles().get(0).getTargetFile().getChangedFile().getPatch());
        assertTrue(result.isTruncated());
    }

    /**
     * 单文件预算应同时裁剪 patch 和 snippet，避免任一文件占满整个 PR 上下文。
     */
    @Test
    void shouldTrimPatchAndSnippetByFileBudget() {
        ReviewContextBudgetService budgetService = new ReviewContextBudgetService(10, 120, 30, 30, 1000);
        ReviewFileSelectionResult selectionResult = selectionResult(List.of(
                file(
                        "src/main/java/App.java",
                        "@@ -1,1 +1,1 @@\n+" + "p".repeat(80),
                        "s".repeat(80)
                )
        ));

        ReviewContextBudgetResult result = budgetService.allocate(selectionResult);
        ReviewContextBudgetFile budgetFile = result.getSelectedFiles().get(0);
        ReviewTargetFile targetFile = budgetFile.getTargetFile();

        assertEquals(30, targetFile.getChangedFile().getPatch().length());
        assertEquals(1, targetFile.getSnippets().size());
        assertEquals(30, targetFile.getSnippets().get(0).getContent().length());
        assertEquals("context truncated by budget", budgetFile.getReason());
        assertEquals("context truncated by budget", targetFile.getNote());
    }

    /**
     * 整个 PR 预算耗尽后，后续文件应被标记为未选中。
     */
    @Test
    void shouldSkipRemainingFilesWhenTotalBudgetIsExhausted() {
        ReviewContextBudgetService budgetService = new ReviewContextBudgetService(10, 90, 30, 30, 90);
        ReviewFileSelectionResult selectionResult = selectionResult(List.of(
                file("src/main/java/A.java", "@@ -1,1 +1,1 @@\n+" + "a".repeat(80), "a".repeat(80)),
                file("src/main/java/B.java", "@@ -1,1 +1,1 @@\n+" + "b".repeat(80), "b".repeat(80))
        ));

        ReviewContextBudgetResult result = budgetService.allocate(selectionResult);

        assertEquals(1, result.getSelectedFiles().size());
        assertEquals(1, result.getSkippedFiles().size());
        assertEquals("context budget exhausted", result.getSkippedFiles().get(0).getReason());
        assertEquals(0, result.getRemainingCharacters());
    }

    /**
     * 预算结果列表应不可变，避免 PR8 误改 PR7 输出。
     */
    @Test
    void shouldReturnImmutableBudgetLists() {
        ReviewContextBudgetService budgetService = new ReviewContextBudgetService(10, 1000, 500, 500, 2000);
        ReviewContextBudgetResult result = budgetService.allocate(selectionResult(List.of(
                file("src/main/java/App.java", "java", "class App {}")
        )));

        assertThrows(UnsupportedOperationException.class, () -> result.getFiles().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.getTargetFiles().clear());
    }

    /**
     * PR7 输出的目标文件应能直接进入 ReviewExecutionInput，证明 A/B 线契约可对接。
     */
    @Test
    void shouldExposeTargetFilesForReviewExecutionInput() {
        ReviewContextBudgetService budgetService = new ReviewContextBudgetService(10, 1000, 500, 500, 2000);
        PrReviewContext reviewContext = reviewContext(List.of(
                file("src/main/java/App.java", "java", "class App {}")
        ));
        ReviewContextBudgetResult budgetResult = budgetService.allocate(selectionService.select(reviewContext));

        ReviewExecutionInput input = new ReviewExecutionInput(
                reviewContext.getDiff().getContext(),
                reviewContext.getDiff(),
                budgetResult.getTargetFiles(),
                new ContextStatus(ContextStatusCode.FULL, "budget ready"),
                new PromptPayload("system", "user", "{}")
        );

        assertEquals(1, input.getFiles().size());
        assertTrue(input.getFiles().get(0).isSelected());
        assertEquals("src/main/java/App.java", input.getFiles().get(0).getChangedFile().getFilename());
    }

    /**
     * 构造一个经过 PR6 过滤排序的测试输入。
     */
    private ReviewFileSelectionResult selectionResult(List<PrReviewFileContext> files) {
        return selectionService.select(reviewContext(files));
    }

    /**
     * 构造一个 PR5 review 上下文。
     */
    private PrReviewContext reviewContext(List<PrReviewFileContext> files) {
        List<PrChangedFile> changedFiles = files.stream()
                .map(PrReviewFileContext::getChangedFile)
                .toList();
        PrDiff diff = new PrDiff(new PrContext("owner", "repo", 7), changedFiles);
        return new PrReviewContext(diff, files);
    }

    /**
     * 创建测试用文件上下文。
     */
    private PrReviewFileContext file(String filename, String patchContent, String snippetContent) {
        PrChangedFile changedFile = new PrChangedFile(
                filename,
                PrChangedFileStatus.MODIFIED,
                1,
                0,
                patchContent
        );
        return new PrReviewFileContext(
                changedFile,
                List.of(new PrReviewFileContext.Snippet(1, 1, snippetContent)),
                false,
                null
        );
    }
}
