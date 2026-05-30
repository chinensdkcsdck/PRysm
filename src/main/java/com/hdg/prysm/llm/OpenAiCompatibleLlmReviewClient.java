package com.hdg.prysm.llm;

import com.hdg.prysm.execution.PromptPayload;
import com.hdg.prysm.optimization.LlmOptimizationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * OpenAI-compatible Chat Completions 模型客户端。
 *
 * 该实现只依赖兼容接口，具体供应商通过 api-base-url、model 和 LLM_API_KEY 配置切换。
 */
@Component
public class OpenAiCompatibleLlmReviewClient implements LlmReviewClient {

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiBaseUrl;
    private final String model;
    private final int timeoutSeconds;
    private final LlmOptimizationProperties optimizationProperties;

    /**
     * 注入运行环境、JSON 解析器和模型调用配置。
     */
    @Autowired
    public OpenAiCompatibleLlmReviewClient(
            Environment environment,
            ObjectMapper objectMapper,
            @Value("${prysm.llm.api-base-url:https://api.openai.com/v1}") String apiBaseUrl,
            @Value("${prysm.llm.model:gpt-4.1-mini}") String model,
            @Value("${prysm.llm.timeout-seconds:60}") int timeoutSeconds,
            LlmOptimizationProperties optimizationProperties
    ) {
        this(
                environment,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                        .build(),
                apiBaseUrl,
                model,
                timeoutSeconds,
                optimizationProperties
        );
    }

    /**
     * 用于测试时注入 HTTP Client，并统一校验配置。
     */
    OpenAiCompatibleLlmReviewClient(
            Environment environment,
            ObjectMapper objectMapper,
            String apiBaseUrl,
            String model,
            int timeoutSeconds
    ) {
        this(
                environment,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                        .build(),
                apiBaseUrl,
                model,
                timeoutSeconds,
                baselineOptimizationProperties()
        );
    }

    OpenAiCompatibleLlmReviewClient(
            Environment environment,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            String apiBaseUrl,
            String model,
            int timeoutSeconds
    ) {
        this(
                environment,
                objectMapper,
                httpClient,
                apiBaseUrl,
                model,
                timeoutSeconds,
                baselineOptimizationProperties()
        );
    }

    OpenAiCompatibleLlmReviewClient(
            Environment environment,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            String apiBaseUrl,
            String model,
            int timeoutSeconds,
            LlmOptimizationProperties optimizationProperties
    ) {
        if (environment == null) {
            throw new IllegalArgumentException("Environment must not be null");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("Object mapper must not be null");
        }
        if (httpClient == null) {
            throw new IllegalArgumentException("HTTP client must not be null");
        }
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            throw new IllegalArgumentException("LLM API base URL must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("LLM model must not be blank");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("LLM timeout seconds must be positive");
        }
        if (optimizationProperties == null) {
            throw new IllegalArgumentException("Optimization properties must not be null");
        }

        this.environment = environment;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.apiBaseUrl = trimTrailingSlash(apiBaseUrl);
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.optimizationProperties = optimizationProperties;
    }

    /**
     * 调用 OpenAI-compatible Chat Completions API 并提取模型内容。
     */
    @Override
    public String review(PromptPayload promptPayload) {
        if (promptPayload == null) {
            throw new IllegalArgumentException("Prompt payload must not be null");
        }

        String apiKey = requireApiKey();
        HttpRequest request = HttpRequest.newBuilder(chatCompletionsUri())
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(promptPayload)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("LLM request failed with status " + response.statusCode());
            }
            return extractContent(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read LLM response", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM request was interrupted", exception);
        }
    }

    /**
     * 构造 Chat Completions 请求体。
     */
    private String buildRequestBody(PromptPayload promptPayload) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("temperature", 0);
        if (optimizationProperties.isMaxOutputTokensEnabled()) {
            root.put("max_tokens", optimizationProperties.getMaxOutputTokens());
        }

        ArrayNode messages = root.putArray("messages");
        messages.add(message("system", promptPayload.getSystemPrompt()));
        messages.add(message("user", promptPayload.getUserPrompt() + "\n\nOutput schema:\n" + promptPayload.getOutputSchema()));

        ObjectNode responseFormat = objectMapper.createObjectNode();
        responseFormat.put("type", "json_object");
        root.set("response_format", responseFormat);

        return objectMapper.writeValueAsString(root);
    }

    /**
     * 构造单条 Chat Completions message。
     */
    private ObjectNode message(String role, String content) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    /**
     * 从 Chat Completions 响应中提取 choices[0].message.content。
     */
    private String extractContent(String responseBody) {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("LLM response choices must not be empty");
        }
        JsonNode content = choices.get(0).path("message").get("content");
        if (content == null || content.isNull() || content.asText().isBlank()) {
            throw new IllegalStateException("LLM response content must not be blank");
        }
        return content.asText();
    }

    /**
     * 读取模型 API Key。
     */
    private String requireApiKey() {
        String apiKey = environment.getProperty("LLM_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LLM_API_KEY must be configured to run LLM review");
        }
        return apiKey;
    }

    /**
     * 拼接 Chat Completions API 地址。
     */
    private URI chatCompletionsUri() {
        return URI.create(apiBaseUrl + "/chat/completions");
    }

    /**
     * 去掉配置中的结尾斜杠。
     */
    private static String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static LlmOptimizationProperties baselineOptimizationProperties() {
        return new LlmOptimizationProperties(
                "baseline",
                0,
                false,
                800,
                false,
                "fast_model",
                "qwen-turbo",
                false
        );
    }
}
