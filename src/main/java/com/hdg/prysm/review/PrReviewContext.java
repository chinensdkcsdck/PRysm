package com.hdg.prysm.review;

import com.hdg.prysm.diff.PrDiff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PR5 产出的 review 上下文。
 *
 * 包含原始 diff 结果，以及每个变更文件补充出的局部上下文。
 */
public class PrReviewContext {

    private final PrDiff diff;
    private final List<PrReviewFileContext> files;

    /**
     * 创建 review 上下文，并防御性复制文件级上下文列表。
     */
    public PrReviewContext(PrDiff diff, List<PrReviewFileContext> files) {
        if (diff == null) {
            throw new IllegalArgumentException("Pull request diff must not be null");
        }
        if (files == null) {
            throw new IllegalArgumentException("Review files must not be null");
        }
        if (files.stream().anyMatch(file -> file == null)) {
            throw new IllegalArgumentException("Review files must not contain null values");
        }

        this.diff = diff;
        this.files = Collections.unmodifiableList(new ArrayList<>(files));
    }

    /**
     * 返回原始 Pull Request diff。
     */
    public PrDiff getDiff() {
        return diff;
    }

    /**
     * 返回不可变的文件级 review 上下文。
     */
    public List<PrReviewFileContext> getFiles() {
        return files;
    }

    /**
     * 返回文件级上下文总数。
     */
    public int getFileCount() {
        return files.size();
    }

    /**
     * 返回成功提取出 snippet 的文件数。
     */
    public long getFilesWithSnippetsCount() {
        return files.stream()
                .filter(PrReviewFileContext::hasSnippets)
                .count();
    }
}
