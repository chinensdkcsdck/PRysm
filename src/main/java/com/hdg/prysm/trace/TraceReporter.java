package com.hdg.prysm.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Emits structured JSON trace logs.
 */
@Component
public class TraceReporter {

    private static final Logger log = LoggerFactory.getLogger(TraceReporter.class);
    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    public TraceReporter(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("Object mapper must not be null");
        }
        this.objectMapper = objectMapper;
    }

    public void report(TraceContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Trace context must not be null");
        }

        log.info("::group::PRysm Trace Summary");
        for (TraceSpan span : context.getSpans()) {
            log.info(toJson(spanJson(context, span)));
        }
        log.info(toJson(summaryJson(new TraceSummary(context))));
        log.info("::endgroup::");
    }

    String toJson(TraceSpan span, TraceContext context) {
        return toJson(spanJson(context, span));
    }

    String toJson(TraceSummary summary) {
        return toJson(summaryJson(summary));
    }

    private ObjectNode spanJson(TraceContext context, TraceSpan span) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("type", "span");
        root.put("traceId", context.getTraceId());
        root.put("spanName", span.getName());
        root.put("status", span.getStatus() == null ? TraceStatus.FAILED.name() : span.getStatus().name());
        root.put("durationMs", span.getDurationMs());
        root.set("attributes", objectMapper.valueToTree(span.getAttributes()));
        if (span.getErrorType() != null) {
            root.put("errorType", span.getErrorType());
        }
        if (span.getErrorMessage() != null) {
            root.put("errorMessage", span.getErrorMessage());
        }
        return root;
    }

    private ObjectNode summaryJson(TraceSummary summary) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("type", "summary");
        root.put("traceId", summary.getTraceId());
        root.put("status", summary.getStatus().name());
        root.put("totalDurationMs", summary.getTotalDurationMs());
        root.put("totalSpans", summary.getTotalSpans());
        putNullable(root, "failedStep", summary.getFailedStep());
        putNullable(root, "totalFindings", summary.getTotalFindings());
        putNullable(root, "ruleFindings", summary.getRuleFindings());
        putNullable(root, "llmFindings", summary.getLlmFindings());
        putNullable(root, "duplicatesRemoved", summary.getDuplicatesRemoved());
        putNullable(root, "promptCharacters", summary.getPromptCharacters());
        putNullable(root, "estimatedPromptTokens", summary.getEstimatedPromptTokens());
        putNullable(root, "promptTokens", summary.getPromptTokens());
        putNullable(root, "completionTokens", summary.getCompletionTokens());
        putNullable(root, "totalTokens", summary.getTotalTokens());
        putNullable(root, "tokenSource", summary.getTokenSource());
        putNullable(root, "commentWritten", summary.getCommentWritten());
        putNullable(root, "optimizationGroup", summary.getOptimizationGroup());
        putNullable(root, "modelName", summary.getModelName());
        putNullable(root, "effectiveModel", summary.getEffectiveModel());
        return root;
    }

    private String toJson(ObjectNode node) {
        return objectMapper.writeValueAsString(node);
    }

    private static void putNullable(ObjectNode node, String fieldName, String value) {
        if (value == null) {
            node.putNull(fieldName);
            return;
        }
        node.put(fieldName, value);
    }

    private static void putNullable(ObjectNode node, String fieldName, Integer value) {
        if (value == null) {
            node.putNull(fieldName);
            return;
        }
        node.put(fieldName, value);
    }

    private static void putNullable(ObjectNode node, String fieldName, Boolean value) {
        if (value == null) {
            node.putNull(fieldName);
            return;
        }
        node.put(fieldName, value);
    }
}
