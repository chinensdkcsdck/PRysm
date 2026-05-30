package com.hdg.prysm.review;

import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.diff.PrChangedFileStatus;
import com.hdg.prysm.diff.PrDiff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrReviewContextLoaderTest {

    @TempDir
    Path repositoryRoot;

    /**
     * patch 存在时，应围绕 hunk 提取当前文件的局部上下文。
     */
    @Test
    void shouldExtractSnippetAroundPatchHunk() throws IOException {
        writeFile("src/main/java/App.java", """
                line1
                line2
                line3
                line4
                line5
                line6
                line7
                """);

        PrReviewContextLoader loader = new PrReviewContextLoader(repositoryRoot, 1, 3, 8000, 40000, 1024 * 1024);
        PrReviewContext reviewContext = loader.load(newDiff(new PrChangedFile(
                "src/main/java/App.java",
                PrChangedFileStatus.MODIFIED,
                2,
                1,
                "@@ -3,2 +3,3 @@"
        )));

        PrReviewFileContext fileContext = reviewContext.getFiles().get(0);
        assertEquals(1, fileContext.getSnippets().size());
        assertEquals(2, fileContext.getSnippets().get(0).getStartLine());
        assertEquals(6, fileContext.getSnippets().get(0).getEndLine());
        assertEquals("line2\nline3\nline4\nline5\nline6", fileContext.getSnippets().get(0).getContent());
        assertFalse(fileContext.isTruncated());
        assertNull(fileContext.getNote());
    }

    /**
     * 多个相邻 hunk 的窗口应合并，避免重复上下文。
     */
    @Test
    void shouldMergeOverlappingHunkWindows() throws IOException {
        writeFile("service.txt", """
                l1
                l2
                l3
                l4
                l5
                l6
                l7
                l8
                l9
                l10
                l11
                l12
                """);

        PrReviewContextLoader loader = new PrReviewContextLoader(repositoryRoot, 2, 3, 8000, 40000, 1024 * 1024);
        PrReviewContext reviewContext = loader.load(newDiff(new PrChangedFile(
                "service.txt",
                PrChangedFileStatus.MODIFIED,
                3,
                1,
                "@@ -5,1 +5,2 @@\n@@ -7,1 +8,1 @@"
        )));

        PrReviewFileContext fileContext = reviewContext.getFiles().get(0);
        assertEquals(1, fileContext.getSnippets().size());
        assertEquals(3, fileContext.getSnippets().get(0).getStartLine());
        assertEquals(10, fileContext.getSnippets().get(0).getEndLine());
    }

    /**
     * patch 缺失时，只保留元数据并说明 patch 不可用。
     */
    @Test
    void shouldKeepMetadataWhenPatchIsUnavailable() {
        PrReviewContextLoader loader = new PrReviewContextLoader(repositoryRoot, 2, 3, 8000, 40000, 1024 * 1024);
        PrReviewContext reviewContext = loader.load(newDiff(new PrChangedFile(
                "README.md",
                PrChangedFileStatus.MODIFIED,
                10,
                2,
                null
        )));

        PrReviewFileContext fileContext = reviewContext.getFiles().get(0);
        assertTrue(fileContext.getSnippets().isEmpty());
        assertEquals("patch unavailable", fileContext.getNote());
        assertFalse(fileContext.isTruncated());
    }

    /**
     * renamed 文件应按 changed file 中的新路径读取内容。
     */
    @Test
    void shouldReadRenamedFileFromNewPath() throws IOException {
        writeFile("src/new-name.txt", """
                a
                b
                c
                d
                """);

        PrReviewContextLoader loader = new PrReviewContextLoader(repositoryRoot, 1, 3, 8000, 40000, 1024 * 1024);
        PrReviewContext reviewContext = loader.load(newDiff(new PrChangedFile(
                "src/new-name.txt",
                PrChangedFileStatus.RENAMED,
                1,
                1,
                "@@ -2,1 +2,1 @@"
        )));

        PrReviewFileContext fileContext = reviewContext.getFiles().get(0);
        assertEquals(1, fileContext.getSnippets().size());
        assertEquals("a\nb\nc", fileContext.getSnippets().get(0).getContent());
    }

    /**
     * 文件路径不能逃逸仓库根目录。
     */
    @Test
    void shouldRejectPathsOutsideRepositoryRoot() {
        PrReviewContextLoader loader = new PrReviewContextLoader(repositoryRoot, 2, 3, 8000, 40000, 1024 * 1024);
        PrReviewContext reviewContext = loader.load(newDiff(new PrChangedFile(
                "../secret.txt",
                PrChangedFileStatus.MODIFIED,
                1,
                1,
                "@@ -1,1 +1,1 @@"
        )));

        PrReviewFileContext fileContext = reviewContext.getFiles().get(0);
        assertTrue(fileContext.getSnippets().isEmpty());
        assertEquals("file path escapes repository root", fileContext.getNote());
    }

    /**
     * 工作区文件缺失时，应保留 diff 元数据并说明无法补充上下文。
     */
    @Test
    void shouldNoteMissingWorkspaceFile() {
        PrReviewContextLoader loader = new PrReviewContextLoader(repositoryRoot, 2, 3, 8000, 40000, 1024 * 1024);
        PrReviewContext reviewContext = loader.load(newDiff(new PrChangedFile(
                "missing.txt",
                PrChangedFileStatus.MODIFIED,
                1,
                1,
                "@@ -1,1 +1,1 @@"
        )));

        PrReviewFileContext fileContext = reviewContext.getFiles().get(0);
        assertTrue(fileContext.getSnippets().isEmpty());
        assertEquals("file not found in workspace", fileContext.getNote());
    }

    /**
     * 超出 snippet 数量限制时，应标记为已裁剪。
     */
    @Test
    void shouldMarkContextTruncatedWhenSnippetLimitExceeded() throws IOException {
        writeFile("App.txt", """
                1
                2
                3
                4
                5
                6
                7
                8
                9
                10
                11
                12
                """);

        PrReviewContextLoader loader = new PrReviewContextLoader(repositoryRoot, 0, 1, 8000, 40000, 1024 * 1024);
        PrReviewContext reviewContext = loader.load(newDiff(new PrChangedFile(
                "App.txt",
                PrChangedFileStatus.MODIFIED,
                3,
                0,
                "@@ -2,1 +2,1 @@\n@@ -8,1 +8,1 @@"
        )));

        PrReviewFileContext fileContext = reviewContext.getFiles().get(0);
        assertEquals(1, fileContext.getSnippets().size());
        assertTrue(fileContext.isTruncated());
        assertEquals("context truncated", fileContext.getNote());
    }

    /**
     * 符号链接文件不应被读取，避免跳出仓库工作区。
     */
    @Test
    void shouldRejectSymbolicLinks() throws IOException {
        Path outsideFile = repositoryRoot.resolveSibling("outside.txt");
        Files.writeString(outsideFile, "secret");
        Path linkPath = repositoryRoot.resolve("linked.txt");

        try {
            Files.createSymbolicLink(linkPath, outsideFile);
        } catch (UnsupportedOperationException | IOException exception) {
            Assumptions.assumeTrue(false, "symbolic links are not supported in this environment");
        }

        PrReviewContextLoader loader = new PrReviewContextLoader(repositoryRoot, 1, 3, 8000, 40000, 1024 * 1024);
        PrReviewContext reviewContext = loader.load(newDiff(new PrChangedFile(
                "linked.txt",
                PrChangedFileStatus.MODIFIED,
                1,
                1,
                "@@ -1,1 +1,1 @@"
        )));

        PrReviewFileContext fileContext = reviewContext.getFiles().get(0);
        assertTrue(fileContext.getSnippets().isEmpty());
        assertEquals("symbolic links are not allowed", fileContext.getNote());
    }

    /**
     * 超大文件在读取前应被拒绝，避免一次性加载全文。
     */
    @Test
    void shouldRejectFilesThatExceedSizeLimit() throws IOException {
        writeFile("large.txt", "12345678901");

        PrReviewContextLoader loader = new PrReviewContextLoader(repositoryRoot, 1, 3, 8000, 40000, 10);
        PrReviewContext reviewContext = loader.load(newDiff(new PrChangedFile(
                "large.txt",
                PrChangedFileStatus.MODIFIED,
                1,
                1,
                "@@ -1,1 +1,1 @@"
        )));

        PrReviewFileContext fileContext = reviewContext.getFiles().get(0);
        assertTrue(fileContext.getSnippets().isEmpty());
        assertEquals("file too large for review context", fileContext.getNote());
    }

    /**
     * 总预算耗尽后，后续文件不应再继续提取 snippet。
     */
    @Test
    void shouldStopLoadingSnippetsWhenTotalBudgetIsExhausted() throws IOException {
        writeFile("first.txt", "12345");
        writeFile("second.txt", "abc");

        PrReviewContextLoader loader = new PrReviewContextLoader(repositoryRoot, 0, 3, 8000, 5, 1024 * 1024);
        PrReviewContext reviewContext = loader.load(new PrDiff(
                new PrContext("chinensdkcsdck", "PRysm", 5),
                List.of(
                        new PrChangedFile("first.txt", PrChangedFileStatus.MODIFIED, 1, 0, "@@ -1,1 +1,1 @@"),
                        new PrChangedFile("second.txt", PrChangedFileStatus.MODIFIED, 1, 0, "@@ -1,1 +1,1 @@")
                )
        ));

        PrReviewFileContext firstFile = reviewContext.getFiles().get(0);
        PrReviewFileContext secondFile = reviewContext.getFiles().get(1);
        assertEquals("12345", firstFile.getSnippets().get(0).getContent());
        assertTrue(secondFile.getSnippets().isEmpty());
        assertEquals("context budget exhausted", secondFile.getNote());
    }

    private PrDiff newDiff(PrChangedFile changedFile) {
        return new PrDiff(new PrContext("chinensdkcsdck", "PRysm", 5), List.of(changedFile));
    }

    private void writeFile(String relativePath, String content) throws IOException {
        Path filePath = repositoryRoot.resolve(relativePath);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}
