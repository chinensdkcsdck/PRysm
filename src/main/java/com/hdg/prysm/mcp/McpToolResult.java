package com.hdg.prysm.mcp;

import java.util.Map;

public record McpToolResult(boolean success, Map<String, Object> content, String errorCode, String message) {
    public static McpToolResult success(Map<String, Object> content) {
        return new McpToolResult(true, content == null ? Map.of() : Map.copyOf(content), null, null);
    }

    public static McpToolResult failure(String errorCode, String message) {
        return new McpToolResult(false, Map.of(), errorCode, message);
    }
}
