package com.hdg.prysm.comment;

import com.hdg.prysm.execution.ReviewFinding;
import com.hdg.prysm.result.ReviewAggregationResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders aggregated findings as one pull request comment.
 */
@Component
public class ReviewCommentRenderer {

    public String render(ReviewAggregationResult result) {
        return render(result, "## PRysm Review Result", null);
    }

    public String renderFastReview(ReviewAggregationResult result) {
        return render(
                result,
                "## PRysm Fast Review Result",
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
        markdown.append("Found ")
                .append(result.getFindings().size())
                .append(" issue(s).");
        markdown.append(" Rule findings: ")
                .append(result.getRuleFindingCount())
                .append(", LLM findings: ")
                .append(result.getLlmFindingCount())
                .append(", duplicates removed: ")
                .append(result.getDuplicateCount())
                .append(".\n\n");

        appendSummary(markdown, "Rule summary", result.getRuleSummary());
        appendSummary(markdown, "LLM summary", result.getLlmSummary());

        if (!result.hasFindings()) {
            markdown.append("No actionable findings were reported.\n");
            return markdown.toString();
        }

        for (Map.Entry<String, List<ReviewFinding>> entry : groupByFile(result.getFindings()).entrySet()) {
            markdown.append("### ")
                    .append(escapeMarkdownText(displayFilePath(entry.getKey())))
                    .append("\n\n");
            for (ReviewFinding finding : entry.getValue()) {
                appendFinding(markdown, finding);
            }
        }

        return markdown.toString();
    }

    private static void appendSummary(StringBuilder markdown, String label, String summary) {
        if (summary == null || summary.isBlank()) {
            return;
        }
        markdown.append("**")
                .append(label)
                .append(":** ")
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
        markdown.append("  - Source: `")
                .append(escapeCodeText(finding.getSource()))
                .append("`");
        if (finding.getRuleId() != null && !finding.getRuleId().isBlank()) {
            markdown.append(" / Rule: `")
                    .append(escapeCodeText(finding.getRuleId()))
                    .append("`");
        }
        markdown.append("\n");
        markdown.append("  - Detail: ")
                .append(escapeMarkdownText(finding.getMessage()))
                .append("\n");
        if (finding.getSuggestion() != null && !finding.getSuggestion().isBlank()) {
            markdown.append("  - Suggestion: ")
                    .append(escapeMarkdownText(finding.getSuggestion()))
                    .append("\n");
        }
        markdown.append("\n");
    }

    private static String location(ReviewFinding finding) {
        if (finding.getLine() != null) {
            return "line " + finding.getLine();
        }
        if (finding.getStartLine() != null && finding.getEndLine() != null) {
            if (finding.getStartLine().equals(finding.getEndLine())) {
                return "line " + finding.getStartLine();
            }
            return "lines " + finding.getStartLine() + "-" + finding.getEndLine();
        }
        if (finding.getStartLine() != null) {
            return "line " + finding.getStartLine();
        }
        return "";
    }

    private static String displayFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "General";
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
