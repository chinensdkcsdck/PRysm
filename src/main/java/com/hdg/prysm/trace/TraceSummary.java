package com.hdg.prysm.trace;

import java.util.List;

/**
 * Summary for one completed or failed trace.
 */
public class TraceSummary {

    private final String traceId;
    private final TraceStatus status;
    private final long totalDurationMs;
    private final int totalSpans;
    private final String failedStep;
    private final Integer totalFindings;
    private final Integer ruleFindings;
    private final Integer llmFindings;
    private final Integer duplicatesRemoved;
    private final Integer promptCharacters;
    private final Integer estimatedPromptTokens;
    private final Integer promptTokens;
    private final Integer completionTokens;
    private final Integer totalTokens;
    private final String tokenSource;
    private final Boolean commentWritten;
    private final String optimizationGroup;
    private final String modelName;
    private final String effectiveModel;

    public TraceSummary(TraceContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Trace context must not be null");
        }

        this.traceId = context.getTraceId();
        this.status = summaryStatus(context.getSpans());
        this.totalDurationMs = context.getTotalDurationMs();
        this.totalSpans = context.getSpans().size();
        this.failedStep = firstSpanWithStatus(context.getSpans(), TraceStatus.FAILED);
        this.totalFindings = readLastInt(context, "result_aggregate", "findings");
        this.ruleFindings = readLastInt(context, "result_aggregate", "ruleFindings");
        this.llmFindings = readLastInt(context, "result_aggregate", "llmFindings");
        this.duplicatesRemoved = readLastInt(context, "result_aggregate", "duplicatesRemoved");
        this.promptCharacters = readLastLlmInt(context, "promptCharacters");
        this.estimatedPromptTokens = readLastLlmInt(context, "estimatedPromptTokens");
        this.promptTokens = readLastLlmInt(context, "promptTokens");
        this.completionTokens = readLastLlmInt(context, "completionTokens");
        this.totalTokens = readLastLlmInt(context, "totalTokens");
        this.tokenSource = readLastLlmString(context, "tokenSource");
        Boolean commentWrittenValue = readLastBoolean(context, "github_comment", "commentWritten");
        if (commentWrittenValue == null) {
            commentWrittenValue = readLastBoolean(context, "github_comment_update", "commentWritten");
        }
        if (commentWrittenValue == null) {
            commentWrittenValue = readLastBoolean(context, "github_comment_fast", "commentWritten");
        }
        this.commentWritten = commentWrittenValue;
        this.optimizationGroup = readLastLlmString(context, "optimizationGroup");
        this.modelName = readLastLlmString(context, "modelName");
        this.effectiveModel = readLastLlmString(context, "effectiveModel");
    }

    public String getTraceId() {
        return traceId;
    }

    public TraceStatus getStatus() {
        return status;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public int getTotalSpans() {
        return totalSpans;
    }

    public String getFailedStep() {
        return failedStep;
    }

    public Integer getTotalFindings() {
        return totalFindings;
    }

    public Integer getRuleFindings() {
        return ruleFindings;
    }

    public Integer getLlmFindings() {
        return llmFindings;
    }

    public Integer getDuplicatesRemoved() {
        return duplicatesRemoved;
    }

    public Integer getPromptCharacters() {
        return promptCharacters;
    }

    public Integer getEstimatedPromptTokens() {
        return estimatedPromptTokens;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public String getTokenSource() {
        return tokenSource;
    }

    public Boolean getCommentWritten() {
        return commentWritten;
    }

    public String getOptimizationGroup() {
        return optimizationGroup;
    }

    public String getModelName() {
        return modelName;
    }

    public String getEffectiveModel() {
        return effectiveModel;
    }

    private static TraceStatus summaryStatus(List<TraceSpan> spans) {
        if (spans.stream().anyMatch(span -> span.getStatus() == TraceStatus.FAILED)) {
            return TraceStatus.FAILED;
        }
        if (spans.stream().anyMatch(span -> span.getStatus() == TraceStatus.DEGRADED)) {
            return TraceStatus.DEGRADED;
        }
        return TraceStatus.SUCCESS;
    }

    private static String firstSpanWithStatus(List<TraceSpan> spans, TraceStatus status) {
        return spans.stream()
                .filter(span -> span.getStatus() == status)
                .map(TraceSpan::getName)
                .findFirst()
                .orElse(null);
    }

    private static Integer readLastInt(TraceContext context, String spanName, String key) {
        Object value = readLastAttribute(context, spanName, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static Integer readLastLlmInt(TraceContext context, String key) {
        Integer value = readLastInt(context, "llm_review_deep", key);
        if (value != null) {
            return value;
        }
        return readLastInt(context, "llm_review_fast", key);
    }

    private static String readLastString(TraceContext context, String spanName, String key) {
        Object value = readLastAttribute(context, spanName, key);
        return value == null ? null : String.valueOf(value);
    }

    private static String readLastLlmString(TraceContext context, String key) {
        String value = readLastString(context, "llm_review_deep", key);
        if (value != null) {
            return value;
        }
        return readLastString(context, "llm_review_fast", key);
    }

    private static Boolean readLastBoolean(TraceContext context, String spanName, String key) {
        Object value = readLastAttribute(context, spanName, key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return null;
    }

    private static Object readLastAttribute(TraceContext context, String spanName, String key) {
        List<TraceSpan> spans = context.getSpans();
        for (int index = spans.size() - 1; index >= 0; index--) {
            TraceSpan span = spans.get(index);
            if (spanName.equals(span.getName()) && span.getAttributes().containsKey(key)) {
                return span.getAttributes().get(key);
            }
        }
        return null;
    }
}
