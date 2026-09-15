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
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内置轻量规则引擎。
 *
 * 规则只扫描本次 patch 的新增行，避免把历史问题重复上报。合并冲突标记是例外，
 * 它还会检查变更点附近的 snippet，防止异常 patch 漏掉未解决的冲突。
 */
@Component
public class BuiltInRuleEngine implements RuleEngine {

    private static final Pattern HUNK_HEADER_PATTERN =
            Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*$");
    private static final String SOURCE = "builtin";
    private static final String RIGHT_SIDE = "RIGHT";

    private static final Predicate<String> ANY_FILE = filename -> true;
    private static final Predicate<String> JAVA_FILE = filename -> lower(filename).endsWith(".java");
    private static final Predicate<String> WORKFLOW_FILE = filename -> {
        String normalized = lower(filename).replace('\\', '/');
        return normalized.startsWith(".github/workflows/")
                && (normalized.endsWith(".yml") || normalized.endsWith(".yaml"));
    };

    private static final List<RuleDefinition> RULES = List.of(
            rule(
                    "BUILTIN_CONFLICT_MARKER", "HIGH", "CORRECTNESS", true,
                    "发现合并冲突标记",
                    "变更代码中仍包含合并冲突标记。",
                    "请先解决冲突标记，再合并这个 Pull Request。",
                    ANY_FILE,
                    line -> {
                        String trimmed = line.trim();
                        return trimmed.startsWith("<<<<<<<")
                                || trimmed.startsWith("=======")
                                || trimmed.startsWith(">>>>>>>");
                    }
            ),
            regexRule(
                    "BUILTIN_SYSTEM_OUT", "LOW", "MAINTAINABILITY",
                    "发现调试输出",
                    "变更的 Java 代码直接写入标准输出或标准错误。",
                    "请使用项目日志组件，或移除这处调试输出。",
                    JAVA_FILE,
                    "\\bSystem\\.(?:out|err)\\.print(?:ln|f)?\\s*\\("
            ),
            rawRegexRule(
                    "BUILTIN_PRIVATE_KEY", "CRITICAL", "SECURITY",
                    "发现私钥内容",
                    "变更中出现了私钥头，私钥一旦提交就应视为已经泄露。",
                    "请立即移除并轮换这把私钥，改用仓库密钥或专用密钥管理服务。",
                    ANY_FILE,
                    "-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----"
            ),
            rawRegexRule(
                    "BUILTIN_AWS_ACCESS_KEY", "CRITICAL", "SECURITY",
                    "发现 AWS 访问密钥",
                    "变更中出现了符合 AWS 访问密钥格式的内容。",
                    "请删除并轮换该密钥，通过环境变量或密钥管理服务注入。",
                    ANY_FILE,
                    "\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"
            ),
            rawRegexRule(
                    "BUILTIN_GITHUB_TOKEN", "CRITICAL", "SECURITY",
                    "发现 GitHub 访问令牌",
                    "变更中出现了符合 GitHub 访问令牌格式的内容。",
                    "请撤销并轮换该令牌，改用 GitHub Actions Secrets。",
                    ANY_FILE,
                    "\\b(?:gh[pousr]_[A-Za-z0-9]{36,255}|github_pat_[A-Za-z0-9_]{20,255})\\b"
            ),
            rawRegexRule(
                    "BUILTIN_SLACK_CREDENTIAL", "CRITICAL", "SECURITY",
                    "发现 Slack 凭据",
                    "变更中出现了 Slack 令牌或 Webhook 地址。",
                    "请删除并轮换凭据，通过密钥管理服务注入。",
                    ANY_FILE,
                    "(?:\\bxox[baprs]-[A-Za-z0-9-]{10,}\\b|hooks\\.slack\\.com/services/[A-Za-z0-9/_-]+)"
            ),
            rawRegexRule(
                    "BUILTIN_URL_CREDENTIALS", "HIGH", "SECURITY",
                    "发现 URL 明文账号密码",
                    "变更中的 URL 直接包含了账号和密码。",
                    "请移除 URL 中的凭据，并通过受控配置或密钥管理服务注入。",
                    ANY_FILE,
                    "https?://[^\\s/:@]+:[^\\s/@]+@[^\\s]+"
            ),
            regexRule(
                    "BUILTIN_PRINT_STACK_TRACE", "MEDIUM", "MAINTAINABILITY",
                    "发现直接打印异常栈",
                    "变更的 Java 代码直接打印异常栈，可能泄露内部信息且不利于日志治理。",
                    "请使用日志组件记录必要上下文，并按环境控制详细错误信息。",
                    JAVA_FILE,
                    "\\.printStackTrace\\s*\\("
            ),
            regexRule(
                    "BUILTIN_EMPTY_CATCH", "HIGH", "CORRECTNESS",
                    "发现空的异常处理",
                    "异常被捕获后直接忽略，可能隐藏真实故障。",
                    "请记录、转换或重新抛出异常，至少说明为什么可以安全忽略。",
                    JAVA_FILE,
                    "\\bcatch\\s*\\([^)]*\\)\\s*\\{\\s*}"
            ),
            regexRule(
                    "BUILTIN_RUNTIME_EXEC", "HIGH", "SECURITY",
                    "发现直接执行系统命令",
                    "变更使用 Runtime 执行系统命令，外部输入参与时可能造成命令注入。",
                    "请使用参数化的受控命令封装，并对白名单参数做严格校验。",
                    JAVA_FILE,
                    "\\bRuntime\\.getRuntime\\s*\\(\\s*\\)\\.exec\\s*\\("
            ),
            regexRule(
                    "BUILTIN_SQL_CONCAT", "HIGH", "SECURITY",
                    "发现 SQL 字符串拼接",
                    "SQL 语句通过字符串拼接加入动态内容，可能造成 SQL 注入。",
                    "请改用预编译语句和占位参数，不要直接拼接外部输入。",
                    JAVA_FILE,
                    "(?i)(?:\"[^\"]*\\b(?:select|insert|update|delete)\\b[^\"]*\"\\s*\\+(?!\\s*\")\\s*|\\b[A-Za-z_$][\\w.$()]*\\s*\\+\\s*\"[^\"]*\\b(?:select|insert|update|delete)\\b)"
            ),
            regexRule(
                    "BUILTIN_MD5", "HIGH", "SECURITY",
                    "发现弱哈希算法 MD5",
                    "MD5 已不适合密码、安全签名或完整性保护。",
                    "请根据用途改用 SHA-256 以上算法；密码存储应使用专用慢哈希。",
                    JAVA_FILE,
                    "\\bMessageDigest\\.getInstance\\s*\\(\\s*\"MD5\""
            ),
            regexRule(
                    "BUILTIN_SHA1", "HIGH", "SECURITY",
                    "发现弱哈希算法 SHA-1",
                    "SHA-1 已不适合安全签名或抗碰撞场景。",
                    "请根据用途改用 SHA-256 以上算法。",
                    JAVA_FILE,
                    "\\bMessageDigest\\.getInstance\\s*\\(\\s*\"SHA-?1\""
            ),
            regexRule(
                    "BUILTIN_ECB_CIPHER", "HIGH", "SECURITY",
                    "发现 ECB 加密模式",
                    "ECB 模式会暴露明文结构，不能提供可靠的数据保密性。",
                    "请使用带随机 nonce 的认证加密模式，例如 AES-GCM。",
                    JAVA_FILE,
                    "\\bCipher\\.getInstance\\s*\\(\\s*\"[A-Za-z0-9-]+/ECB/"
            ),
            regexRule(
                    "BUILTIN_STRING_REFERENCE_COMPARE", "MEDIUM", "CORRECTNESS",
                    "发现字符串引用比较",
                    "Java 使用 == 或 != 比较字符串时比较的是对象引用，不是字符串内容。",
                    "请使用 equals、Objects.equals，或在确实比较引用时写明原因。",
                    JAVA_FILE,
                    "(?:==|!=)\\s*\"(?:[^\"\\\\]|\\\\.)*\"|\"(?:[^\"\\\\]|\\\\.)*\"\\s*(?:==|!=)"
            ),
            regexRule(
                    "BUILTIN_BIGDECIMAL_DOUBLE", "MEDIUM", "CORRECTNESS",
                    "发现用浮点数创建 BigDecimal",
                    "浮点数字面量可能在创建 BigDecimal 前已经产生精度误差。",
                    "请使用字符串构造，或使用 BigDecimal.valueOf。",
                    JAVA_FILE,
                    "\\bnew\\s+BigDecimal\\s*\\(\\s*[+-]?\\d+\\.\\d+(?:[dDfF])?\\s*\\)"
            ),
            regexRule(
                    "BUILTIN_WILDCARD_CORS", "HIGH", "SECURITY",
                    "发现跨域来源全部放开",
                    "跨域配置允许任意来源访问，可能扩大敏感接口的暴露面。",
                    "请配置明确的可信来源列表，并同时检查凭据策略。",
                    JAVA_FILE,
                    "(?:@CrossOrigin\\s*\\(\\s*(?:(?:value|origins)\\s*=\\s*)?(?:\\{\\s*)?\"\\*\"|allowedOrigins\\s*\\(\\s*\"\\*\")"
            ),
            regexRule(
                    "BUILTIN_CSRF_DISABLED", "HIGH", "SECURITY",
                    "发现关闭 CSRF 防护",
                    "变更关闭了 CSRF 防护，基于 Cookie 的会话可能受到跨站请求伪造攻击。",
                    "请确认接口确实无状态；否则保留 CSRF 防护并配置令牌。",
                    JAVA_FILE,
                    "(?:\\.csrf\\s*\\(\\s*\\)\\s*\\.disable\\s*\\(|\\.csrf\\s*\\(\\s*[^)]*::disable\\s*\\)|\\.csrf\\s*\\([^;]*->[^;]*\\.disable\\s*\\()"
            ),
            regexRule(
                    "BUILTIN_WORKFLOW_WRITE_ALL", "CRITICAL", "WORKFLOW",
                    "发现工作流全局写权限",
                    "GitHub Actions 工作流向令牌授予了 write-all 权限。",
                    "请按最小权限原则，只为确实需要的资源授予写权限。",
                    WORKFLOW_FILE,
                    "(?i)^\\s*permissions\\s*:\\s*write-all\\s*(?:#.*)?$"
            ),
            regexRule(
                    "BUILTIN_WORKFLOW_FLOATING_REF", "HIGH", "WORKFLOW",
                    "发现工作流引用浮动版本",
                    "GitHub Actions 引用了 main、master 或 latest，远端内容变化后会直接影响工作流。",
                    "请固定到可信的提交哈希，并通过受控流程更新版本。",
                    WORKFLOW_FILE,
                    "(?i)^\\s*-?\\s*uses\\s*:\\s*[^\\s#]+@(?:main|master|latest)\\s*(?:#.*)?$"
            )
    );

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
                ? "内置规则未发现问题。"
                : "内置规则发现 " + findings.size() + " 个问题。";
        return new RuleEngineResult(findings, summary);
    }

    static int supportedRuleCount() {
        return RULES.size();
    }

    private static void inspectPatch(PrChangedFile changedFile, List<ReviewFinding> findings) {
        String patch = changedFile.getPatch();
        if (patch == null || patch.isBlank()) {
            return;
        }

        for (PatchLine patchLine : addedPatchLines(patch)) {
            inspectLine(changedFile.getFilename(), patchLine.lineNumber(), patchLine.content(), false, findings);
        }
    }

    private static void inspectSnippets(ReviewTargetFile file, List<ReviewFinding> findings) {
        for (PrReviewFileContext.Snippet snippet : file.getSnippets()) {
            String[] lines = snippet.getContent().split("\\R", -1);
            for (int index = 0; index < lines.length; index++) {
                inspectLine(
                        file.getChangedFile().getFilename(),
                        snippet.getStartLine() + index,
                        lines[index],
                        true,
                        findings
                );
            }
        }
    }

    private static void inspectLine(
            String filename,
            int lineNumber,
            String content,
            boolean snippet,
            List<ReviewFinding> findings
    ) {
        for (RuleDefinition rule : RULES) {
            if (snippet && !rule.inspectSnippets()) {
                continue;
            }
            if (!rule.matches(filename, content) || hasEquivalentFinding(findings, rule.id(), filename, lineNumber)) {
                continue;
            }
            findings.add(rule.toFinding(filename, lineNumber));
        }
    }

    /**
     * 从 unified diff 中解析新增行和对应的新文件行号。
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
            if (currentNewLine < 0 || line.startsWith("+++") || line.startsWith("---")) {
                continue;
            }
            if (line.startsWith("+")) {
                addedLines.add(new PatchLine(currentNewLine, line.substring(1)));
                currentNewLine++;
            } else if (!line.startsWith("-") && !line.startsWith("\\")) {
                currentNewLine++;
            }
        }

        return addedLines;
    }

    private static boolean hasEquivalentFinding(
            List<ReviewFinding> findings,
            String ruleId,
            String filename,
            int lineNumber
    ) {
        return findings.stream().anyMatch(finding ->
                ruleId.equals(finding.getRuleId())
                        && filename.equals(finding.getFilePath())
                        && Integer.valueOf(lineNumber).equals(finding.getLine()));
    }

    private static RuleDefinition regexRule(
            String id,
            String severity,
            String category,
            String title,
            String message,
            String suggestion,
            Predicate<String> filenameMatcher,
            String regex
    ) {
        Pattern pattern = Pattern.compile(regex);
        return rule(
                id,
                severity,
                category,
                false,
                title,
                message,
                suggestion,
                filenameMatcher,
                line -> isReviewableCodeLine(line) && pattern.matcher(line).find()
        );
    }

    private static RuleDefinition rawRegexRule(
            String id,
            String severity,
            String category,
            String title,
            String message,
            String suggestion,
            Predicate<String> filenameMatcher,
            String regex
    ) {
        Pattern pattern = Pattern.compile(regex);
        return rule(
                id,
                severity,
                category,
                false,
                title,
                message,
                suggestion,
                filenameMatcher,
                line -> pattern.matcher(line).find()
        );
    }

    private static RuleDefinition rule(
            String id,
            String severity,
            String category,
            boolean inspectSnippets,
            String title,
            String message,
            String suggestion,
            Predicate<String> filenameMatcher,
            Predicate<String> contentMatcher
    ) {
        return new RuleDefinition(
                id,
                severity,
                category,
                title,
                message,
                suggestion,
                inspectSnippets,
                filenameMatcher,
                contentMatcher
        );
    }

    private static boolean isReviewableCodeLine(String line) {
        String trimmed = line.trim();
        return !trimmed.isEmpty()
                && !trimmed.startsWith("//")
                && !trimmed.startsWith("/*")
                && !trimmed.startsWith("*");
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private record RuleDefinition(
            String id,
            String severity,
            String category,
            String title,
            String message,
            String suggestion,
            boolean inspectSnippets,
            Predicate<String> filenameMatcher,
            Predicate<String> contentMatcher
    ) {

        private boolean matches(String filename, String content) {
            return filenameMatcher.test(filename) && contentMatcher.test(content);
        }

        private ReviewFinding toFinding(String filename, int lineNumber) {
            return new ReviewFinding(
                    SOURCE,
                    severity,
                    filename,
                    lineNumber,
                    lineNumber,
                    RIGHT_SIDE,
                    lineNumber,
                    RIGHT_SIDE,
                    title,
                    message,
                    suggestion,
                    id,
                    "HIGH",
                    category
            );
        }
    }

    private record PatchLine(int lineNumber, String content) {
    }
}
