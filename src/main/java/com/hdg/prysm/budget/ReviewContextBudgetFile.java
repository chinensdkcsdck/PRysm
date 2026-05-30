package com.hdg.prysm.budget;

import com.hdg.prysm.execution.ReviewTargetFile;
import com.hdg.prysm.selection.ReviewFileSelection;

/**
 * PR7 产出的单文件上下文预算分配结果。
 *
 * 该对象保留 PR6 的选择结果，并给出预算裁剪后的执行目标文件。
 */
public class ReviewContextBudgetFile {

    private final ReviewFileSelection selection;
    private final ReviewTargetFile targetFile;
    private final int requestedCharacters;
    private final int allocatedCharacters;
    private final boolean truncated;
    private final String reason;

    /**
     * 创建单文件预算分配结果。
     */
    public ReviewContextBudgetFile(
            ReviewFileSelection selection,
            ReviewTargetFile targetFile,
            int requestedCharacters,
            int allocatedCharacters,
            boolean truncated,
            String reason
    ) {
        if (selection == null) {
            throw new IllegalArgumentException("Review file selection must not be null");
        }
        if (targetFile == null) {
            throw new IllegalArgumentException("Review target file must not be null");
        }
        if (requestedCharacters < 0) {
            throw new IllegalArgumentException("Requested characters must not be negative");
        }
        if (allocatedCharacters < 0) {
            throw new IllegalArgumentException("Allocated characters must not be negative");
        }
        if (!targetFile.isSelected() && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Skipped budget file reason must not be blank");
        }

        this.selection = selection;
        this.targetFile = targetFile;
        this.requestedCharacters = requestedCharacters;
        this.allocatedCharacters = allocatedCharacters;
        this.truncated = truncated;
        this.reason = reason;
    }

    /**
     * 返回 PR6 的原始文件选择结果。
     */
    public ReviewFileSelection getSelection() {
        return selection;
    }

    /**
     * 返回预算裁剪后的执行目标文件。
     */
    public ReviewTargetFile getTargetFile() {
        return targetFile;
    }

    /**
     * 返回该文件是否仍进入后续执行输入。
     */
    public boolean isSelected() {
        return targetFile.isSelected();
    }

    /**
     * 返回原始文件上下文预估需要的字符数。
     */
    public int getRequestedCharacters() {
        return requestedCharacters;
    }

    /**
     * 返回预算后实际占用的字符数。
     */
    public int getAllocatedCharacters() {
        return allocatedCharacters;
    }

    /**
     * 返回该文件上下文是否被预算裁剪。
     */
    public boolean isTruncated() {
        return truncated;
    }

    /**
     * 返回预算裁剪或跳过原因。
     */
    public String getReason() {
        return reason;
    }
}
