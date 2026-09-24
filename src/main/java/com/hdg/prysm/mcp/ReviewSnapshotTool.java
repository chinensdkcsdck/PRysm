package com.hdg.prysm.mcp;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ReviewSnapshotTool implements McpTool {

    private final AgenticRunStore store;

    public ReviewSnapshotTool(AgenticRunStore store) { this.store = store; }

    @Override
    public McpToolDefinition definition() {
        return new McpToolDefinition(
                "prysm.review.snapshot",
                "Read the prepared PR diff, context status, files and rule findings for one agentic run.",
                Map.of("type", "object", "required", List.of("runId"), "properties", Map.of("runId", Map.of("type", "string"))),
                true
        );
    }

    @Override
    public McpToolResult call(Map<String, Object> arguments) {
        Object value = arguments.get("runId");
        if (!(value instanceof String runId) || runId.isBlank()) {
            return McpToolResult.failure("INVALID_ARGUMENT", "runId is required");
        }
        AgenticRunStore.RunContext context = store.require(runId);
        return McpToolResult.success(Map.of(
                "fileCount", context.input().getFiles().size(),
                "contextStatus", context.input().getContextStatus().getCode().name(),
                "ruleFindingCount", context.rules().getFindings().size(),
                "files", context.input().getFiles().stream().map(file -> file.getChangedFile().getFilename()).toList()
        ));
    }
}
