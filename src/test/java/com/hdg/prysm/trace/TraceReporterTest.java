package com.hdg.prysm.trace;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraceReporterTest {

    @Test
    void shouldRenderSpanAsStructuredJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        TraceReporter reporter = new TraceReporter(objectMapper);
        TraceContext context = TraceContext.create("trace-1", Instant.now());
        TraceSpan span = new TraceSpan("github_diff_fetch", Instant.now());
        span.put("changedFiles", 2);
        span.finish(TraceStatus.SUCCESS, Instant.now());

        JsonNode json = objectMapper.readTree(reporter.toJson(span, context));

        assertEquals(1, json.get("schemaVersion").asInt());
        assertEquals("span", json.get("type").asText());
        assertEquals("trace-1", json.get("traceId").asText());
        assertEquals("github_diff_fetch", json.get("spanName").asText());
        assertEquals("SUCCESS", json.get("status").asText());
        assertEquals(2, json.get("attributes").get("changedFiles").asInt());
    }

    @Test
    void shouldRenderSummaryAsStructuredJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        TraceReporter reporter = new TraceReporter(objectMapper);
        TraceContext context = TraceContext.create("trace-1", Instant.now());
        TraceSpan span = new TraceSpan("github_comment", Instant.now());
        span.put("commentWritten", false);
        span.finish(TraceStatus.SKIPPED, Instant.now());
        context.addSpan(span);
        TraceSpan llm = new TraceSpan("llm_review_deep", Instant.now());
        llm.put("optimizationGroup", "baseline");
        llm.put("modelName", "qwen-plus");
        llm.put("effectiveModel", "qwen-plus");
        llm.put("promptTokens", 30);
        llm.put("completionTokens", 12);
        llm.put("totalTokens", 42);
        llm.finish(TraceStatus.SUCCESS, Instant.now());
        context.addSpan(llm);

        JsonNode json = objectMapper.readTree(reporter.toJson(new TraceSummary(context)));

        assertEquals(1, json.get("schemaVersion").asInt());
        assertEquals("summary", json.get("type").asText());
        assertEquals("trace-1", json.get("traceId").asText());
        assertEquals("SUCCESS", json.get("status").asText());
        assertEquals(false, json.get("commentWritten").asBoolean());
        assertEquals("baseline", json.get("optimizationGroup").asText());
        assertEquals("qwen-plus", json.get("modelName").asText());
        assertEquals("qwen-plus", json.get("effectiveModel").asText());
        assertEquals(30, json.get("promptTokens").asInt());
        assertEquals(12, json.get("completionTokens").asInt());
        assertEquals(42, json.get("totalTokens").asInt());
    }
}
