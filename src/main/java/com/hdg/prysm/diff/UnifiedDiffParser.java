package com.hdg.prysm.diff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses new-side line positions from GitHub unified diff patches.
 */
public final class UnifiedDiffParser {

    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*$");

    private UnifiedDiffParser() {
    }

    /**
     * Returns added lines with their absolute line numbers in the target file.
     */
    public static List<AddedLine> addedLines(String patch) {
        if (patch == null || patch.isBlank()) {
            return List.of();
        }

        List<AddedLine> result = new ArrayList<>();
        int currentNewLine = -1;
        for (String line : patch.split("\\R")) {
            Matcher header = HUNK_HEADER.matcher(line);
            if (header.matches()) {
                currentNewLine = Integer.parseInt(header.group(1));
                continue;
            }
            if (currentNewLine < 0 || line.startsWith("+++") || line.startsWith("---")) {
                continue;
            }
            if (line.startsWith("+")) {
                result.add(new AddedLine(currentNewLine, line.substring(1)));
                currentNewLine++;
            } else if (!line.startsWith("-") && !line.startsWith("\\")) {
                currentNewLine++;
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Builds a normalized file-to-added-lines index for a complete pull request diff.
     */
    public static Map<String, List<AddedLine>> addedLinesByFile(PrDiff diff) {
        if (diff == null) {
            throw new IllegalArgumentException("Pull request diff must not be null");
        }

        Map<String, List<AddedLine>> result = new LinkedHashMap<>();
        for (PrChangedFile file : diff.getChangedFiles()) {
            result.put(normalizePath(file.getFilename()), addedLines(file.getPatch()));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns all GitHub-reviewable new-side line numbers for a patch.
     */
    public static Set<Integer> addedLineNumbers(String patch) {
        Set<Integer> result = new LinkedHashSet<>();
        for (AddedLine line : addedLines(patch)) {
            result.add(line.lineNumber());
        }
        return Collections.unmodifiableSet(result);
    }

    public static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    public record AddedLine(int lineNumber, String content) {
        public AddedLine {
            if (lineNumber <= 0) {
                throw new IllegalArgumentException("Added line number must be positive");
            }
            if (content == null) {
                throw new IllegalArgumentException("Added line content must not be null");
            }
        }
    }
}
