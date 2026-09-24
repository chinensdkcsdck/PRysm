package com.hdg.prysm.execution;

import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.review.PrReviewFileContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 进入规则检查和 LLM Review 阶段的单个目标文件。
 *
 * 它只描述上游最终选中的文件、片段和优先级，不负责再次读取文件或解析 patch。
 */
public class ReviewTargetFile implements java.io.Serializable {

    private final PrChangedFile changedFile;
    private final List<PrReviewFileContext.Snippet> snippets;
    private final int priority;
    private final boolean selected;
    private final String note;

    /**
     * 创建一个可执行审查的目标文件。
     */
    public ReviewTargetFile(
            PrChangedFile changedFile,
            List<PrReviewFileContext.Snippet> snippets,
            int priority,
            boolean selected,
            String note
    ) {
        if (changedFile == null) {
            throw new IllegalArgumentException("Changed file must not be null");
        }
        if (snippets == null) {
            throw new IllegalArgumentException("Snippets must not be null");
        }
        if (snippets.stream().anyMatch(snippet -> snippet == null)) {
            throw new IllegalArgumentException("Snippets must not contain null values");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("Priority must not be negative");
        }

        this.changedFile = changedFile;
        this.snippets = Collections.unmodifiableList(new ArrayList<>(snippets));
        this.priority = priority;
        this.selected = selected;
        this.note = note;
    }

    /**
     * 返回原始 changed file 元数据。
     */
    public PrChangedFile getChangedFile() {
        return changedFile;
    }

    /**
     * 返回上游保留下来的 review snippet。
     */
    public List<PrReviewFileContext.Snippet> getSnippets() {
        return snippets;
    }

    /**
     * 返回文件优先级，数值越小表示越优先。
     */
    public int getPriority() {
        return priority;
    }

    /**
     * 返回该文件是否进入最终执行输入。
     */
    public boolean isSelected() {
        return selected;
    }

    /**
     * 返回文件级补充说明。
     */
    public String getNote() {
        return note;
    }
}
