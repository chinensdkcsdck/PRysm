package com.hdg.prysm.mcp;

import com.hdg.prysm.agentic.AgenticExecutionBudget;
import com.hdg.prysm.agentic.AgenticReviewProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolRegistryTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void exposesSchemasAndAccountsForCalls() {
        McpTool tool = new McpTool() {
            @Override public McpToolDefinition definition() {
                return new McpToolDefinition("test.echo", "Echo input", Map.of("type", "object"), true);
            }
            @Override public McpToolResult call(Map<String, Object> arguments) {
                return McpToolResult.success(arguments);
            }
        };
        AgenticReviewProperties properties = new AgenticReviewProperties(true, 12, 30, 1000, 1, 1);
        McpToolRegistry registry = new McpToolRegistry(List.of(tool), properties, executor);
        AgenticExecutionBudget budget = new AgenticExecutionBudget(2, 1000);

        McpToolResult result = registry.invoke("test.echo", Map.of("value", "ok"), budget);

        assertThat(result.success()).isTrue();
        assertThat(result.content()).containsEntry("value", "ok");
        assertThat(budget.toolCalls()).isEqualTo(1);
        assertThat(registry.definitions()).extracting(McpToolDefinition::name).containsExactly("test.echo");
    }

    @Test
    void returnsStructuredFailureForUnknownTool() {
        AgenticReviewProperties properties = new AgenticReviewProperties(true, 12, 30, 1000, 1, 1);
        McpToolRegistry registry = new McpToolRegistry(List.of(), properties, executor);

        McpToolResult result = registry.invoke("missing", Map.of(), new AgenticExecutionBudget(2, 1000));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("TOOL_NOT_FOUND");
    }

    @Test
    void retriesTransientFailure() {
        AtomicInteger attempts = new AtomicInteger();
        McpTool tool = toolThatFails(attempts, true);
        AgenticReviewProperties properties = new AgenticReviewProperties(true, 12, 30, 1000, 1, 3);
        McpToolRegistry registry = new McpToolRegistry(List.of(tool), properties, executor);

        McpToolResult result = registry.invoke("test.failure", Map.of(), new AgenticExecutionBudget(2, 1000));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("UPSTREAM_BUSY");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void doesNotRetryPermanentFailure() {
        AtomicInteger attempts = new AtomicInteger();
        McpTool tool = toolThatFails(attempts, false);
        AgenticReviewProperties properties = new AgenticReviewProperties(true, 12, 30, 1000, 1, 3);
        McpToolRegistry registry = new McpToolRegistry(List.of(tool), properties, executor);

        McpToolResult result = registry.invoke("test.failure", Map.of(), new AgenticExecutionBudget(2, 1000));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(attempts).hasValue(1);
    }

    private static McpTool toolThatFails(AtomicInteger attempts, boolean retryable) {
        return new McpTool() {
            @Override public McpToolDefinition definition() {
                return new McpToolDefinition("test.failure", "Always fails", Map.of("type", "object"), true);
            }
            @Override public McpToolResult call(Map<String, Object> arguments) throws Exception {
                attempts.incrementAndGet();
                if (retryable) {
                    throw McpToolException.transientFailure("UPSTREAM_BUSY", "try later", null);
                }
                throw McpToolException.permanentFailure("INVALID_ARGUMENT", "bad input");
            }
        };
    }
}
