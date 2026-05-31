package com.hdg.prysm.comment;

import com.hdg.prysm.execution.ReviewFinding;
import com.hdg.prysm.result.ReviewAggregationResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将聚合后的 findings 渲染为一条 Pull Request 评论。
 */
@Component
public class ReviewCommentRenderer {

    public String render(ReviewAggregationResult result) {
        return render(result, "## PRysm 审查结果", null);
    }

    public String renderFastReview(ReviewAggregationResult result) {
        return render(
                result,
                "## PRysm 快速审查结果",
                "快速初筛完成。深度审查仍在进行，稍后会更新本评论；以下结果可能被深度审查修正。"
        );
    }

    private String render(ReviewAggregationResult result, String title, String notice) {
        if (result == null) {
            throw new IllegalArgumentException("Review aggregation result must not be null");
        }

        StringBuilder markdown = new StringBuilder();
        markdown.append(title).append("\n\n");
        if (notice != null && !notice.isBlank()) {
            markdown.append("> ")
                    .append(escapeMarkdownText(notice))
                    .append("\n\n");
        }
        appendOverview(markdown, result);
        appendSummary(markdown, "变更总结", result.getLlmSummary());
        appendSummary(markdown, "规则摘要", result.getRuleSummary());

        if (!result.hasFindings()) {
            markdown.append("### 风险代码\n\n");
            markdown.append("未发现需要处理的明确风险。\n\n");
            markdown.append("### Review 建议\n\n");
            markdown.append("当前没有需要立即处理的修改建议，建议结合业务场景继续进行人工确认。\n");
            return markdown.toString();
        }

        markdown.append("### 风险代码\n\n");
        for (Map.Entry<String, List<ReviewFinding>> entry : groupByFile(result.getFindings()).entrySet()) {
            markdown.append("#### ")
                    .append(escapeMarkdownText(displayFilePath(entry.getKey())))
                    .append("\n\n");
            for (ReviewFinding finding : entry.getValue()) {
                appendFinding(markdown, finding);
            }
        }
        appendReviewSuggestions(markdown, result.getFindings());

        return markdown.toString();
    }

    private static void appendOverview(StringBuilder markdown, ReviewAggregationResult result) {
        markdown.append("### 审查概览\n\n");
        markdown.append("- 发现问题: ")
                .append(result.getFindings().size())
                .append('\n');
        markdown.append("- 规则结果: ")
                .append(result.getRuleFindingCount())
                .append('\n');
        markdown.append("- 模型结果: ")
                .append(result.getLlmFindingCount())
                .append('\n');
        markdown.append("- 去重数量: ")
                .append(result.getDuplicateCount())
                .append("\n\n");
    }

    private static void appendSummary(StringBuilder markdown, String label, String summary) {
        if (summary == null || summary.isBlank()) {
            return;
        }
        markdown.append("### ")
                .append(label)
                .append("\n\n")
                .append(escapeMarkdownText(summary.trim()))
                .append("\n\n");
    }

    private static Map<String, List<ReviewFinding>> groupByFile(List<ReviewFinding> findings) {
        Map<String, List<ReviewFinding>> grouped = new LinkedHashMap<>();
        for (ReviewFinding finding : findings) {
            grouped.computeIfAbsent(displayFilePath(finding.getFilePath()), ignored -> new java.util.ArrayList<>())
                    .add(finding);
        }
        return grouped;
    }

    private static void appendFinding(StringBuilder markdown, ReviewFinding finding) {
        markdown.append("- **[")
                .append(escapeMarkdownText(finding.getSeverity()))
                .append("] ")
                .append(escapeMarkdownText(finding.getTitle()))
                .append("**");
        String location = location(finding);
        if (!location.isBlank()) {
            markdown.append(" (").append(location).append(")");
        }
        markdown.append("\n");
        markdown.append("  - 来源: `")
                .append(escapeCodeText(finding.getSource()))
                .append("`");
        if (finding.getRuleId() != null && !finding.getRuleId().isBlank()) {
            markdown.append(" / 规则: `")
                    .append(escapeCodeText(finding.getRuleId()))
                    .append("`");
        }
        markdown.append("\n");
        markdown.append("  - 说明: ")
                .append(escapeMarkdownText(finding.getMessage()))
                .append("\n");
        if (finding.getSuggestion() != null && !finding.getSuggestion().isBlank()) {
            markdown.append("  - 建议: ")
                    .append(escapeMarkdownText(finding.getSuggestion()))
                    .append("\n");
        }
        markdown.append("\n");
    }

    private static void appendReviewSuggestions(StringBuilder markdown, List<ReviewFinding> findings) {
        markdown.append("### Review 建议\n\n");
        int index = 1;
        for (ReviewFinding finding : findings) {
            if (finding.getSuggestion() == null || finding.getSuggestion().isBlank()) {
                continue;
            }
            markdown.append(index++)
                    .append(". ");
            String filePath = displayFilePath(finding.getFilePath());
            if (!filePath.isBlank()) {
                markdown.append("`")
                        .append(escapeCodeText(filePath))
                        .append("`: ");
            }
            markdown.append(escapeMarkdownText(finding.getSuggestion()))
                    .append("\n");
        }
        if (index == 1) {
            markdown.append("暂无可执行的修改建议，请结合风险说明进行人工确认。\n");
        }
    }

    private static String location(ReviewFinding finding) {
        if (finding.getLine() != null) {
            return "第 " + finding.getLine() + " 行";
        }
        if (finding.getStartLine() != null && finding.getEndLine() != null) {
            if (finding.getStartLine().equals(finding.getEndLine())) {
                return "第 " + finding.getStartLine() + " 行";
            }
            return "第 " + finding.getStartLine() + "-" + finding.getEndLine() + " 行";
        }
        if (finding.getStartLine() != null) {
            return "第 " + finding.getStartLine() + " 行";
        }
        return "";
    }

    private static String displayFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "通用问题";
        }
        return filePath;
    }

    private static String escapeMarkdownText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeCodeText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("`", "'");
    }
}
