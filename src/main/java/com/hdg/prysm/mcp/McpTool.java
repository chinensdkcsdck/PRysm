package com.hdg.prysm.mcp;

import java.util.Map;

public interface McpTool {
    McpToolDefinition definition();
    McpToolResult call(Map<String, Object> arguments) throws Exception;
}
