package com.hdg.prysm.quality;

import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.diff.PrDiff;
import com.hdg.prysm.diff.UnifiedDiffParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Immutable index of target-side lines that GitHub can anchor to the current diff.
 */
public class ReviewableLineIndex {

    private final Map<String, FileLines> files;

    public ReviewableLineIndex(PrDiff diff) {
        if (diff == null) {
            throw new IllegalArgumentException("Pull request diff must not be null");
        }

        Map<String, FileLines> indexedFiles = new LinkedHashMap<>();
        for (PrChangedFile file : diff.getChangedFiles()) {
            String path = UnifiedDiffParser.normalizePath(file.getFilename());
            indexedFiles.put(path, new FileLines(path, UnifiedDiffParser.addedLines(file.getPatch())));
        }
        this.files = Map.copyOf(indexedFiles);
    }

    public boolean containsFile(String path) {
        return files.containsKey(UnifiedDiffParser.normalizePath(path));
    }

    public String canonicalPath(String path) {
        FileLines file = files.get(UnifiedDiffParser.normalizePath(path));
        return file == null ? null : file.path();
    }

    public boolean isReviewable(String path, Integer lineNumber) {
        if (lineNumber == null || lineNumber <= 0) {
            return false;
        }
        FileLines file = files.get(UnifiedDiffParser.normalizePath(path));
        return file != null && file.lines().stream().anyMatch(line -> line.lineNumber() == lineNumber);
    }

    /**
     * Relocates only when the normalized code line has one unambiguous match in the target-side diff.
     */
    public OptionalInt relocate(String path, String codeSnippet) {
        FileLines file = files.get(UnifiedDiffParser.normalizePath(path));
        String needle = normalizeCode(codeSnippet);
        if (file == null || needle.isBlank()) {
            return OptionalInt.empty();
        }

        List<Integer> matches = new ArrayList<>();
        for (UnifiedDiffParser.AddedLine line : file.lines()) {
            if (needle.equals(normalizeCode(line.content()))) {
                matches.add(line.lineNumber());
            }
        }
        return matches.size() == 1 ? OptionalInt.of(matches.get(0)) : OptionalInt.empty();
    }

    private static String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        return code.strip().replaceAll("\\s+", " ");
    }

    private record FileLines(String path, List<UnifiedDiffParser.AddedLine> lines) {
        private FileLines {
            lines = List.copyOf(lines);
        }
    }
}
