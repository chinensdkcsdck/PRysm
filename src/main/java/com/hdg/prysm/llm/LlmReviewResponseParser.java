package com.hdg.prysm.llm;

import com.hdg.prysm.execution.LlmReviewResult;
import com.hdg.prysm.execution.ReviewFinding;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 将模型返回的结构化 JSON 解析成 LlmReviewResult。
 */
@Component
public class LlmReviewResponseParser {

    private static final String SOURCE = "llm";
    private static final String RIGHT_SIDE = "RIGHT";

    private final ObjectMapper objectMapper;

    /**
     * 注入 JSON 解析器。
     */
    public LlmReviewResponseParser(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("Object mapper must not be null");
        }

        this.objectMapper = objectMapper;
    }

    /**
     * 解析模型返回的 JSON 文本。
     */
    public LlmReviewResult parse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalArgumentException("LLM response must not be blank");
        }

        JsonNode root = readRoot(rawResponse);
        String summary = readNullableText(root, "summary");
        JsonNode findingsNode = root.get("findings");
        if (findingsNode == null || findingsNode.isNull()) {
            return new LlmReviewResult(List.of(), summary, rawResponse);
        }
        if (!findingsNode.isArray()) {
            throw new IllegalStateException("LLM findings must be a JSON array");
        }

        List<ReviewFinding> findings = new ArrayList<>();
        for (JsonNode findingNode : findingsNode) {
            findings.add(toFinding(findingNode));
        }
        return new LlmReviewResult(findings, summary, rawResponse);
    }

    /**
     * 读取模型响应 JSON 根节点。
     */
    private JsonNode readRoot(String rawResponse) {
        try {
            return objectMapper.readTree(rawResponse);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to parse LLM response JSON", exception);
        }
    }

    /**
     * 将单个模型 finding 转换成统一 ReviewFinding。
     */
    private static ReviewFinding toFinding(JsonNode node) {
        String filePath = readNullableText(node, "filePath");
        Integer startLine = readNullableInt(node, "startLine");
        Integer endLine = readNullableInt(node, "endLine");
        Integer line = readNullableInt(node, "line");
        if (line == null) {
            line = endLine == null ? startLine : endLine;
        }

        return new ReviewFinding(
                SOURCE,
                readRequiredText(node, "severity"),
                filePath,
                startLine,
                endLine,
                line == null ? null : RIGHT_SIDE,
                line,
                startLine == null ? null : RIGHT_SIDE,
                readRequiredText(node, "title"),
                readRequiredText(node, "message"),
                readNullableText(node, "suggestion"),
                defaultRuleId(readNullableText(node, "ruleId"))
        );
    }

    /**
     * 读取必填字符串字段。
     */
    private static String readRequiredText(JsonNode node, String fieldName) {
        String value = readNullableText(node, fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("LLM finding is missing required field: " + fieldName);
        }
        return value;
    }

    /**
     * 读取可选字符串字段。
     */
    private static String readNullableText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    /**
     * 读取可选整数行号字段。
     */
    private static Integer readNullableInt(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.canConvertToInt()) {
            throw new IllegalStateException("LLM finding field must be an integer: " + fieldName);
        }
        return value.asInt();
    }

    /**
     * 为空 ruleId 补充稳定默认值。
     */
    private static String defaultRuleId(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            return "LLM_REVIEW";
        }
        return ruleId;
    }
}
