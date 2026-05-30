package com.hdg.prysm.budget;

import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.execution.ReviewTargetFile;
import com.hdg.prysm.review.PrReviewFileContext;
import com.hdg.prysm.selection.ReviewFileSelection;
import com.hdg.prysm.selection.ReviewFileSelectionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * PR7 的上下文预算分配服务。
 *
 * 该类只消费 PR6 的选择结果，按文件、批次和整个 PR 的预算限制裁剪 patch 与 snippet。
 */
@Component
public class ReviewContextBudgetService {

    private static final String CONTEXT_TRUNCATED_BY_BUDGET = "context truncated by budget";
    private static final String BATCH_FILE_LIMIT_REACHED = "batch file limit reached";
    private static final String CONTEXT_BUDGET_EXHAUSTED = "context budget exhausted";
    private static final String FILE_CONTEXT_BUDGET_TOO_SMALL = "file context budget too small";

    private final int maxFilesPerBatch;
    private final int maxFileContextChars;
    private final int maxPatchCharsPerFile;
    private final int maxSnippetCharsPerFile;
    private final int maxTotalContextChars;

    /**
     * 注入上下文预算限制配置。
     */
    @Autowired
    public ReviewContextBudgetService(
            @Value("${prysm.review.budget.max-files-per-batch:20}") int maxFilesPerBatch,
            @Value("${prysm.review.budget.max-file-context-chars:8000}") int maxFileContextChars,
            @Value("${prysm.review.budget.max-patch-chars-per-file:4000}") int maxPatchCharsPerFile,
            @Value("${prysm.review.budget.max-snippet-chars-per-file:4000}") int maxSnippetCharsPerFile,
            @Value("${prysm.review.budget.max-total-context-chars:32000}") int maxTotalContextChars
    ) {
        if (maxFilesPerBatch <= 0) {
            throw new IllegalArgumentException("Maximum files per batch must be positive");
        }
        if (maxFileContextChars <= 0) {
            throw new IllegalArgumentException("Maximum file context characters must be positive");
        }
        if (maxPatchCharsPerFile <= 0) {
            throw new IllegalArgumentException("Maximum patch characters per file must be positive");
        }
        if (maxSnippetCharsPerFile <= 0) {
            throw new IllegalArgumentException("Maximum snippet characters per file must be positive");
        }
        if (maxTotalContextChars <= 0) {
            throw new IllegalArgumentException("Maximum total context characters must be positive");
        }

        this.maxFilesPerBatch = maxFilesPerBatch;
        this.maxFileContextChars = maxFileContextChars;
        this.maxPatchCharsPerFile = maxPatchCharsPerFile;
        this.maxSnippetCharsPerFile = maxSnippetCharsPerFile;
        this.maxTotalContextChars = maxTotalContextChars;
    }

    /**
     * 基于 PR6 选择结果分配最终进入模型上下文的预算。
     */
    public ReviewContextBudgetResult allocate(ReviewFileSelectionResult selectionResult) {
        if (selectionResult == null) {
            throw new IllegalArgumentException("Review file selection result must not be null");
        }

        List<ReviewContextBudgetFile> budgetFiles = new ArrayList<>();
        int selectedCount = 0;
        int remainingTotalCharacters = maxTotalContextChars;

        for (ReviewFileSelection selection : selectionResult.getSelectedFiles()) {
            int requestedCharacters = estimateRequestedCharacters(selection);
            if (selectedCount >= maxFilesPerBatch) {
                budgetFiles.add(skippedFile(selection, requestedCharacters, BATCH_FILE_LIMIT_REACHED));
                continue;
            }
            if (remainingTotalCharacters <= 0) {
                budgetFiles.add(skippedFile(selection, requestedCharacters, CONTEXT_BUDGET_EXHAUSTED));
                continue;
            }

            ReviewContextBudgetFile budgetFile = allocateFile(selection, requestedCharacters, remainingTotalCharacters);
            budgetFiles.add(budgetFile);
            if (budgetFile.isSelected()) {
                selectedCount++;
                remainingTotalCharacters -= budgetFile.getAllocatedCharacters();
            }
        }

        return new ReviewContextBudgetResult(selectionResult, budgetFiles, maxTotalContextChars);
    }

    /**
     * 为单个文件分配 patch、snippet 和元数据预算。
     */
    private ReviewContextBudgetFile allocateFile(
            ReviewFileSelection selection,
            int requestedCharacters,
            int remainingTotalCharacters
    ) {
        PrReviewFileContext fileContext = selection.getFileContext();
        PrChangedFile changedFile = fileContext.getChangedFile();
        int fileBudget = Math.min(maxFileContextChars, remainingTotalCharacters);
        int metadataCharacters = estimateMetadataCharacters(changedFile);
        if (fileBudget <= metadataCharacters) {
            return skippedFile(selection, requestedCharacters, FILE_CONTEXT_BUDGET_TOO_SMALL);
        }

        int contentBudget = fileBudget - metadataCharacters;
        TextBudget patchBudget = allocatePatch(changedFile.getPatch(), Math.min(maxPatchCharsPerFile, contentBudget));
        int remainingFileCharacters = contentBudget - patchBudget.getUsedCharacters();
        SnippetBudget snippetBudget = allocateSnippets(
                fileContext.getSnippets(),
                Math.min(maxSnippetCharsPerFile, remainingFileCharacters)
        );

        if (patchBudget.getUsedCharacters() == 0 && snippetBudget.getUsedCharacters() == 0) {
            return skippedFile(selection, requestedCharacters, FILE_CONTEXT_BUDGET_TOO_SMALL);
        }

        boolean truncated = patchBudget.isTruncated()
                || snippetBudget.isTruncated()
                || requestedCharacters > metadataCharacters + patchBudget.getUsedCharacters() + snippetBudget.getUsedCharacters();
        String budgetReason = truncated ? CONTEXT_TRUNCATED_BY_BUDGET : null;
        String note = mergeNotes(fileContext.getNote(), budgetReason);
        PrChangedFile budgetedChangedFile = new PrChangedFile(
                changedFile.getFilename(),
                changedFile.getStatus(),
                changedFile.getAdditions(),
                changedFile.getDeletions(),
                patchBudget.getText()
        );
        ReviewTargetFile targetFile = new ReviewTargetFile(
                budgetedChangedFile,
                snippetBudget.getSnippets(),
                selection.getPriority(),
                true,
                note
        );
        int allocatedCharacters = metadataCharacters + patchBudget.getUsedCharacters() + snippetBudget.getUsedCharacters();
        return new ReviewContextBudgetFile(
                selection,
                targetFile,
                requestedCharacters,
                allocatedCharacters,
                truncated,
                budgetReason
        );
    }

    /**
     * 创建因预算限制被跳过的文件结果。
     */
    private ReviewContextBudgetFile skippedFile(
            ReviewFileSelection selection,
            int requestedCharacters,
            String reason
    ) {
        PrChangedFile changedFile = selection.getFileContext().getChangedFile();
        PrChangedFile skippedChangedFile = new PrChangedFile(
                changedFile.getFilename(),
                changedFile.getStatus(),
                changedFile.getAdditions(),
                changedFile.getDeletions(),
                null
        );
        ReviewTargetFile targetFile = new ReviewTargetFile(
                skippedChangedFile,
                List.of(),
                selection.getPriority(),
                false,
                mergeNotes(selection.getFileContext().getNote(), reason)
        );
        return new ReviewContextBudgetFile(selection, targetFile, requestedCharacters, 0, true, reason);
    }

    /**
     * 为 patch 文本分配预算。
     */
    private TextBudget allocatePatch(String patch, int budget) {
        if (patch == null || patch.isBlank() || budget <= 0) {
            return new TextBudget(null, 0, patch != null && !patch.isBlank());
        }
        if (patch.length() <= budget) {
            return new TextBudget(patch, patch.length(), false);
        }
        String truncatedPatch = patch.substring(0, budget);
        return new TextBudget(truncatedPatch, truncatedPatch.length(), true);
    }

    /**
     * 为 snippet 列表分配预算，并按剩余字符裁剪最后一个 snippet。
     */
    private SnippetBudget allocateSnippets(List<PrReviewFileContext.Snippet> snippets, int budget) {
        if (snippets.isEmpty() || budget <= 0) {
            return new SnippetBudget(List.of(), 0, !snippets.isEmpty());
        }

        List<PrReviewFileContext.Snippet> allocatedSnippets = new ArrayList<>();
        int usedCharacters = 0;
        boolean truncated = false;

        for (PrReviewFileContext.Snippet snippet : snippets) {
            int remainingCharacters = budget - usedCharacters;
            if (remainingCharacters <= 0) {
                truncated = true;
                break;
            }

            String content = snippet.getContent();
            if (content.length() <= remainingCharacters) {
                allocatedSnippets.add(snippet);
                usedCharacters += content.length();
                continue;
            }

            String truncatedContent = content.substring(0, remainingCharacters);
            if (!truncatedContent.isBlank()) {
                allocatedSnippets.add(new PrReviewFileContext.Snippet(
                        snippet.getStartLine(),
                        truncatedSnippetEndLine(snippet, truncatedContent),
                        truncatedContent
                ));
                usedCharacters += truncatedContent.length();
            }
            truncated = true;
            break;
        }

        return new SnippetBudget(allocatedSnippets, usedCharacters, truncated);
    }

    /**
     * 根据裁剪后的 snippet 文本重新计算结束行号。
     */
    private int truncatedSnippetEndLine(PrReviewFileContext.Snippet snippet, String truncatedContent) {
        int lineCount = truncatedContent.split("\\R", -1).length;
        return Math.min(snippet.getEndLine(), snippet.getStartLine() + lineCount - 1);
    }

    /**
     * 估算单个文件在没有预算裁剪时需要的总字符数。
     */
    private int estimateRequestedCharacters(ReviewFileSelection selection) {
        PrReviewFileContext fileContext = selection.getFileContext();
        return estimateMetadataCharacters(fileContext.getChangedFile())
                + textLength(fileContext.getChangedFile().getPatch())
                + fileContext.getSnippetCharacters();
    }

    /**
     * 估算文件元数据进入 prompt 时会占用的字符数。
     */
    private int estimateMetadataCharacters(PrChangedFile changedFile) {
        return changedFile.getFilename().length()
                + changedFile.getStatus().name().length()
                + String.valueOf(changedFile.getAdditions()).length()
                + String.valueOf(changedFile.getDeletions()).length()
                + 24;
    }

    /**
     * 返回文本长度，空文本按 0 计算。
     */
    private int textLength(String text) {
        return text == null ? 0 : text.length();
    }

    /**
     * 合并 PR5 说明和 PR7 预算说明。
     */
    private String mergeNotes(String originalNote, String budgetNote) {
        if (originalNote == null || originalNote.isBlank()) {
            return budgetNote;
        }
        if (budgetNote == null || budgetNote.isBlank() || originalNote.equals(budgetNote)) {
            return originalNote;
        }
        return originalNote + "; " + budgetNote;
    }

    /**
     * 记录文本预算分配结果。
     */
    private static class TextBudget {

        private final String text;
        private final int usedCharacters;
        private final boolean truncated;

        /**
         * 创建文本预算结果。
         */
        private TextBudget(String text, int usedCharacters, boolean truncated) {
            this.text = text;
            this.usedCharacters = usedCharacters;
            this.truncated = truncated;
        }

        /**
         * 返回预算后的文本。
         */
        private String getText() {
            return text;
        }

        /**
         * 返回实际使用的字符数。
         */
        private int getUsedCharacters() {
            return usedCharacters;
        }

        /**
         * 返回文本是否被裁剪。
         */
        private boolean isTruncated() {
            return truncated;
        }
    }

    /**
     * 记录 snippet 预算分配结果。
     */
    private static class SnippetBudget {

        private final List<PrReviewFileContext.Snippet> snippets;
        private final int usedCharacters;
        private final boolean truncated;

        /**
         * 创建 snippet 预算结果。
         */
        private SnippetBudget(List<PrReviewFileContext.Snippet> snippets, int usedCharacters, boolean truncated) {
            this.snippets = List.copyOf(snippets);
            this.usedCharacters = usedCharacters;
            this.truncated = truncated;
        }

        /**
         * 返回预算后的 snippet 列表。
         */
        private List<PrReviewFileContext.Snippet> getSnippets() {
            return snippets;
        }

        /**
         * 返回 snippet 实际使用的字符数。
         */
        private int getUsedCharacters() {
            return usedCharacters;
        }

        /**
         * 返回 snippet 是否被裁剪。
         */
        private boolean isTruncated() {
            return truncated;
        }
    }
}
