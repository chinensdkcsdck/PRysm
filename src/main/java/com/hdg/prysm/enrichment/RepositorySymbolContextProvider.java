package com.hdg.prysm.enrichment;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Builds a bounded Java AST index and follows source-level definitions, direct callers and tests.
 */
@Component
public class RepositorySymbolContextProvider implements CrossFileContextProvider {

    private static final Set<String> IGNORED_SYMBOLS = Set.of(
            "String", "Integer", "Long", "Double", "Float", "Boolean", "Object", "Class",
            "List", "Set", "Map", "Collection", "Optional", "Stream", "Override", "SuppressWarnings",
            "System", "Runtime", "Exception", "RuntimeException", "IllegalArgumentException"
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
    private final JavaParser javaParser;

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
        this(Path.of(repositoryRoot), maxIndexedFiles, maxSymbols, maxSnippets,
                snippetWindowLines, maxContextCharacters, maxFileSizeBytes);
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
        this.javaParser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
    }

    @Override
    public CrossFileContext build(ReviewExecutionInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Review execution input must not be null");
        }

        List<IndexedJavaFile> files = indexJavaFiles();
        LinkedHashSet<String> symbols = changedSymbols(input, files);
        if (symbols.isEmpty()) {
            return CrossFileContext.empty();
        }

        List<RelatedLocation> locations = relatedLocations(files, symbols, changedPaths(input));
        if (locations.isEmpty()) {
            return CrossFileContext.empty();
        }

        StringBuilder prompt = new StringBuilder("跨文件依赖上下文\n");
        prompt.append("- 追踪范围: Java AST 定义、直接调用方和相关测试（源码一层调用图）\n");
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

    private LinkedHashSet<String> changedSymbols(ReviewExecutionInput input, List<IndexedJavaFile> files) {
        Map<String, IndexedJavaFile> byPath = new HashMap<>();
        for (IndexedJavaFile file : files) {
            byPath.put(file.path(), file);
        }

        LinkedHashSet<String> symbols = new LinkedHashSet<>();
        for (ReviewTargetFile targetFile : input.getFiles()) {
            PrChangedFile changedFile = targetFile.getChangedFile();
            if (!targetFile.isSelected() || !isJavaPath(changedFile.getFilename())) {
                continue;
            }
            IndexedJavaFile file = byPath.get(UnifiedDiffParser.normalizePath(changedFile.getFilename()));
            if (file == null) {
                continue;
            }
            collectChangedNodes(file.unit(), UnifiedDiffParser.addedLineNumbers(changedFile.getPatch()), symbols);
            if (symbols.size() >= maxSymbols) {
                break;
            }
        }
        return symbols;
    }

    private void collectChangedNodes(CompilationUnit unit, Set<Integer> changedLines, LinkedHashSet<String> symbols) {
        for (TypeDeclaration<?> declaration : unit.findAll(TypeDeclaration.class)) {
            addWhenChanged(declaration, declaration.getNameAsString(), changedLines, symbols);
        }
        for (CallableDeclaration<?> declaration : unit.findAll(CallableDeclaration.class)) {
            addWhenChanged(declaration, declaration.getNameAsString(), changedLines, symbols);
        }
        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            addWhenChanged(call, call.getNameAsString(), changedLines, symbols);
        }
        for (ObjectCreationExpr creation : unit.findAll(ObjectCreationExpr.class)) {
            addWhenChanged(creation, creation.getType().getNameAsString(), changedLines, symbols);
        }
        for (ClassOrInterfaceType type : unit.findAll(ClassOrInterfaceType.class)) {
            addWhenChanged(type, type.getNameAsString(), changedLines, symbols);
        }
    }

    private void addWhenChanged(Node node, String symbol, Set<Integer> changedLines, LinkedHashSet<String> symbols) {
        if (symbols.size() < maxSymbols && !IGNORED_SYMBOLS.contains(symbol) && overlaps(node, changedLines)) {
            symbols.add(symbol);
        }
    }

    private static boolean overlaps(Node node, Set<Integer> lines) {
        return node.getRange().map(range -> lines.stream()
                .anyMatch(line -> line >= range.begin.line && line <= range.end.line)).orElse(false);
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
            paths = stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
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
                if (Files.size(path) > maxFileSizeBytes) {
                    continue;
                }
                String source = Files.readString(path, StandardCharsets.UTF_8);
                javaParser.parse(source).getResult().ifPresent(unit -> files.add(new IndexedJavaFile(
                        UnifiedDiffParser.normalizePath(repositoryRoot.relativize(path).toString()),
                        source.lines().toList(),
                        unit
                )));
            } catch (IOException ignored) {
                // One unreadable or unparsable source file must not stop the complete review.
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
            Set<String> symbols,
            Set<String> changedPaths
    ) {
        List<RelatedLocation> locations = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (IndexedJavaFile file : files) {
            if (changedPaths.contains(file.path())) {
                continue;
            }
            boolean testFile = isTestPath(file.path());
            for (TypeDeclaration<?> declaration : file.unit().findAll(TypeDeclaration.class)) {
                addDefinition(file, declaration, declaration.getNameAsString(), symbols, testFile, locations, seen);
            }
            for (CallableDeclaration<?> declaration : file.unit().findAll(CallableDeclaration.class)) {
                addDefinition(file, declaration, declaration.getNameAsString(), symbols, testFile, locations, seen);
            }
            for (MethodCallExpr call : file.unit().findAll(MethodCallExpr.class)) {
                addCall(file, call, call.getNameAsString(), symbols, testFile, locations, seen);
            }
            for (ObjectCreationExpr creation : file.unit().findAll(ObjectCreationExpr.class)) {
                addCall(file, creation, creation.getType().getNameAsString(), symbols, testFile, locations, seen);
            }
        }
        locations.sort(Comparator.comparingInt((RelatedLocation location) -> location.relation().priority)
                .thenComparing(RelatedLocation::path)
                .thenComparingInt(RelatedLocation::startLine));
        return locations;
    }

    private void addDefinition(
            IndexedJavaFile file, Node node, String symbol, Set<String> symbols, boolean testFile,
            List<RelatedLocation> locations, Set<String> seen
    ) {
        if (symbols.contains(symbol)) {
            addLocation(file, node, symbol, testFile ? Relation.TEST : Relation.DEFINITION, locations, seen);
        }
    }

    private void addCall(
            IndexedJavaFile file, Node node, String symbol, Set<String> symbols, boolean testFile,
            List<RelatedLocation> locations, Set<String> seen
    ) {
        if (symbols.contains(symbol)) {
            addLocation(file, node, symbol, testFile ? Relation.TEST : Relation.CALLER, locations, seen);
        }
    }

    private void addLocation(
            IndexedJavaFile file, Node node, String symbol, Relation relation,
            List<RelatedLocation> locations, Set<String> seen
    ) {
        node.getRange().ifPresent(range -> {
            String key = file.path() + ':' + range.begin.line;
            if (seen.add(key)) {
                locations.add(location(file, range.begin.line - 1, symbol, relation));
            }
        });
    }

    private RelatedLocation location(IndexedJavaFile file, int lineIndex, String symbol, Relation relation) {
        int startIndex = Math.max(0, lineIndex - snippetWindowLines);
        int endIndex = Math.min(file.lines().size() - 1, lineIndex + snippetWindowLines);
        StringBuilder content = new StringBuilder();
        for (int index = startIndex; index <= endIndex; index++) {
            content.append(index + 1).append(": ").append(file.lines().get(index)).append('\n');
        }
        return new RelatedLocation(file.path(), startIndex + 1, endIndex + 1,
                symbol, relation, content.toString().stripTrailing());
    }

    private String renderLocation(RelatedLocation location, int index) {
        return "依赖片段 " + index + " [" + location.relation().label + "] "
                + location.path() + " 行 " + location.startLine() + '-' + location.endLine() + "，符号 "
                + location.symbol() + "\n```java\n" + location.content().replace("```", "'''") + "\n```\n";
    }

    private static boolean isJavaPath(String path) {
        return path != null && path.toLowerCase(Locale.ROOT).endsWith(".java");
    }

    private static boolean isTestPath(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.contains("/test/") || normalized.endsWith("test.java") || normalized.endsWith("tests.java");
    }

    private enum Relation {
        DEFINITION(0, "定义"), CALLER(1, "直接调用方"), TEST(2, "相关测试");

        private final int priority;
        private final String label;

        Relation(int priority, String label) {
            this.priority = priority;
            this.label = label;
        }
    }

    private record IndexedJavaFile(String path, List<String> lines, CompilationUnit unit) {
    }

    private record RelatedLocation(
            String path, int startLine, int endLine, String symbol, Relation relation, String content
    ) {
    }
}
