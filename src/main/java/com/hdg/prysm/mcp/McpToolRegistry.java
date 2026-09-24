package com.hdg.prysm.mcp;

import com.hdg.prysm.agentic.AgenticExecutionBudget;
import com.hdg.prysm.agentic.AgenticReviewProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class McpToolRegistry {

    private final Map<String, McpTool> tools;
    private final AgenticReviewProperties properties;
    private final ExecutorService executor;

    public McpToolRegistry(Collection<McpTool> tools, AgenticReviewProperties properties, ExecutorService mcpToolExecutor) {
        Map<String, McpTool> indexed = new LinkedHashMap<>();
        for (McpTool tool : tools) {
            McpTool previous = indexed.put(tool.definition().name(), tool);
            if (previous != null) {
                throw new IllegalStateException("Duplicate MCP tool: " + tool.definition().name());
            }
        }
        this.tools = Map.copyOf(indexed);
        this.properties = properties;
        this.executor = mcpToolExecutor;
    }

    public Collection<McpToolDefinition> definitions() {
        return tools.values().stream().map(McpTool::definition).toList();
    }

    public McpToolResult invoke(String name, Map<String, Object> arguments, AgenticExecutionBudget budget) {
        McpTool tool = tools.get(name);
        if (tool == null) {
            return McpToolResult.failure("TOOL_NOT_FOUND", "Unknown MCP tool: " + name);
        }
        budget.consumeToolCall();
        ToolFailure lastFailure = null;
        for (int attempt = 1; attempt <= properties.getToolMaxAttempts(); attempt++) {
            try {
                return CompletableFuture.supplyAsync(() -> call(tool, arguments), executor)
                        .orTimeout(Duration.ofSeconds(properties.getToolTimeoutSeconds()).toMillis(), TimeUnit.MILLISECONDS)
                        .join();
            } catch (RuntimeException exception) {
                lastFailure = classify(exception);
                if (!lastFailure.retryable()) {
                    break;
                }
            }
        }
        return McpToolResult.failure(
                lastFailure == null ? "TOOL_FAILED" : lastFailure.errorCode(),
                lastFailure == null ? "Tool failed" : lastFailure.message());
    }

    private static McpToolResult call(McpTool tool, Map<String, Object> arguments) {
        try {
            return tool.call(arguments == null ? Map.of() : arguments);
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }

    private static ToolFailure classify(RuntimeException exception) {
        Throwable cause = exception;
        while ((cause instanceof CompletionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof McpToolException toolException) {
            return new ToolFailure(toolException.errorCode(), toolException.getMessage(), toolException.retryable());
        }
        if (cause instanceof TimeoutException) {
            return new ToolFailure("TOOL_TIMEOUT", "Tool call timed out", true);
        }
        String message = cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName() : cause.getMessage();
        return new ToolFailure("TOOL_FAILED", message, false);
    }

    private record ToolFailure(String errorCode, String message, boolean retryable) {}
}
