package com.hdg.prysm.diff;

import com.hdg.prysm.context.PrContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 当前 Pull Request 的 diff 结果。
 *
 * 该对象是 PR4 和后续 review 步骤之间的共享契约。
 */
public class PrDiff {

    private final PrContext context;
    private final List<PrChangedFile> changedFiles;

    /**
     * 创建 Pull Request diff，并防御性复制变更文件列表。
     */
    public PrDiff(PrContext context, List<PrChangedFile> changedFiles) {
        if (context == null) {
            throw new IllegalArgumentException("Pull request context must not be null");
        }
        if (changedFiles == null) {
            throw new IllegalArgumentException("Changed files must not be null");
        }
        if (changedFiles.stream().anyMatch(file -> file == null)) {
            throw new IllegalArgumentException("Changed files must not contain null values");
        }

        this.context = context;
        this.changedFiles = Collections.unmodifiableList(new ArrayList<>(changedFiles));
    }

    /**
     * 返回当前 diff 对应的 Pull Request 上下文。
     */
    public PrContext getContext() {
        return context;
    }

    /**
     * 返回不可变的变更文件列表。
     */
    public List<PrChangedFile> getChangedFiles() {
        return changedFiles;
    }

    /**
     * 返回本次 diff 中包含的文件数量。
     */
    public int getFileCount() {
        return changedFiles.size();
    }

    /**
     * 汇总所有变更文件的新增行数。
     */
    public int getTotalAdditions() {
        return changedFiles.stream()
                .mapToInt(PrChangedFile::getAdditions)
                .sum();
    }

    /**
     * 汇总所有变更文件的删除行数。
     */
    public int getTotalDeletions() {
        return changedFiles.stream()
                .mapToInt(PrChangedFile::getDeletions)
                .sum();
    }
}
