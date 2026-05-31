package com.hdg.prysm.execution;

/**
 * 规则引擎和 LLM Review 共用的统一问题结构。
 */
public class ReviewFinding {

    private final String source;
    private final String severity;
    private final String filePath;
    private final Integer startLine;
    private final Integer endLine;
    private final String side;
    private final Integer line;
    private final String startSide;
    private final String title;
    private final String message;
    private final String suggestion;
    private final String ruleId;
    private final String confidence;
    private final String category;

    public ReviewFinding(
            String source,
            String severity,
            String filePath,
            Integer startLine,
            Integer endLine,
            String side,
            Integer line,
            String startSide,
            String title,
            String message,
            String suggestion,
            String ruleId
    ) {
        this(
                source,
                severity,
                filePath,
                startLine,
                endLine,
                side,
                line,
                startSide,
                title,
                message,
                suggestion,
                ruleId,
                null,
                null
        );
    }

    public ReviewFinding(
            String source,
            String severity,
            String filePath,
            Integer startLine,
            Integer endLine,
            String side,
            Integer line,
            String startSide,
            String title,
            String message,
            String suggestion,
            String ruleId,
            String confidence,
            String category
    ) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Finding source must not be blank");
        }
        if (severity == null || severity.isBlank()) {
            throw new IllegalArgumentException("Finding severity must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Finding title must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Finding message must not be blank");
        }
        validateLine("startLine", startLine);
        validateLine("endLine", endLine);
        validateLine("line", line);
        if (startLine != null && endLine != null && endLine < startLine) {
            throw new IllegalArgumentException("Finding end line must be greater than or equal to start line");
        }

        this.source = source;
        this.severity = severity;
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
        this.side = side;
        this.line = line;
        this.startSide = startSide;
        this.title = title;
        this.message = message;
        this.suggestion = suggestion;
        this.ruleId = ruleId;
        this.confidence = normalizeOptional(confidence);
        this.category = normalizeOptional(category);
    }

    public String getSource() {
        return source;
    }

    public String getSeverity() {
        return severity;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getStartLine() {
        return startLine;
    }

    public Integer getEndLine() {
        return endLine;
    }

    public String getSide() {
        return side;
    }

    public Integer getLine() {
        return line;
    }

    public String getStartSide() {
        return startSide;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getConfidence() {
        return confidence;
    }

    public String getCategory() {
        return category;
    }

    private static void validateLine(String fieldName, Integer line) {
        if (line != null && line <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive when present");
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
