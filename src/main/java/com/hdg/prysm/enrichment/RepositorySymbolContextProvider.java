package com.hdg.prysm.enrichment;

import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.diff.UnifiedDiffParser;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewTargetFile;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Builds a lightweight Java symbol index and returns bounded cross-file context.
 *
 * This is deliberately not a compiler: it only follows direct definitions, callers and tests,
 * and fails closed when a repository file cannot be read safely.
 */
@Component
public class RepositorySymbolContextProvider implements CrossFileContextProvider {

    private static final Pattern TYPE_DECLARATION =
            Pattern.compile("\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
            "\\b(?:public|protected|private|static|final|abstract|synchronized|native|default)"
                    + "(?:[\\w<>\\[\\],.?@ ]+)?\\s+([a-zA-Z_$][A-Za-z0-9_$]*)\\s*\\("
    );
    private static final Pattern METHOD_CALL =
            Pattern.compile("\\b([a-zA-Z_$][A-Za-z0-9_$]*)\\s*\\(");
    private static final Pattern TYPE_REFERENCE =
            Pattern.compile("\\b([A-Z][A-Za-z0-9_$]{2,})\\b");
    private static final Set<String> IGNORED_SYMBOLS = Set.of(
            "String", "Integer", "Long", "Double", "Float", "Boolean", "Object", "Class",
            "List", "Set", "Map", "Collection", "Optional", "Stream", "Override", "SuppressWarnings",
            "System", "Runtime", "Exception", "RuntimeException", "IllegalArgumentException",
            "if", "for", "while", "switch", "catch", "return", "new", "super", "this"
    );
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".idea", ".gradle", "target", "build", "dist", "node_modules", "vendor"
    );

    private final Path repositoryRoot;
    private final int maxIndexedFiles;
    private final int maxSymbols;
    private final int maxSnippets;
    private final int snippetWindowLines;
    private final int maxContextCharacters;
    private final long maxFileSizeBytes;

    @Autowired
    public RepositorySymbolContextProvider(
            @Value("${prysm.review.cross-file.repository-root:${prysm.review.repository-root:${user.dir}}}") String repositoryRoot,
            @Value("${prysm.review.cross-file.max-indexed-files:1000}") int maxIndexedFiles,
            @Value("${prysm.review.cross-file.max-symbols:20}") int maxSymbols,
            @Value("${prysm.review.cross-file.max-snippets:8}") int maxSnippets,
            @Value("${prysm.review.cross-file.window-lines:4}") int snippetWindowLines,
            @Value("${prysm.review.cross-file.max-context-chars:12000}") int maxContextCharacters,
            @Value("${prysm.review.cross-file.max-file-size-bytes:262144}") long maxFileSizeBytes
    ) {
        this(
                Path.of(repositoryRoot),
                maxIndexedFiles,
                maxSymbols,
                maxSnippets,
                snippetWindowLines,
                maxContextCharacters,
                maxFileSizeBytes
        );
    }

    RepositorySymbolContextProvider(
            Path repositoryRoot,
            int maxIndexedFiles,
            int maxSymbols,
            int maxSnippets,
            int snippetWindowLines,
            int maxContextCharacters,
            long maxFileSizeBytes
    ) {
        if (repositoryRoot == null) {
            throw new IllegalArgumentException("Repository root must not be null");
        }
        if (maxIndexedFiles <= 0 || maxSymbols <= 0 || maxSnippets <= 0 || maxContextCharacters <= 0) {
            throw new IllegalArgumentException("Cross-file context limits must be positive");
        }
        if (snippetWindowLines < 0 || maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("Cross-file context window and file size limits are invalid");
        }
        this.repositoryRoot = repositoryRoot.toAbsolutePath().normalize();
        this.maxIndexedFiles = maxIndexedFiles;
        this.maxSymbols = maxSymbols;
        this.maxSnippets = maxSnippets;
        this.snippetWindowLines = snippetWindowLines;
        this.maxContextCharacters = maxContextCharacters;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public CrossFileContext build(ReviewExecutionInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Review execution input must not be null");
        }

        LinkedHashSet<String> symbols = changedSymbols(input);
        if (symbols.isEmpty()) {
            return CrossFileContext.empty();
        }

        Set<String> changedPaths = changedPaths(input);
        List<IndexedJavaFile> files = indexJavaFiles();
        List<RelatedLocation> locations = relatedLocations(files, symbols, changedPaths);
        if (locations.isEmpty()) {
            return CrossFileContext.empty();
        }

        StringBuilder prompt = new StringBuilder("跨文件依赖上下文\n");
        prompt.append("- 追踪范围: Java 直接定义、直接调用方和相关测试（1 层）\n");
        prompt.append("- 变更涉及符号: ").append(String.join(", ", symbols)).append('\n');

        int appended = 0;
        boolean truncated = false;
        for (RelatedLocation location : locations) {
            if (appended >= maxSnippets) {
                truncated = true;
                break;
            }
            String block = renderLocation(location, appended + 1);
            if (prompt.length() + block.length() > maxContextCharacters) {
                truncated = true;
                break;
            }
            prompt.append(block);
            appended++;
        }
        if (appended == 0) {
            return CrossFileContext.empty();
        }
        if (truncated) {
            prompt.append("- 说明: 跨文件上下文已按预算裁剪。\n");
        }
        return new CrossFileContext(prompt.toString(), symbols.size(), appended, truncated);
    }

    private LinkedHashSet<String> changedSymbols(ReviewExecutionInput input) {
        LinkedHashSet<String> symbols = new LinkedHashSet<>();
        for (ReviewTargetFile targetFile : input.getFiles()) {
            PrChangedFile file = targetFile.getChangedFile();
            if (!targetFile.isSelected() || !isJavaPath(file.getFilename())) {
                continue;
            }
            for (UnifiedDiffParser.AddedLine line : UnifiedDiffParser.addedLines(file.getPatch())) {
                collectMatches(TYPE_DECLARATION, line.content(), symbols);
                collectMatches(METHOD_DECLARATION, line.content(), symbols);
                collectMatches(TYPE_REFERENCE, line.content(), symbols);
                collectMatches(METHOD_CALL, line.content(), symbols);
                if (symbols.size() >= maxSymbols) {
                    return symbols;
                }
            }
        }
        return symbols;
    }

    private void collectMatches(Pattern pattern, String content, LinkedHashSet<String> symbols) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find() && symbols.size() < maxSymbols) {
            String symbol = matcher.group(1);
            if (!IGNORED_SYMBOLS.contains(symbol)) {
                symbols.add(symbol);
            }
        }
    }

    private Set<String> changedPaths(ReviewExecutionInput input) {
        Set<String> paths = new HashSet<>();
        for (PrChangedFile file : input.getDiff().getChangedFiles()) {
            paths.add(UnifiedDiffParser.normalizePath(file.getFilename()));
        }
        return paths;
    }

    private List<IndexedJavaFile> indexJavaFiles() {
        if (!Files.isDirectory(repositoryRoot, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }

        List<Path> paths;
        try (Stream<Path> stream = Files.walk(repositoryRoot)) {
            paths = stream
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(this::isIndexableJavaFile)
                    .sorted()
                    .limit(maxIndexedFiles)
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }

        List<IndexedJavaFile> files = new ArrayList<>();
        for (Path path : paths) {
            try {
                if (Files.size(path) <= maxFileSizeBytes) {
                    files.add(new IndexedJavaFile(
                            UnifiedDiffParser.normalizePath(repositoryRoot.relativize(path).toString()),
                            Files.readAllLines(path, StandardCharsets.UTF_8)
                    ));
                }
            } catch (IOException ignored) {
                // One unreadable source file must not stop the complete review.
            }
        }
        return files;
    }

    private boolean isIndexableJavaFile(Path path) {
        Path relative = repositoryRoot.relativize(path);
        for (Path segment : relative) {
            if (EXCLUDED_DIRECTORIES.contains(segment.toString().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return isJavaPath(path.getFileName().toString());
    }

    private List<RelatedLocation> relatedLocations(
            List<IndexedJavaFile> files,
            LinkedHashSet<String> symbols,
            Set<String> changedPaths
    ) {
        List<RelatedLocation> locations = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, SymbolPatterns> patternsBySymbol = new LinkedHashMap<>();
        for (String symbol : symbols) {
            patternsBySymbol.put(symbol, SymbolPatterns.forSymbol(symbol));
        }
        for (IndexedJavaFile file : files) {
            if (changedPaths.contains(file.path())) {
                continue;
            }
            boolean testFile = isTestPath(file.path());
            for (int index = 0; index < file.lines().size(); index++) {
                String line = file.lines().get(index);
                for (String symbol : symbols) {
                    Relation relation = relation(line, symbol, patternsBySymbol.get(symbol), testFile);
                    if (relation == null) {
                        continue;
                    }
                    String key = file.path() + ':' + (index + 1);
                    if (seen.add(key)) {
                        locations.add(location(file, index, symbol, relation));
                    }
                    break;
                }
            }
        }
        locations.sort(Comparator
                .comparingInt((RelatedLocation location) -> location.relation().priority)
                .thenComparing(RelatedLocation::path)
                .thenComparingInt(RelatedLocation::startLine));
        return locations;
    }

    private Relation relation(String line, String symbol, SymbolPatterns patterns, boolean testFile) {
        if (!patterns.word().matcher(line).find()) {
            return null;
        }
        if (patterns.typeDefinition().matcher(line).find()
                || patterns.methodDefinition().matcher(line).find()) {
            return Relation.DEFINITION;
        }
        if (testFile) {
            return Relation.TEST;
        }
        if (patterns.call().matcher(line).find()
                || Character.isUpperCase(symbol.charAt(0))) {
            return Relation.CALLER;
        }
        return null;
    }

    private RelatedLocation location(IndexedJavaFile file, int lineIndex, String symbol, Relation relation) {
        int startIndex = Math.max(0, lineIndex - snippetWindowLines);
        int endIndex = Math.min(file.lines().size() - 1, lineIndex + snippetWindowLines);
        StringBuilder content = new StringBuilder();
        for (int index = startIndex; index <= endIndex; index++) {
            content.append(index + 1).append(": ").append(file.lines().get(index)).append('\n');
        }
        return new RelatedLocation(
                file.path(),
                startIndex + 1,
                endIndex + 1,
                symbol,
                relation,
                content.toString().stripTrailing()
        );
    }

    private String renderLocation(RelatedLocation location, int index) {
        return "依赖片段 " + index
                + " [" + location.relation().label + "] "
                + location.path() + " 行 " + location.startLine() + '-' + location.endLine() + "，符号 "
                + location.symbol() + "\n```java\n"
                + location.content().replace("```", "'''")
                + "\n```\n";
    }

    private static boolean isJavaPath(String path) {
        return path != null && path.toLowerCase(Locale.ROOT).endsWith(".java");
    }

    private static boolean isTestPath(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.contains("/test/") || normalized.endsWith("test.java") || normalized.endsWith("tests.java");
    }

    private enum Relation {
        DEFINITION(0, "定义"),
        CALLER(1, "直接调用方"),
        TEST(2, "相关测试");

        private final int priority;
        private final String label;

        Relation(int priority, String label) {
            this.priority = priority;
            this.label = label;
        }
    }

    private record IndexedJavaFile(String path, List<String> lines) {
    }

    private record RelatedLocation(
            String path,
            int startLine,
            int endLine,
            String symbol,
            Relation relation,
            String content
    ) {
    }

    private record SymbolPatterns(
            Pattern word,
            Pattern typeDefinition,
            Pattern methodDefinition,
            Pattern call
    ) {
        private static SymbolPatterns forSymbol(String symbol) {
            String quoted = Pattern.quote(symbol);
            return new SymbolPatterns(
                    Pattern.compile("\\b" + quoted + "\\b"),
                    Pattern.compile("\\b(?:class|interface|enum|record)\\s+" + quoted + "\\b"),
                    Pattern.compile("\\b" + quoted + "\\s*\\([^;]*\\)\\s*(?:throws [^{]+)?\\{"),
                    Pattern.compile("\\b" + quoted + "\\s*\\(")
            );
        }
    }
}
