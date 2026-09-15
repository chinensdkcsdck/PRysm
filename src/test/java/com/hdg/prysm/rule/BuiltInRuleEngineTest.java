package com.hdg.prysm.rule;

import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.diff.PrChangedFileStatus;
import com.hdg.prysm.diff.PrDiff;
import com.hdg.prysm.execution.ContextStatus;
import com.hdg.prysm.execution.ContextStatusCode;
import com.hdg.prysm.execution.PromptPayload;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewFinding;
import com.hdg.prysm.execution.ReviewTargetFile;
import com.hdg.prysm.execution.RuleEngineResult;
import com.hdg.prysm.review.PrReviewFileContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInRuleEngineTest {

    /**
     * 规则目录应稳定包含 20 条内置规则，防止扩展时意外漏注册。
     */
    @Test
    void shouldExposeTwentyBuiltInRules() {
        assertEquals(20, BuiltInRuleEngine.supportedRuleCount());
    }

    /**
     * 每条规则都应能在新增行上产生带位置、分类和置信度的 finding。
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("builtInRuleCases")
    void shouldFindEveryBuiltInRule(String expectedRuleId, String filename, String addedLine) {
        ReviewExecutionInput input = newInput(newTargetFile(
                filename,
                "@@ -9,0 +10,1 @@\n+" + addedLine,
                "safe context",
                true
        ));

        RuleEngineResult result = new BuiltInRuleEngine().run(input);

        assertEquals(1, result.getFindings().size());
        ReviewFinding finding = result.getFindings().get(0);
        assertEquals(expectedRuleId, finding.getRuleId());
        assertEquals(filename, finding.getFilePath());
        assertEquals(10, finding.getLine());
        assertEquals("RIGHT", finding.getSide());
        assertEquals("HIGH", finding.getConfidence());
        assertNotNull(finding.getCategory());
        assertTrue(finding.getSuggestion() != null && !finding.getSuggestion().isBlank());
    }

    /**
     * 常见安全写法和注释示例不应触发规则，控制明显误报。
     */
    @ParameterizedTest(name = "safe: {0}")
    @MethodSource("safeLineCases")
    void shouldIgnoreSafeOrNonExecutableLines(String filename, String addedLine) {
        ReviewExecutionInput input = newInput(newTargetFile(
                filename,
                "@@ -1,0 +1,1 @@\n+" + addedLine,
                addedLine,
                true
        ));

        RuleEngineResult result = new BuiltInRuleEngine().run(input);

        assertTrue(result.getFindings().isEmpty());
    }

    /**
     * Diff 的 no-newline 元数据不能推进新文件行号。
     */
    @Test
    void shouldKeepLineNumberAcrossNoNewlineMetadata() {
        ReviewExecutionInput input = newInput(newTargetFile(
                "src/App.java",
                "@@ -1,1 +1,2 @@\n context\n\\ No newline at end of file\n+System.out.println(\"debug\");",
                "context\nSystem.out.println(\"debug\");",
                true
        ));

        RuleEngineResult result = new BuiltInRuleEngine().run(input);

        assertEquals(1, result.getFindings().size());
        assertEquals(2, result.getFindings().get(0).getLine());
    }

    /**
     * 删除行中的历史问题不属于本次新增风险，不应被上报。
     */
    @Test
    void shouldIgnoreFindingsThatOnlyExistOnRemovedLines() {
        ReviewExecutionInput input = newInput(newTargetFile(
                "src/App.java",
                "@@ -1,1 +1,1 @@\n-System.out.println(\"old\");\n+logger.info(\"new\");",
                "logger.info(\"new\");",
                true
        ));

        RuleEngineResult result = new BuiltInRuleEngine().run(input);

        assertTrue(result.getFindings().isEmpty());
    }

    /**
     * 内置规则应识别新增行里的 Java 标准输出。
     */
    @Test
    void shouldFindSystemOutInAddedJavaLine() {
        ReviewExecutionInput input = newInput(newTargetFile(
                "src/App.java",
                """
                @@ -1,1 +1,2 @@
                 class App {
                +    System.out.println("debug");
                """,
                "class App {\n    System.out.println(\"debug\");",
                true
        ));

        RuleEngineResult result = new BuiltInRuleEngine().run(input);

        assertEquals(1, result.getFindings().size());
        ReviewFinding finding = result.getFindings().get(0);
        assertEquals("BUILTIN_SYSTEM_OUT", finding.getRuleId());
        assertEquals("src/App.java", finding.getFilePath());
        assertEquals(2, finding.getLine());
        assertEquals("RIGHT", finding.getSide());
        assertEquals("发现调试输出", finding.getTitle());
    }

    /**
     * 内置规则应识别 snippet 中残留的合并冲突标记。
     */
    @Test
    void shouldFindConflictMarkerInSnippet() {
        ReviewExecutionInput input = newInput(newTargetFile(
                "src/App.java",
                """
                @@ -1,1 +1,1 @@
                 class App {}
                """,
                "class App {}\n<<<<<<< HEAD",
                true
        ));

        RuleEngineResult result = new BuiltInRuleEngine().run(input);

        assertEquals(1, result.getFindings().size());
        ReviewFinding finding = result.getFindings().get(0);
        assertEquals("BUILTIN_CONFLICT_MARKER", finding.getRuleId());
        assertEquals(2, finding.getLine());
        assertEquals("HIGH", finding.getSeverity());
        assertEquals("发现合并冲突标记", finding.getTitle());
    }

    /**
     * 未选中的文件不应被内置规则检查。
     */
    @Test
    void shouldIgnoreUnselectedFiles() {
        ReviewExecutionInput input = newInput(newTargetFile(
                "src/App.java",
                """
                @@ -1,1 +1,1 @@
                +System.out.println("debug");
                """,
                "System.out.println(\"debug\");",
                false
        ));

        RuleEngineResult result = new BuiltInRuleEngine().run(input);

        assertTrue(result.getFindings().isEmpty());
        assertEquals("内置规则未发现问题。", result.getSummary());
    }

    private static Stream<Arguments> builtInRuleCases() {
        return Stream.of(
                Arguments.of("BUILTIN_CONFLICT_MARKER", "README.md", "<<<<<<< HEAD"),
                Arguments.of("BUILTIN_SYSTEM_OUT", "src/App.java", "System.err.println(\"debug\");"),
                Arguments.of("BUILTIN_PRIVATE_KEY", "secret.pem", "-----BEGIN " + "PRIVATE KEY-----"),
                Arguments.of("BUILTIN_PRIVATE_KEY", "Notes.java", "// -----BEGIN " + "PRIVATE KEY-----"),
                Arguments.of("BUILTIN_AWS_ACCESS_KEY", ".env", "AWS_KEY=" + "AKIA" + "A".repeat(16)),
                Arguments.of("BUILTIN_GITHUB_TOKEN", ".env", "TOKEN=" + "ghp_" + "a".repeat(36)),
                Arguments.of("BUILTIN_SLACK_CREDENTIAL", ".env", "SLACK=" + "xoxb-" + "a".repeat(12)),
                Arguments.of("BUILTIN_URL_CREDENTIALS", "application.yml", "url: https://admin:password@example.com/db"),
                Arguments.of("BUILTIN_PRINT_STACK_TRACE", "src/App.java", "exception.printStackTrace();"),
                Arguments.of("BUILTIN_EMPTY_CATCH", "src/App.java", "tryWork(); } catch (Exception exception) {}"),
                Arguments.of("BUILTIN_RUNTIME_EXEC", "src/App.java", "Runtime.getRuntime().exec(command);"),
                Arguments.of("BUILTIN_SQL_CONCAT", "src/App.java", "String sql = \"SELECT * FROM users WHERE id=\" + userId;"),
                Arguments.of("BUILTIN_MD5", "src/App.java", "MessageDigest.getInstance(\"MD5\");"),
                Arguments.of("BUILTIN_SHA1", "src/App.java", "MessageDigest.getInstance(\"SHA-1\");"),
                Arguments.of("BUILTIN_ECB_CIPHER", "src/App.java", "Cipher.getInstance(\"AES/ECB/PKCS5Padding\");"),
                Arguments.of("BUILTIN_STRING_REFERENCE_COMPARE", "src/App.java", "if (status == \"READY\") {}"),
                Arguments.of("BUILTIN_BIGDECIMAL_DOUBLE", "src/App.java", "BigDecimal amount = new BigDecimal(0.1);"),
                Arguments.of("BUILTIN_WILDCARD_CORS", "src/App.java", "@CrossOrigin(origins = \"*\")"),
                Arguments.of("BUILTIN_WILDCARD_CORS", "src/App.java", "@CrossOrigin(origins = {\"*\"})"),
                Arguments.of("BUILTIN_CSRF_DISABLED", "src/App.java", "http.csrf(AbstractHttpConfigurer::disable);"),
                Arguments.of("BUILTIN_CSRF_DISABLED", "src/App.java", "http.csrf(csrf -> csrf.disable());"),
                Arguments.of("BUILTIN_WORKFLOW_WRITE_ALL", ".github/workflows/review.yml", "permissions: write-all"),
                Arguments.of("BUILTIN_WORKFLOW_FLOATING_REF", ".github/workflows/review.yml", "- uses: vendor/action@main")
        );
    }

    private static Stream<Arguments> safeLineCases() {
        return Stream.of(
                Arguments.of("src/App.java", "// System.out.println(\"example\");"),
                Arguments.of("src/App.java", "if (\"READY\".equals(status)) {}"),
                Arguments.of("src/App.java", "MessageDigest.getInstance(\"SHA-256\");"),
                Arguments.of("src/App.java", "PreparedStatement statement = connection.prepareStatement(sql);"),
                Arguments.of("src/App.java", "String sql = \"SELECT id \" + \"FROM users\";"),
                Arguments.of("src/App.java", "BigDecimal amount = new BigDecimal(\"0.1\");"),
                Arguments.of("README.md", "System.out.println(\"example\");"),
                Arguments.of(".github/workflows/review.yml", "permissions: read-all"),
                Arguments.of(".github/workflows/review.yml", "- uses: actions/checkout@v4"),
                Arguments.of("application.yml", "token: ${GITHUB_TOKEN}")
        );
    }

    /**
     * 创建一个只包含单个目标文件的执行输入。
     */
    private static ReviewExecutionInput newInput(ReviewTargetFile targetFile) {
        PrContext context = new PrContext("owner", "repo", 12);
        PrDiff diff = new PrDiff(context, List.of(targetFile.getChangedFile()));
        return new ReviewExecutionInput(
                context,
                diff,
                List.of(targetFile),
                new ContextStatus(ContextStatusCode.FULL, "ready"),
                new PromptPayload("system", "user", "{}")
        );
    }

    /**
     * 创建一个测试用目标文件。
     */
    private static ReviewTargetFile newTargetFile(
            String filename,
            String patch,
            String snippet,
            boolean selected
    ) {
        return new ReviewTargetFile(
                new PrChangedFile(filename, PrChangedFileStatus.MODIFIED, 1, 0, patch),
                List.of(new PrReviewFileContext.Snippet(1, Math.max(1, snippet.split("\\R").length), snippet)),
                0,
                selected,
                "test"
        );
    }
}
