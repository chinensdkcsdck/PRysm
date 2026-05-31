package com.hdg.prysm.assembly;

import com.hdg.prysm.budget.ReviewContextBudgetFile;
import com.hdg.prysm.budget.ReviewContextBudgetResult;
import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.diff.PrDiff;
import com.hdg.prysm.execution.ContextStatus;
import com.hdg.prysm.execution.ContextStatusCode;
import com.hdg.prysm.execution.PromptPayload;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewTargetFile;
import com.hdg.prysm.optimization.LlmOptimizationContext;
import com.hdg.prysm.optimization.LlmOptimizationProperties;
import com.hdg.prysm.review.PrReviewFileContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;

/**
 * PR8 的 Review 执行输入组装服务。
 *
 * 该类只消费 PR7 的预算结果，负责生成稳定的 ReviewExecutionInput 和 LLM prompt 载荷。
 */
@Component
public class ReviewExecutionInputAssembler {

    private static final String SYSTEM_PROMPT = """
            你是 Prysm，一个自动化 Pull Request 代码审查助手。
            你只能基于输入中提供的 patch、代码片段和元数据进行审查。
            不要编造输入中不存在的文件、行号、API、依赖或运行时行为。
            优先关注正确性、安全性、可维护性、可靠性和测试影响。
            summary 必须概括本次 PR 的主要变更，作为最终评论中的“变更总结”。
            title、message、suggestion 必须分别说明风险标题、风险原因和可执行 Review 建议。
            除文件路径、代码标识符、API 名称、错误码和枚举值外，summary、title、message、suggestion 等面向用户的文本必须使用简体中文。
            只返回符合输出 schema 的 JSON，不要输出 Markdown 或额外解释。
            """;

    private static final String OUTPUT_SCHEMA = """
            {
              "summary": "使用简体中文概括本次 PR 的主要变更，作为最终评论中的变更总结。",
              "findings": [
                {
                  "source": "llm",
                  "severity": "error|warning|info",
                  "filePath": "问题所在文件路径",
                  "startLine": 1,
                  "endLine": 1,
                  "side": "RIGHT",
                  "line": 1,
                  "startSide": "RIGHT",
                  "title": "使用简体中文描述风险标题",
                  "message": "使用简体中文说明风险原因。",
                  "suggestion": "使用简体中文给出具体、可执行的 Review 建议。",
                  "ruleId": "LLM_RULE_ID",
                  "confidence": "HIGH|MEDIUM|LOW",
                  "category": "bug|security|secret|workflow|config|test|maintainability|documentation"
                }
              ]
            }
            """;

    private static final String COMPACT_OUTPUT_SCHEMA = """
            {"summary":"简体中文 PR 变更总结","findings":[{"severity":"error|warning|info","filePath":"string","startLine":1,"endLine":1,"line":1,"title":"简体中文风险标题","message":"简体中文风险原因","suggestion":"简体中文可执行 Review 建议","ruleId":"LLM_RULE_ID","confidence":"HIGH|MEDIUM|LOW","category":"bug|security|secret|workflow|config|test|maintainability|documentation"}]}
            """;

    private final LlmOptimizationProperties optimizationProperties;
    private final LlmOptimizationContext optimizationContext;

    @Autowired
    public ReviewExecutionInputAssembler(
            LlmOptimizationProperties optimizationProperties,
            LlmOptimizationContext optimizationContext
    ) {
        if (optimizationProperties == null) {
            throw new IllegalArgumentException("Optimization properties must not be null");
        }
        if (optimizationContext == null) {
            throw new IllegalArgumentException("Optimization context must not be null");
        }
        this.optimizationProperties = optimizationProperties;
        this.optimizationContext = optimizationContext;
    }

    /**
     * 将 PR7 的预算结果组装成 B 线可直接消费的 ReviewExecutionInput。
     */
    public ReviewExecutionInput assemble(ReviewContextBudgetResult budgetResult) {
        if (budgetResult == null) {
            throw new IllegalArgumentException("Review context budget result must not be null");
        }

        PrDiff diff = budgetResult.getSelectionResult().getReviewContext().getDiff();
        PrContext prContext = diff.getContext();
        List<ReviewTargetFile> targetFiles = budgetResult.getTargetFiles();
        String fullPrompt = userPrompt(budgetResult, diff, targetFiles);
        String effectivePrompt = optimizationProperties.isCompactPromptEnabled()
                ? compactUserPrompt(budgetResult, diff, targetFiles)
                : fullPrompt;
        optimizationContext.recordPromptCharacters(fullPrompt.length(), effectivePrompt.length());
        return new ReviewExecutionInput(
                prContext,
                diff,
                targetFiles,
                contextStatus(budgetResult),
                new PromptPayload(
                        SYSTEM_PROMPT,
                        effectivePrompt,
                        optimizationProperties.isCompactPromptEnabled() ? COMPACT_OUTPUT_SCHEMA : OUTPUT_SCHEMA
                )
        );
    }

    /**
     * 根据 PR7 的预算结果给出 PR8 的基础上下文状态。
     */
    private ContextStatus contextStatus(ReviewContextBudgetResult budgetResult) {
        if (budgetResult.getSelectedFiles().isEmpty()) {
            return new ContextStatus(ContextStatusCode.SKIPPED, "没有文件进入 Review 执行输入");
        }
        if (budgetResult.isTruncated()) {
            return new ContextStatus(ContextStatusCode.LIMITED, "Review 执行输入已被上下文预算裁剪");
        }
        return new ContextStatus(ContextStatusCode.FULL, "Review 执行输入已组装完成");
    }

    /**
     * 组装本次 Pull Request 的 user prompt。
     */
    private String userPrompt(
            ReviewContextBudgetResult budgetResult,
            PrDiff diff,
            List<ReviewTargetFile> targetFiles
    ) {
        StringBuilder prompt = new StringBuilder();
        appendPullRequestMetadata(prompt, diff);
        appendBudgetSummary(prompt, budgetResult);
        appendReviewFiles(prompt, budgetResult, targetFiles);
        appendSkippedFiles(prompt, budgetResult);
        return prompt.toString();
    }

    /**
     * 组装压缩版 user prompt，用于灰度对比 prompt 规模对 LLM 耗时的影响。
     */
    private String compactUserPrompt(
            ReviewContextBudgetResult budgetResult,
            PrDiff diff,
            List<ReviewTargetFile> targetFiles
    ) {
        StringBuilder prompt = new StringBuilder();
        PrContext context = diff.getContext();
        prompt.append("PR ")
                .append(context.getOwner())
                .append('/')
                .append(context.getRepository())
                .append('#')
                .append(context.getPullRequestNumber())
                .append(": files=")
                .append(diff.getFileCount())
                .append(", +")
                .append(diff.getTotalAdditions())
                .append(", -")
                .append(diff.getTotalDeletions())
                .append('\n');
        prompt.append("Budget: used=")
                .append(budgetResult.getUsedCharacters())
                .append(", remaining=")
                .append(budgetResult.getRemainingCharacters())
                .append(", truncated=")
                .append(budgetResult.isTruncated())
                .append("\n\n");
        prompt.append("Review files\n");
        for (int index = 0; index < targetFiles.size(); index++) {
            appendCompactReviewFile(prompt, targetFiles.get(index), index + 1);
        }
        if (!budgetResult.getSkippedFiles().isEmpty()) {
            prompt.append("Skipped files: ")
                    .append(budgetResult.getSkippedFiles().size())
                    .append('\n');
        }
        return prompt.toString();
    }

    /**
     * 写入压缩版单文件上下文，优先保留 patch，缺少 patch 时才保留 snippet。
     */
    private void appendCompactReviewFile(StringBuilder prompt, ReviewTargetFile targetFile, int fileIndex) {
        PrChangedFile changedFile = targetFile.getChangedFile();
        prompt.append("File ")
                .append(fileIndex)
                .append(": ")
                .append(changedFile.getFilename())
                .append(" status=")
                .append(changedFile.getStatus())
                .append(" +")
                .append(changedFile.getAdditions())
                .append(" -")
                .append(changedFile.getDeletions())
                .append(" priority=")
                .append(targetFile.getPriority())
                .append('\n');
        if (changedFile.getPatch() != null && !changedFile.getPatch().isBlank()) {
            prompt.append(fencedBlock("diff", changedFile.getPatch())).append('\n');
            return;
        }
        if (!targetFile.getSnippets().isEmpty()) {
            PrReviewFileContext.Snippet snippet = targetFile.getSnippets().get(0);
            prompt.append("Snippet ")
                    .append(snippet.getStartLine())
                    .append('-')
                    .append(snippet.getEndLine())
                    .append('\n');
            prompt.append(fencedBlock("text", snippet.getContent())).append('\n');
        }
    }

    /**
     * 写入 Pull Request 级别的基础元数据。
     */
    private void appendPullRequestMetadata(StringBuilder prompt, PrDiff diff) {
        PrContext context = diff.getContext();
        prompt.append("Pull Request 基础信息\n");
        prompt.append("- 所有者: ").append(context.getOwner()).append('\n');
        prompt.append("- 仓库: ").append(context.getRepository()).append('\n');
        prompt.append("- PR 编号: ").append(context.getPullRequestNumber()).append('\n');
        prompt.append("- 变更文件数: ").append(diff.getFileCount()).append('\n');
        prompt.append("- 新增行数: ").append(diff.getTotalAdditions()).append('\n');
        prompt.append("- 删除行数: ").append(diff.getTotalDeletions()).append("\n\n");
    }

    /**
     * 写入 PR7 预算使用情况。
     */
    private void appendBudgetSummary(StringBuilder prompt, ReviewContextBudgetResult budgetResult) {
        prompt.append("上下文预算\n");
        prompt.append("- 最大总字符数: ").append(budgetResult.getMaxTotalCharacters()).append('\n');
        prompt.append("- 已使用字符数: ").append(budgetResult.getUsedCharacters()).append('\n');
        prompt.append("- 剩余字符数: ").append(budgetResult.getRemainingCharacters()).append('\n');
        prompt.append("- 是否裁剪: ").append(budgetResult.isTruncated()).append("\n\n");
    }

    /**
     * 写入预算后真正进入 Review 的文件内容。
     */
    private void appendReviewFiles(
            StringBuilder prompt,
            ReviewContextBudgetResult budgetResult,
            List<ReviewTargetFile> targetFiles
    ) {
        prompt.append("待审查文件\n");
        if (targetFiles.isEmpty()) {
            prompt.append("经过过滤和预算分配后，没有文件进入本次 Review 执行输入。\n\n");
            return;
        }

        for (int index = 0; index < targetFiles.size(); index++) {
            ReviewTargetFile targetFile = targetFiles.get(index);
            ReviewContextBudgetFile budgetFile = budgetResult.getSelectedFiles().get(index);
            appendReviewFile(prompt, targetFile, budgetFile, index + 1);
        }
    }

    /**
     * 写入单个文件的元数据、patch 和 snippet。
     */
    private void appendReviewFile(
            StringBuilder prompt,
            ReviewTargetFile targetFile,
            ReviewContextBudgetFile budgetFile,
            int fileIndex
    ) {
        PrChangedFile changedFile = targetFile.getChangedFile();
        prompt.append("文件 ").append(fileIndex).append('\n');
        prompt.append("- 路径: ").append(changedFile.getFilename()).append('\n');
        prompt.append("- 状态: ").append(changedFile.getStatus()).append('\n');
        prompt.append("- 新增行数: ").append(changedFile.getAdditions()).append('\n');
        prompt.append("- 删除行数: ").append(changedFile.getDeletions()).append('\n');
        prompt.append("- 优先级: ").append(targetFile.getPriority()).append('\n');
        prompt.append("- 是否选中: ").append(targetFile.isSelected()).append('\n');
        appendOptionalLine(prompt, "说明", targetFile.getNote());
        prompt.append("- 原始所需字符数: ").append(budgetFile.getRequestedCharacters()).append('\n');
        prompt.append("- 实际分配字符数: ").append(budgetFile.getAllocatedCharacters()).append('\n');
        prompt.append("- 是否被裁剪: ").append(budgetFile.isTruncated()).append('\n');
        appendOptionalLine(prompt, "预算原因", budgetFile.getReason());
        appendPatch(prompt, changedFile.getPatch());
        appendSnippets(prompt, targetFile.getSnippets());
        prompt.append('\n');
    }

    /**
     * 写入可选的单行元数据。
     */
    private void appendOptionalLine(StringBuilder prompt, String name, String value) {
        if (value != null && !value.isBlank()) {
            prompt.append("- ").append(name).append(": ").append(value).append('\n');
        }
    }

    /**
     * 写入预算后的 patch 文本。
     */
    private void appendPatch(StringBuilder prompt, String patch) {
        prompt.append("Patch 内容\n");
        prompt.append(fencedBlock("diff", patch == null || patch.isBlank() ? "patch 不可用" : patch));
        prompt.append('\n');
    }

    /**
     * 写入预算后的 snippet 文本。
     */
    private void appendSnippets(StringBuilder prompt, List<PrReviewFileContext.Snippet> snippets) {
        prompt.append("邻近代码片段\n");
        if (snippets.isEmpty()) {
            prompt.append("没有邻近代码片段\n");
            return;
        }

        for (int index = 0; index < snippets.size(); index++) {
            PrReviewFileContext.Snippet snippet = snippets.get(index);
            prompt.append("片段 ").append(index + 1)
                    .append(" 行号 ")
                    .append(snippet.getStartLine())
                    .append('-')
                    .append(snippet.getEndLine())
                    .append('\n');
            prompt.append(fencedBlock("text", snippet.getContent()));
            prompt.append('\n');
        }
    }

    /**
     * 写入因预算限制未进入 Review 的文件摘要。
     */
    private void appendSkippedFiles(StringBuilder prompt, ReviewContextBudgetResult budgetResult) {
        List<ReviewContextBudgetFile> skippedFiles = budgetResult.getSkippedFiles();
        if (skippedFiles.isEmpty()) {
            return;
        }

        prompt.append("未进入 Review 的文件\n");
        StringJoiner skippedFileLines = new StringJoiner("\n");
        for (ReviewContextBudgetFile skippedFile : skippedFiles) {
            PrChangedFile changedFile = skippedFile.getTargetFile().getChangedFile();
            skippedFileLines.add("- " + changedFile.getFilename() + ": " + skippedFile.getReason());
        }
        prompt.append(skippedFileLines).append('\n');
    }

    /**
     * 创建 Markdown 代码块，并规避内容中的三反引号破坏 prompt 结构。
     */
    private String fencedBlock(String language, String content) {
        return "```" + language + "\n" + safeFenceContent(content) + "\n```";
    }

    /**
     * 清理代码块内容中的三反引号。
     */
    private String safeFenceContent(String content) {
        if (content == null || content.isBlank()) {
            return "空内容";
        }
        return content.replace("```", "'''");
    }
}
