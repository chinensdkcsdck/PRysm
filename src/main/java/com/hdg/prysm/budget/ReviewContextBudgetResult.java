package com.hdg.prysm.budget;

import com.hdg.prysm.execution.ReviewTargetFile;
import com.hdg.prysm.selection.ReviewFileSelectionResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PR7 的整体上下文预算分配结果。
 *
 * 保存 PR6 输出、预算后的文件列表，以及整个 PR 的预算使用情况。
 */
public class ReviewContextBudgetResult {

    private final ReviewFileSelectionResult selectionResult;
    private final List<ReviewContextBudgetFile> files;
    private final int maxTotalCharacters;

    /**
     * 创建整体预算分配结果，并防御性复制文件列表。
     */
    public ReviewContextBudgetResult(
            ReviewFileSelectionResult selectionResult,
            List<ReviewContextBudgetFile> files,
            int maxTotalCharacters
    ) {
        if (selectionResult == null) {
            throw new IllegalArgumentException("Review file selection result must not be null");
        }
        if (files == null) {
            throw new IllegalArgumentException("Budget files must not be null");
        }
        if (files.stream().anyMatch(file -> file == null)) {
            throw new IllegalArgumentException("Budget files must not contain null values");
        }
        if (maxTotalCharacters <= 0) {
            throw new IllegalArgumentException("Maximum total characters must be positive");
        }

        this.selectionResult = selectionResult;
        this.files = Collections.unmodifiableList(new ArrayList<>(files));
        this.maxTotalCharacters = maxTotalCharacters;
    }

    /**
     * 返回 PR6 的原始选择结果。
     */
    public ReviewFileSelectionResult getSelectionResult() {
        return selectionResult;
    }

    /**
     * 返回所有经过 PR7 处理的文件预算结果。
     */
    public List<ReviewContextBudgetFile> getFiles() {
        return files;
    }

    /**
     * 返回最终进入后续执行输入的预算文件。
     */
    public List<ReviewContextBudgetFile> getSelectedFiles() {
        return files.stream()
                .filter(ReviewContextBudgetFile::isSelected)
                .toList();
    }

    /**
     * 返回因预算限制被跳过的文件。
     */
    public List<ReviewContextBudgetFile> getSkippedFiles() {
        return files.stream()
                .filter(file -> !file.isSelected())
                .toList();
    }

    /**
     * 返回预算裁剪后的目标文件列表，供 PR8 组装 ReviewExecutionInput。
     */
    public List<ReviewTargetFile> getTargetFiles() {
        return getSelectedFiles().stream()
                .map(ReviewContextBudgetFile::getTargetFile)
                .toList();
    }

    /**
     * 返回整个 PR 的预算上限。
     */
    public int getMaxTotalCharacters() {
        return maxTotalCharacters;
    }

    /**
     * 返回所有选中文件实际使用的字符数。
     */
    public int getUsedCharacters() {
        return files.stream()
                .filter(ReviewContextBudgetFile::isSelected)
                .mapToInt(ReviewContextBudgetFile::getAllocatedCharacters)
                .sum();
    }

    /**
     * 返回整个 PR 预算的剩余字符数。
     */
    public int getRemainingCharacters() {
        return Math.max(0, maxTotalCharacters - getUsedCharacters());
    }

    /**
     * 返回是否存在预算裁剪或预算跳过。
     */
    public boolean isTruncated() {
        return files.stream().anyMatch(file -> file.isTruncated() || !file.isSelected());
    }
}
