package com.hdg.prysm.mcp;

import java.util.Map;

public record McpToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema,
        boolean readOnly
) {
    public McpToolDefinition {
        if (name == null || name.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("MCP tool name and description are required");
        }
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
    }
}
