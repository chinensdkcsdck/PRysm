package com.hdg.prysm.review;

import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.diff.PrChangedFileStatus;
import com.hdg.prysm.diff.PrDiff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 PR diff 结果补充 review 所需的局部文件上下文。
 *
 * 该类只围绕 patch 变更块提取窗口，不无上限读取文件全文。
 */
@Component
public class PrReviewContextLoader {

    private static final Pattern HUNK_HEADER_PATTERN =
            Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@.*$");

    private final Path repositoryRoot;
    private final int windowLines;
    private final int maxSnippetsPerFile;
    private final int maxFileContextChars;
    private final int maxTotalContextChars;
    private final long maxFileSizeBytes;

    /**
     * 注入仓库根目录和上下文窗口相关配置。
     */
    @Autowired
    public PrReviewContextLoader(
            @Value("${prysm.review.repository-root:${user.dir}}") String repositoryRoot,
            @Value("${prysm.review.window-lines:20}") int windowLines,
            @Value("${prysm.review.max-snippets-per-file:3}") int maxSnippetsPerFile,
            @Value("${prysm.review.max-file-context-chars:8000}") int maxFileContextChars,
            @Value("${prysm.review.max-total-context-chars:40000}") int maxTotalContextChars,
            @Value("${prysm.review.max-file-size-bytes:1048576}") long maxFileSizeBytes
    ) {
        this(
                Path.of(repositoryRoot),
                windowLines,
                maxSnippetsPerFile,
                maxFileContextChars,
                maxTotalContextChars,
                maxFileSizeBytes
        );
    }

    /**
     * 用于测试时传入仓库目录和配置值。
     */
    PrReviewContextLoader(
            Path repositoryRoot,
            int windowLines,
            int maxSnippetsPerFile,
            int maxFileContextChars,
            int maxTotalContextChars,
            long maxFileSizeBytes
    ) {
        if (repositoryRoot == null) {
            throw new IllegalArgumentException("Repository root must not be null");
        }
        if (windowLines < 0) {
            throw new IllegalArgumentException("Window lines must not be negative");
        }
        if (maxSnippetsPerFile <= 0) {
            throw new IllegalArgumentException("Maximum snippet count must be positive");
        }
        if (maxFileContextChars <= 0) {
            throw new IllegalArgumentException("Maximum file context characters must be positive");
        }
        if (maxTotalContextChars <= 0) {
            throw new IllegalArgumentException("Maximum total context characters must be positive");
        }
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("Maximum file size must be positive");
        }

        this.repositoryRoot = repositoryRoot.toAbsolutePath().normalize();
        this.windowLines = windowLines;
        this.maxSnippetsPerFile = maxSnippetsPerFile;
        this.maxFileContextChars = maxFileContextChars;
        this.maxTotalContextChars = maxTotalContextChars;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    /**
     * 根据 Pull Request diff 提取每个变更文件的局部 review 上下文。
     */
    public PrReviewContext load(PrDiff diff) {
        if (diff == null) {
            throw new IllegalArgumentException("Pull request diff must not be null");
        }

        List<PrReviewFileContext> files = new ArrayList<>();
        int remainingTotalCharacters = maxTotalContextChars;
        for (PrChangedFile changedFile : diff.getChangedFiles()) {
            PrReviewFileContext fileContext = loadFileContext(changedFile, remainingTotalCharacters);
            files.add(fileContext);
            remainingTotalCharacters -= fileContext.getSnippetCharacters();
        }
        return new PrReviewContext(diff, files);
    }

    /**
     * 为单个 changed file 补充局部上下文。
     *
     * 这里只处理当前工作区里可安全读取的文本文件。
     */
    private PrReviewFileContext loadFileContext(PrChangedFile changedFile, int remainingTotalCharacters) {
        if (remainingTotalCharacters <= 0) {
            return new PrReviewFileContext(changedFile, List.of(), false, "context budget exhausted");
        }

        String patch = changedFile.getPatch();
        if (patch == null || patch.isBlank()) {
            return new PrReviewFileContext(changedFile, List.of(), false, "patch unavailable");
        }
        if (changedFile.getStatus() == PrChangedFileStatus.REMOVED) {
            return new PrReviewFileContext(changedFile, List.of(), false, "file removed from workspace");
        }

        Path filePath = resolveWithinRepository(changedFile.getFilename());
        if (filePath == null) {
            return new PrReviewFileContext(changedFile, List.of(), false, "file path escapes repository root");
        }
        if (containsSymbolicLink(filePath)) {
            return new PrReviewFileContext(changedFile, List.of(), false, "symbolic links are not allowed");
        }
        if (!Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS)) {
            return new PrReviewFileContext(changedFile, List.of(), false, "file not found in workspace");
        }
        try {
            if (Files.size(filePath) > maxFileSizeBytes) {
                return new PrReviewFileContext(changedFile, List.of(), false, "file too large for review context");
            }
        } catch (IOException exception) {
            return new PrReviewFileContext(changedFile, List.of(), false, "failed to read workspace file");
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return new PrReviewFileContext(changedFile, List.of(), false, "failed to read workspace file");
        }

        List<Range> windows = mergeRanges(parseHunkRanges(patch, lines.size()));
        if (windows.isEmpty()) {
            return new PrReviewFileContext(changedFile, List.of(), false, "no patch hunks parsed");
        }

        boolean truncated = false;
        List<PrReviewFileContext.Snippet> snippets = new ArrayList<>();
        int usedCharacters = 0;
        int fileCharacterBudget = Math.min(maxFileContextChars, remainingTotalCharacters);

        for (int index = 0; index < windows.size(); index++) {
            if (index >= maxSnippetsPerFile) {
                truncated = true;
                break;
            }

            Range window = windows.get(index);
            String content = buildSnippetContent(lines, window);
            if (content.isBlank()) {
                continue;
            }

            int remainingCharacters = fileCharacterBudget - usedCharacters;
            if (remainingCharacters <= 0) {
                truncated = true;
                break;
            }
            if (content.length() > remainingCharacters) {
                content = content.substring(0, remainingCharacters);
                truncated = true;
            }

            snippets.add(new PrReviewFileContext.Snippet(window.startLine, window.endLine, content));
            usedCharacters += content.length();

            if (usedCharacters >= fileCharacterBudget) {
                truncated = true;
                break;
            }
        }

        String note = truncated ? "context truncated" : null;
        return new PrReviewFileContext(changedFile, snippets, truncated, note);
    }

    /**
     * 将 changed file 的相对路径解析到仓库根目录下。
     *
     * 如果路径试图跳出仓库根目录，则直接拒绝。
     */
    private Path resolveWithinRepository(String relativePath) {
        Path resolved = repositoryRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(repositoryRoot)) {
            return null;
        }
        return resolved;
    }

    /**
     * 检查目标路径及其中间目录是否包含符号链接。
     *
     * 这样可以避免通过符号链接跳出当前 checkout 工作区。
     */
    private boolean containsSymbolicLink(Path filePath) {
        Path current = repositoryRoot;
        Path relativePath = repositoryRoot.relativize(filePath);
        for (Path segment : relativePath) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 patch 中解析新文件侧的 hunk 行号范围，并扩展出上下文窗口。
     */
    private List<Range> parseHunkRanges(String patch, int totalLines) {
        List<Range> ranges = new ArrayList<>();
        for (String line : patch.split("\\R")) {
            Matcher matcher = HUNK_HEADER_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }

            int newStart = Integer.parseInt(matcher.group(1));
            int newCount = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
            int effectiveCount = Math.max(newCount, 1);
            int startLine = Math.max(1, newStart - windowLines);
            int endLine = Math.min(totalLines, newStart + effectiveCount - 1 + windowLines);
            if (startLine <= endLine) {
                ranges.add(new Range(startLine, endLine));
            }
        }
        return ranges;
    }

    /**
     * 合并相交或相邻的窗口，避免为同一段代码生成重复 snippet。
     */
    private List<Range> mergeRanges(List<Range> ranges) {
        if (ranges.isEmpty()) {
            return List.of();
        }

        List<Range> sortedRanges = new ArrayList<>(ranges);
        sortedRanges.sort(Comparator.comparingInt(range -> range.startLine));

        List<Range> mergedRanges = new ArrayList<>();
        Range current = sortedRanges.get(0);

        for (int index = 1; index < sortedRanges.size(); index++) {
            Range next = sortedRanges.get(index);
            if (next.startLine <= current.endLine + 1) {
                current = new Range(current.startLine, Math.max(current.endLine, next.endLine));
                continue;
            }
            mergedRanges.add(current);
            current = next;
        }

        mergedRanges.add(current);
        return mergedRanges;
    }

    /**
     * 根据最终窗口范围拼接 snippet 文本内容。
     */
    private static String buildSnippetContent(List<String> lines, Range range) {
        return String.join("\n", lines.subList(range.startLine - 1, range.endLine));
    }

    /**
     * 记录单个 snippet 的行号窗口。
     */
    private static class Range {

        private final int startLine;
        private final int endLine;

        private Range(int startLine, int endLine) {
            this.startLine = startLine;
            this.endLine = endLine;
        }
    }
}
