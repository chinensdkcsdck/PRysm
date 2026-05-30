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
import com.hdg.prysm.review.PrReviewFileContext;
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
            You are Prysm, an automated pull request code reviewer.
            Review only the provided patch, snippets, and metadata.
            Do not invent files, line numbers, APIs, dependencies, or runtime behavior that are not present in the input.
            Focus on correctness, security, maintainability, reliability, and test impact.
            Return only JSON that follows the output schema.
            """;

    private static final String OUTPUT_SCHEMA = """
            {
              "summary": "Short review summary.",
              "findings": [
                {
                  "source": "llm",
                  "severity": "error|warning|info",
                  "filePath": "path/to/file",
                  "startLine": 1,
                  "endLine": 1,
                  "side": "RIGHT",
                  "line": 1,
                  "startSide": "RIGHT",
                  "title": "Issue title",
                  "message": "Why this is a problem.",
                  "suggestion": "Concrete fix direction.",
                  "ruleId": "LLM_RULE_ID"
                }
              ]
            }
            """;

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
        return new ReviewExecutionInput(
                prContext,
                diff,
                targetFiles,
                contextStatus(budgetResult),
                new PromptPayload(SYSTEM_PROMPT, userPrompt(budgetResult, diff, targetFiles), OUTPUT_SCHEMA)
        );
    }

    /**
     * 根据 PR7 的预算结果给出 PR8 的基础上下文状态。
     */
    private ContextStatus contextStatus(ReviewContextBudgetResult budgetResult) {
        if (budgetResult.getSelectedFiles().isEmpty()) {
            return new ContextStatus(ContextStatusCode.SKIPPED, "no files selected for review input");
        }
        if (budgetResult.isTruncated()) {
            return new ContextStatus(ContextStatusCode.LIMITED, "review input was truncated by context budget");
        }
        return new ContextStatus(ContextStatusCode.FULL, "review input assembled");
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
     * 写入 Pull Request 级别的基础元数据。
     */
    private void appendPullRequestMetadata(StringBuilder prompt, PrDiff diff) {
        PrContext context = diff.getContext();
        prompt.append("Pull Request\n");
        prompt.append("- owner: ").append(context.getOwner()).append('\n');
        prompt.append("- repository: ").append(context.getRepository()).append('\n');
        prompt.append("- number: ").append(context.getPullRequestNumber()).append('\n');
        prompt.append("- changedFiles: ").append(diff.getFileCount()).append('\n');
        prompt.append("- totalAdditions: ").append(diff.getTotalAdditions()).append('\n');
        prompt.append("- totalDeletions: ").append(diff.getTotalDeletions()).append("\n\n");
    }

    /**
     * 写入 PR7 预算使用情况。
     */
    private void appendBudgetSummary(StringBuilder prompt, ReviewContextBudgetResult budgetResult) {
        prompt.append("Context Budget\n");
        prompt.append("- maxTotalCharacters: ").append(budgetResult.getMaxTotalCharacters()).append('\n');
        prompt.append("- usedCharacters: ").append(budgetResult.getUsedCharacters()).append('\n');
        prompt.append("- remainingCharacters: ").append(budgetResult.getRemainingCharacters()).append('\n');
        prompt.append("- truncated: ").append(budgetResult.isTruncated()).append("\n\n");
    }

    /**
     * 写入预算后真正进入 Review 的文件内容。
     */
    private void appendReviewFiles(
            StringBuilder prompt,
            ReviewContextBudgetResult budgetResult,
            List<ReviewTargetFile> targetFiles
    ) {
        prompt.append("Review Files\n");
        if (targetFiles.isEmpty()) {
            prompt.append("No files were selected for review after filtering and budget allocation.\n\n");
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
        prompt.append("File ").append(fileIndex).append('\n');
        prompt.append("- path: ").append(changedFile.getFilename()).append('\n');
        prompt.append("- status: ").append(changedFile.getStatus()).append('\n');
        prompt.append("- additions: ").append(changedFile.getAdditions()).append('\n');
        prompt.append("- deletions: ").append(changedFile.getDeletions()).append('\n');
        prompt.append("- priority: ").append(targetFile.getPriority()).append('\n');
        prompt.append("- selected: ").append(targetFile.isSelected()).append('\n');
        appendOptionalLine(prompt, "note", targetFile.getNote());
        prompt.append("- requestedCharacters: ").append(budgetFile.getRequestedCharacters()).append('\n');
        prompt.append("- allocatedCharacters: ").append(budgetFile.getAllocatedCharacters()).append('\n');
        prompt.append("- truncated: ").append(budgetFile.isTruncated()).append('\n');
        appendOptionalLine(prompt, "budgetReason", budgetFile.getReason());
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
        prompt.append("Patch\n");
        prompt.append(fencedBlock("diff", patch == null || patch.isBlank() ? "(patch unavailable)" : patch));
        prompt.append('\n');
    }

    /**
     * 写入预算后的 snippet 文本。
     */
    private void appendSnippets(StringBuilder prompt, List<PrReviewFileContext.Snippet> snippets) {
        prompt.append("Snippets\n");
        if (snippets.isEmpty()) {
            prompt.append("(no snippets)\n");
            return;
        }

        for (int index = 0; index < snippets.size(); index++) {
            PrReviewFileContext.Snippet snippet = snippets.get(index);
            prompt.append("Snippet ").append(index + 1)
                    .append(" lines ")
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

        prompt.append("Skipped Files\n");
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
            return "(empty)";
        }
        return content.replace("```", "'''");
    }
}
