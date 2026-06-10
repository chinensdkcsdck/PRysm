package com.hdg.prysm.trace;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceSummaryTest {

    @Test
    void shouldSummarizeSuccessfulTrace() {
        TraceContext context = TraceContext.create("trace-1", Instant.now());
        TraceSpan llm = new TraceSpan("llm_review_deep", Instant.now());
        llm.put("promptCharacters", 100);
        llm.put("estimatedPromptTokens", 25);
        llm.put("promptTokens", 30);
        llm.put("completionTokens", 12);
        llm.put("totalTokens", 42);
        llm.put("tokenSource", "provider");
        llm.put("optimizationGroup", "baseline");
        llm.put("modelName", "qwen-plus");
        llm.put("effectiveModel", "qwen-plus");
        llm.finish(TraceStatus.SUCCESS, Instant.now());
        context.addSpan(llm);
        TraceSpan aggregate = new TraceSpan("result_aggregate", Instant.now());
        aggregate.put("findings", 3);
        aggregate.put("ruleFindings", 1);
        aggregate.put("llmFindings", 2);
        aggregate.put("duplicatesRemoved", 0);
        aggregate.finish(TraceStatus.SUCCESS, Instant.now());
        context.addSpan(aggregate);
        TraceSpan comment = new TraceSpan("github_comment", Instant.now());
        comment.put("commentWritten", true);
        comment.finish(TraceStatus.SUCCESS, Instant.now());
        context.addSpan(comment);

        TraceSummary summary = new TraceSummary(context);

        assertEquals("trace-1", summary.getTraceId());
        assertEquals(TraceStatus.SUCCESS, summary.getStatus());
        assertNull(summary.getFailedStep());
        assertEquals(3, summary.getTotalFindings());
        assertEquals(1, summary.getRuleFindings());
        assertEquals(2, summary.getLlmFindings());
        assertEquals(25, summary.getEstimatedPromptTokens());
        assertEquals(30, summary.getPromptTokens());
        assertEquals(12, summary.getCompletionTokens());
        assertEquals(42, summary.getTotalTokens());
        assertEquals("provider", summary.getTokenSource());
        assertEquals(true, summary.getCommentWritten());
        assertEquals("baseline", summary.getOptimizationGroup());
        assertEquals("qwen-plus", summary.getModelName());
        assertEquals("qwen-plus", summary.getEffectiveModel());
    }

    @Test
    void shouldPreferFailedStatusAndFailedStep() {
        TraceContext context = TraceContext.create("trace-2", Instant.now());
        TraceSpan span = new TraceSpan("github_diff_fetch", Instant.now());
        span.fail(new IllegalStateException("forbidden"), Instant.now());
        context.addSpan(span);

        TraceSummary summary = new TraceSummary(context);

        assertEquals(TraceStatus.FAILED, summary.getStatus());
        assertEquals("github_diff_fetch", summary.getFailedStep());
    }

    @Test
    void shouldUseDegradedStatusWhenNoSpanFailed() {
        TraceContext context = TraceContext.create("trace-3", Instant.now());
        TraceSpan span = new TraceSpan("llm_review", Instant.now());
        span.finish(TraceStatus.DEGRADED, Instant.now());
        context.addSpan(span);

        TraceSummary summary = new TraceSummary(context);

        assertEquals(TraceStatus.DEGRADED, summary.getStatus());
        assertNull(summary.getFailedStep());
    }

    @Test
    void shouldReadFastCommentWriteStatus() {
        TraceContext context = TraceContext.create("trace-4", Instant.now());
        TraceSpan comment = new TraceSpan("github_comment_fast", Instant.now());
        comment.put("commentWritten", true);
        comment.finish(TraceStatus.SUCCESS, Instant.now());
        context.addSpan(comment);

        TraceSummary summary = new TraceSummary(context);

        assertEquals(true, summary.getCommentWritten());
    }
}
