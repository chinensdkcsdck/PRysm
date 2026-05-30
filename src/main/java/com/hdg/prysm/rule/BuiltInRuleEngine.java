package com.hdg.prysm.rule;

import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewFinding;
import com.hdg.prysm.execution.ReviewTargetFile;
import com.hdg.prysm.execution.RuleEngineResult;
import com.hdg.prysm.review.PrReviewFileContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内置轻量规则引擎。
 *
 * 该引擎不依赖外部命令，先提供一组稳定的确定性检查，后续可并行接入 Semgrep、Checkstyle 或 SpotBugs。
 */
@Component
public class BuiltInRuleEngine implements RuleEngine {

    private static final Pattern HUNK_HEADER_PATTERN =
            Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*$");
    private static final String SOURCE = "builtin";
    private static final String RIGHT_SIDE = "RIGHT";

    /**
     * 对上游已经选中的目标文件执行内置规则检查。
     */
    @Override
    public RuleEngineResult run(ReviewExecutionInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Review execution input must not be null");
        }

        List<ReviewFinding> findings = new ArrayList<>();
        for (ReviewTargetFile file : input.getFiles()) {
            if (!file.isSelected()) {
                continue;
            }
            inspectPatch(file.getChangedFile(), findings);
            inspectSnippets(file, findings);
        }

        String summary = findings.isEmpty()
                ? "Built-in rules found no issues."
                : "Built-in rules found " + findings.size() + " issue(s).";
        return new RuleEngineResult(findings, summary);
    }

    /**
     * 检查 patch 新增行中的确定性问题。
     */
    private static void inspectPatch(PrChangedFile changedFile, List<ReviewFinding> findings) {
        String patch = changedFile.getPatch();
        if (patch == null || patch.isBlank()) {
            return;
        }

        for (PatchLine patchLine : addedPatchLines(patch)) {
            if (containsConflictMarker(patchLine.content())) {
                findings.add(conflictMarkerFinding(changedFile.getFilename(), patchLine.lineNumber()));
            }
            if (isJavaFile(changedFile) && containsSystemOutPrint(patchLine.content())) {
                findings.add(systemOutFinding(changedFile.getFilename(), patchLine.lineNumber()));
            }
        }
    }

    /**
     * 检查 snippet 中未被 patch 新增行覆盖的确定性问题。
     */
    private static void inspectSnippets(ReviewTargetFile file, List<ReviewFinding> findings) {
        for (PrReviewFileContext.Snippet snippet : file.getSnippets()) {
            inspectSnippet(file.getChangedFile().getFilename(), snippet, findings);
        }
    }

    /**
     * 检查单个 snippet 的内容。
     */
    private static void inspectSnippet(
            String filename,
            PrReviewFileContext.Snippet snippet,
            List<ReviewFinding> findings
    ) {
        String[] lines = snippet.getContent().split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            if (containsConflictMarker(lines[index])) {
                int lineNumber = snippet.getStartLine() + index;
                if (!hasEquivalentConflictFinding(findings, filename, lineNumber)) {
                    findings.add(conflictMarkerFinding(filename, lineNumber));
                }
            }
        }
    }

    /**
     * 从 unified diff patch 中解析新增行和它们对应的新文件行号。
     */
    private static List<PatchLine> addedPatchLines(String patch) {
        List<PatchLine> addedLines = new ArrayList<>();
        int currentNewLine = -1;

        for (String line : patch.split("\\R")) {
            Matcher matcher = HUNK_HEADER_PATTERN.matcher(line);
            if (matcher.matches()) {
                currentNewLine = Integer.parseInt(matcher.group(1));
                continue;
            }
            if (currentNewLine < 0) {
                continue;
            }
            if (line.startsWith("+++") || line.startsWith("---")) {
                continue;
            }
            if (line.startsWith("+")) {
                addedLines.add(new PatchLine(currentNewLine, line.substring(1)));
                currentNewLine++;
                continue;
            }
            if (line.startsWith("-")) {
                continue;
            }
            currentNewLine++;
        }

        return addedLines;
    }

    /**
     * 判断一行代码是否包含合并冲突标记。
     */
    private static boolean containsConflictMarker(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("<<<<<<<")
                || trimmed.startsWith("=======")
                || trimmed.startsWith(">>>>>>>");
    }

    /**
     * 判断一个文件是否是 Java 源文件。
     */
    private static boolean isJavaFile(PrChangedFile changedFile) {
        return changedFile.getFilename().endsWith(".java");
    }

    /**
     * 判断一行 Java 代码是否包含调试输出。
     */
    private static boolean containsSystemOutPrint(String line) {
        return line.contains("System.out.print");
    }

    /**
     * 创建合并冲突标记 finding。
     */
    private static ReviewFinding conflictMarkerFinding(String filename, int lineNumber) {
        return new ReviewFinding(
                SOURCE,
                "HIGH",
                filename,
                lineNumber,
                lineNumber,
                RIGHT_SIDE,
                lineNumber,
                RIGHT_SIDE,
                "Merge conflict marker found",
                "The changed code contains a merge conflict marker.",
                "Resolve the conflict marker before merging this pull request.",
                "BUILTIN_CONFLICT_MARKER"
        );
    }

    /**
     * 创建 Java 调试输出 finding。
     */
    private static ReviewFinding systemOutFinding(String filename, int lineNumber) {
        return new ReviewFinding(
                SOURCE,
                "LOW",
                filename,
                lineNumber,
                lineNumber,
                RIGHT_SIDE,
                lineNumber,
                RIGHT_SIDE,
                "Debug output found",
                "The changed Java code writes directly to standard output.",
                "Use the project logger or remove the debug output.",
                "BUILTIN_SYSTEM_OUT"
        );
    }

    /**
     * 判断是否已经存在同位置的冲突标记 finding，避免 patch 和 snippet 重复上报。
     */
    private static boolean hasEquivalentConflictFinding(List<ReviewFinding> findings, String filename, int lineNumber) {
        return findings.stream().anyMatch(finding ->
                "BUILTIN_CONFLICT_MARKER".equals(finding.getRuleId())
                        && filename.equals(finding.getFilePath())
                        && Integer.valueOf(lineNumber).equals(finding.getLine()));
    }

    /**
     * 记录 patch 新增行的文件行号和内容。
     */
    private record PatchLine(int lineNumber, String content) {
    }
}
