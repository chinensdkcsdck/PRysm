package com.hdg.prysm.llm;

import com.hdg.prysm.execution.LlmReviewResult;
import com.hdg.prysm.execution.PromptPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleLlmReviewClientRealTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_LLM_REAL_TEST", matches = "true")
    void shouldCallRealOpenAiCompatibleModelAndParseReviewResponse() {
        String apiKey = System.getenv("LLM_API_KEY");
        assertTrue(apiKey != null && !apiKey.isBlank(), "LLM_API_KEY must be set for the real LLM smoke test");

        String apiBaseUrl = envOrDefault(
                "LLM_API_BASE_URL",
                "https://dashscope.aliyuncs.com/compatible-mode/v1"
        );
        String model = envOrDefault("LLM_MODEL", "qwen-plus");
        ObjectMapper objectMapper = new ObjectMapper();

        OpenAiCompatibleLlmReviewClient client = new OpenAiCompatibleLlmReviewClient(
                new MockEnvironment().withProperty("LLM_API_KEY", apiKey),
                objectMapper,
                apiBaseUrl,
                model,
                60
        );

        LlmReviewClientResponse response = client.review(new PromptPayload(
                "You are a strict code review assistant. Return only a JSON object matching the schema.",
                """
                Review this tiny pull request diff.

                File: src/main/java/example/Calculator.java
                Patch:
                @@ -1,5 +1,8 @@
                 package example;

                 public class Calculator {
                +    public int divide(int left, int right) {
                +        return left / right;
                +    }
                 }

                Report only concrete correctness issues. If there are no issues, return an empty findings array.
                """,
                """
                {
                  "type": "object",
                  "required": ["summary", "findings"],
                  "properties": {
                    "summary": {"type": "string"},
                    "findings": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "required": ["severity", "title", "message"],
                        "properties": {
                          "severity": {"type": "string"},
                          "filePath": {"type": "string"},
                          "startLine": {"type": "integer"},
                          "endLine": {"type": "integer"},
                          "line": {"type": "integer"},
                          "title": {"type": "string"},
                          "message": {"type": "string"},
                          "suggestion": {"type": "string"},
                          "ruleId": {"type": "string"}
                        }
                      }
                    }
                  }
                }
                """
        ));

        LlmReviewResult result = new LlmReviewResponseParser(objectMapper).parse(response.getContent());

        assertNotNull(result.getSummary());
        assertNotNull(result.getRawResponse());
        assertTrue(result.getFindings().size() <= 3, "Smoke test response should stay small");
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
