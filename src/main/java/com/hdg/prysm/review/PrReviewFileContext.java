package com.hdg.prysm.review;

import com.hdg.prysm.diff.PrChangedFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个变更文件的 review 上下文。
 *
 * 包含原始 changed file 元数据，以及围绕变更点提取出的局部片段。
 */
public class PrReviewFileContext {

    private final PrChangedFile changedFile;
    private final List<Snippet> snippets;
    private final boolean truncated;
    private final String note;

    /**
     * 创建单文件 review 上下文。
     */
    public PrReviewFileContext(
            PrChangedFile changedFile,
            List<Snippet> snippets,
            boolean truncated,
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

        this.changedFile = changedFile;
        this.snippets = Collections.unmodifiableList(new ArrayList<>(snippets));
        this.truncated = truncated;
        this.note = note;
    }

    /**
     * 返回原始 changed file 元数据。
     */
    public PrChangedFile getChangedFile() {
        return changedFile;
    }

    /**
     * 返回不可变的上下文片段列表。
     */
    public List<Snippet> getSnippets() {
        return snippets;
    }

    /**
     * 返回当前文件的上下文是否被裁剪。
     */
    public boolean isTruncated() {
        return truncated;
    }

    /**
     * 返回补充说明，例如 patch 缺失、文件不存在、路径越界等。
     */
    public String getNote() {
        return note;
    }

    /**
     * 返回该文件是否成功提取到至少一个 snippet。
     */
    public boolean hasSnippets() {
        return !snippets.isEmpty();
    }

    /**
     * 返回当前文件所有 snippet 文本的总字符数。
     */
    public int getSnippetCharacters() {
        return snippets.stream()
                .mapToInt(snippet -> snippet.getContent().length())
                .sum();
    }

    /**
     * 单个 snippet 的行号范围和内容。
     */
    public static class Snippet {

        private final int startLine;
        private final int endLine;
        private final String content;

        /**
         * 创建单个 snippet。
         */
        public Snippet(int startLine, int endLine, String content) {
            if (startLine <= 0) {
                throw new IllegalArgumentException("Snippet start line must be positive");
            }
            if (endLine < startLine) {
                throw new IllegalArgumentException("Snippet end line must be greater than or equal to start line");
            }
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("Snippet content must not be blank");
            }

            this.startLine = startLine;
            this.endLine = endLine;
            this.content = content;
        }

        /**
         * 返回 snippet 起始行号。
         */
        public int getStartLine() {
            return startLine;
        }

        /**
         * 返回 snippet 结束行号。
         */
        public int getEndLine() {
            return endLine;
        }

        /**
         * 返回 snippet 文本内容。
         */
        public String getContent() {
            return content;
        }
    }
}
