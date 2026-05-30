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
        this.promptCharacters = readLastInt(context, "llm_review", "promptCharacters");
        this.estimatedPromptTokens = readLastInt(context, "llm_review", "estimatedPromptTokens");
        this.tokenSource = readLastString(context, "llm_review", "tokenSource");
        this.commentWritten = readLastBoolean(context, "github_comment", "commentWritten");
        this.optimizationGroup = readLastString(context, "llm_review", "optimizationGroup");
        this.modelName = readLastString(context, "llm_review", "modelName");
        this.effectiveModel = readLastString(context, "llm_review", "effectiveModel");
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

    private static String readLastString(TraceContext context, String spanName, String key) {
        Object value = readLastAttribute(context, spanName, key);
        return value == null ? null : String.valueOf(value);
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
