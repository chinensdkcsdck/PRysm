package com.hdg.prysm.llm;

import com.hdg.prysm.execution.LlmReviewResult;
import com.hdg.prysm.execution.ReviewFinding;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmReviewResponseParserTest {

    /**
     * 正常模型 JSON 应解析成 LlmReviewResult。
     */
    @Test
    void shouldParseFindings() {
        LlmReviewResponseParser parser = new LlmReviewResponseParser(new ObjectMapper());

        LlmReviewResult result = parser.parse("""
                {
                  "summary": "review complete",
                  "findings": [
                    {
                      "severity": "HIGH",
                      "filePath": "src/App.java",
                      "startLine": 10,
                      "endLine": 12,
                      "title": "Bug",
                      "message": "This can fail.",
                      "suggestion": "Handle the edge case.",
                      "ruleId": "LLM_BUG",
                      "confidence": "HIGH",
                      "category": "bug"
                    }
                  ]
                }
                """);

        assertEquals("review complete", result.getSummary());
        assertEquals(1, result.getFindings().size());
        ReviewFinding finding = result.getFindings().get(0);
        assertEquals("llm", finding.getSource());
        assertEquals("HIGH", finding.getSeverity());
        assertEquals("src/App.java", finding.getFilePath());
        assertEquals(10, finding.getStartLine());
        assertEquals(12, finding.getLine());
        assertEquals("RIGHT", finding.getSide());
        assertEquals("LLM_BUG", finding.getRuleId());
        assertEquals("HIGH", finding.getConfidence());
        assertEquals("bug", finding.getCategory());
    }

    /**
     * 空 findings 应解析为空结果。
     */
    @Test
    void shouldParseEmptyFindings() {
        LlmReviewResponseParser parser = new LlmReviewResponseParser(new ObjectMapper());

        LlmReviewResult result = parser.parse("""
                {
                  "summary": "LGTM",
                  "findings": []
                }
                """);

        assertEquals("LGTM", result.getSummary());
        assertTrue(result.getFindings().isEmpty());
    }

    /**
     * 缺少 findings 字段时应按空 finding 处理。
     */
    @Test
    void shouldAllowMissingFindings() {
        LlmReviewResponseParser parser = new LlmReviewResponseParser(new ObjectMapper());

        LlmReviewResult result = parser.parse("""
                {
                  "summary": "no findings"
                }
                """);

        assertEquals("no findings", result.getSummary());
        assertTrue(result.getFindings().isEmpty());
    }

    /**
     * 缺少必要字段时应失败，避免产生不可用 finding。
     */
    @Test
    void shouldRejectFindingWithoutRequiredFields() {
        LlmReviewResponseParser parser = new LlmReviewResponseParser(new ObjectMapper());

        assertThrows(IllegalStateException.class, () -> parser.parse("""
                {
                  "findings": [
                    {
                      "severity": "LOW",
                      "message": "missing title"
                    }
                  ]
                }
                """));
    }

    /**
     * 非法 JSON 应失败。
     */
    @Test
    void shouldRejectInvalidJson() {
        LlmReviewResponseParser parser = new LlmReviewResponseParser(new ObjectMapper());

        assertThrows(IllegalStateException.class, () -> parser.parse("{ invalid json"));
    }
}
