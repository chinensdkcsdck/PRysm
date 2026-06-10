package com.hdg.prysm.llm;

import com.hdg.prysm.execution.PromptPayload;
import com.hdg.prysm.execution.LlmTokenUsage;
import com.hdg.prysm.optimization.LlmOptimizationContext;
import com.hdg.prysm.optimization.LlmOptimizationDecision;
import com.hdg.prysm.optimization.LlmOptimizationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleLlmReviewClientTest {

    /**
     * 缺少 API Key 时应给出明确失败原因。
     */
    @Test
    void shouldRejectMissingApiKey() {
        OpenAiCompatibleLlmReviewClient client = new OpenAiCompatibleLlmReviewClient(
                new MockEnvironment(),
                new ObjectMapper(),
                new FakeHttpClient(200, "{}"),
                "https://api.example.com/v1",
                "test-model",
                30
        );

        assertThrows(IllegalStateException.class, () -> client.review(newPromptPayload()));
    }

    /**
     * 成功响应时应提取 choices[0].message.content。
     */
    @Test
    void shouldExtractMessageContent() {
        AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();
        OpenAiCompatibleLlmReviewClient client = new OpenAiCompatibleLlmReviewClient(
                new MockEnvironment().withProperty("LLM_API_KEY", "secret"),
                new ObjectMapper(),
                new FakeHttpClient(
                        200,
                        """
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"summary\\":\\"ok\\",\\"findings\\":[]}"
                              }
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 41,
                            "completion_tokens": 9,
                            "total_tokens": 50
                          }
                        }
                        """,
                        capturedRequest
                ),
                "https://api.example.com/v1/",
                "test-model",
                30
        );

        LlmReviewClientResponse response = client.review(newPromptPayload());
        LlmTokenUsage usage = response.getTokenUsage();

        assertEquals("{\"summary\":\"ok\",\"findings\":[]}", response.getContent());
        assertEquals(41, usage.getPromptTokens());
        assertEquals(9, usage.getCompletionTokens());
        assertEquals(50, usage.getTotalTokens());
        assertEquals(URI.create("https://api.example.com/v1/chat/completions"), capturedRequest.get().uri());
        assertEquals(Optional.of("Bearer secret"), capturedRequest.get().headers().firstValue("Authorization"));
    }

    /**
     * 请求体应包含模型、system prompt、user prompt 和输出 schema。
     */
    @Test
    void shouldBuildExpectedRequestBody() throws Exception {
        AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();
        OpenAiCompatibleLlmReviewClient client = new OpenAiCompatibleLlmReviewClient(
                new MockEnvironment().withProperty("LLM_API_KEY", "secret"),
                new ObjectMapper(),
                new FakeHttpClient(
                        200,
                        """
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"summary\\":\\"ok\\",\\"findings\\":[]}"
                              }
                            }
                          ]
                        }
                        """,
                        capturedRequest
                ),
                "https://api.example.com/v1",
                "test-model",
                30
        );

        client.review(newPromptPayload());
        String requestBody = readBody(capturedRequest.get());
        JsonNode root = new ObjectMapper().readTree(requestBody);

        assertEquals("test-model", root.get("model").asText());
        assertEquals("json_object", root.get("response_format").get("type").asText());
        assertEquals("system", root.get("messages").get(0).get("role").asText());
        assertTrue(root.get("messages").get(1).get("content").asText().contains("Output schema"));
        assertTrue(root.get("max_tokens") == null);
    }

    /**
     * 开启 max-output-tokens 灰度时，请求体应携带 max_tokens。
     */
    @Test
    void shouldIncludeMaxTokensWhenOptimizationIsEnabled() throws Exception {
        AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();
        OpenAiCompatibleLlmReviewClient client = new OpenAiCompatibleLlmReviewClient(
                new MockEnvironment().withProperty("LLM_API_KEY", "secret"),
                new ObjectMapper(),
                new FakeHttpClient(
                        200,
                        """
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"summary\\":\\"ok\\",\\"findings\\":[]}"
                              }
                            }
                          ]
                        }
                        """,
                        capturedRequest
                ),
                "https://api.example.com/v1",
                "test-model",
                30,
                new LlmOptimizationProperties(
                        "exp_1_max_output_tokens",
                        0,
                        true,
                        512,
                        false,
                        "fast_model",
                        "qwen-turbo",
                        false
                )
        );

        client.review(newPromptPayload());
        JsonNode root = new ObjectMapper().readTree(readBody(capturedRequest.get()));

        assertEquals(512, root.get("max_tokens").asInt());
    }

    /**
     * fast-path 命中时，请求体应使用本次决策的有效模型。
     */
    @Test
    void shouldUseEffectiveModelFromOptimizationDecision() throws Exception {
        AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();
        LlmOptimizationContext optimizationContext = new LlmOptimizationContext();
        optimizationContext.setCurrentDecision(LlmOptimizationDecision.fastPath(
                "test-model",
                "fast-model",
                "small_doc_or_workflow_pr"
        ));
        OpenAiCompatibleLlmReviewClient client = new OpenAiCompatibleLlmReviewClient(
                new MockEnvironment().withProperty("LLM_API_KEY", "secret"),
                new ObjectMapper(),
                new FakeHttpClient(
                        200,
                        """
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"summary\\":\\"ok\\",\\"findings\\":[]}"
                              }
                            }
                          ]
                        }
                        """,
                        capturedRequest
                ),
                "https://api.example.com/v1",
                "test-model",
                30,
                new LlmOptimizationProperties(
                        "exp_2_fast_path",
                        0,
                        false,
                        800,
                        true,
                        "fast_model",
                        "fast-model",
                        false
                ),
                optimizationContext
        );

        client.review(newPromptPayload());
        JsonNode root = new ObjectMapper().readTree(readBody(capturedRequest.get()));

        assertEquals("fast-model", root.get("model").asText());
    }

    /**
     * 非 2xx 响应应失败。
     */
    @Test
    void shouldRejectNonSuccessResponse() {
        OpenAiCompatibleLlmReviewClient client = new OpenAiCompatibleLlmReviewClient(
                new MockEnvironment().withProperty("LLM_API_KEY", "secret"),
                new ObjectMapper(),
                new FakeHttpClient(500, "{}"),
                "https://api.example.com/v1",
                "test-model",
                30
        );

        assertThrows(IllegalStateException.class, () -> client.review(newPromptPayload()));
    }

    /**
     * 创建测试用 prompt 载荷。
     */
    private static PromptPayload newPromptPayload() {
        return new PromptPayload("system prompt", "user prompt", "{\"type\":\"object\"}");
    }

    /**
     * 读取请求体文本。
     */
    private static String readBody(HttpRequest request) {
        CapturingSubscriber subscriber = new CapturingSubscriber();
        request.bodyPublisher().orElseThrow().subscribe(subscriber);
        return subscriber.content();
    }

    /**
     * 用于测试的 HTTP Client。
     */
    private static class FakeHttpClient extends HttpClient {

        private final int statusCode;
        private final String body;
        private final AtomicReference<HttpRequest> capturedRequest;

        /**
         * 创建一个不记录请求的 fake HTTP client。
         */
        private FakeHttpClient(int statusCode, String body) {
            this(statusCode, body, new AtomicReference<>());
        }

        /**
         * 创建一个记录请求的 fake HTTP client。
         */
        private FakeHttpClient(int statusCode, String body, AtomicReference<HttpRequest> capturedRequest) {
            this.statusCode = statusCode;
            this.body = body;
            this.capturedRequest = capturedRequest;
        }

        /**
         * 返回预设响应，并记录请求。
         */
        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            capturedRequest.set(request);
            @SuppressWarnings("unchecked")
            T responseBody = (T) body;
            return new FakeHttpResponse<>(statusCode, responseBody);
        }

        /**
         * 异步调用在当前测试中不使用。
         */
        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            throw new UnsupportedOperationException("Async send is not used in tests");
        }

        /**
         * 异步调用在当前测试中不使用。
         */
        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException("Async send is not used in tests");
        }

        /**
         * 返回空 CookieHandler。
         */
        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        /**
         * 返回空连接超时。
         */
        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        /**
         * 返回默认重定向策略。
         */
        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        /**
         * 返回空代理选择器。
         */
        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        /**
         * 返回空认证器。
         */
        @Override
        public Optional<java.net.Authenticator> authenticator() {
            return Optional.empty();
        }

        /**
         * 返回空 SSLContext。
         */
        @Override
        public SSLContext sslContext() {
            return null;
        }

        /**
         * 返回空 SSL 参数。
         */
        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        /**
         * 返回默认 HTTP 版本。
         */
        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        /**
         * 返回空 executor。
         */
        @Override
        public Optional<java.util.concurrent.Executor> executor() {
            return Optional.empty();
        }
    }

    /**
     * 用于测试的 HTTP 响应。
     */
    private record FakeHttpResponse<T>(int statusCode, T body) implements HttpResponse<T> {

        /**
         * 返回空请求。
         */
        @Override
        public HttpRequest request() {
            return null;
        }

        /**
         * 返回空前序响应。
         */
        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        /**
         * 返回空响应头。
         */
        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (key, value) -> true);
        }

        /**
         * 返回空 SSL 会话。
         */
        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }

        /**
         * 返回空 URI。
         */
        @Override
        public URI uri() {
            return null;
        }

        /**
         * 返回默认 HTTP 版本。
         */
        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    /**
     * 捕获 BodyPublisher 输出的订阅者。
     */
    private static class CapturingSubscriber implements Flow.Subscriber<ByteBuffer> {

        private final StringBuilder content = new StringBuilder();

        /**
         * 订阅时请求全部内容。
         */
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        /**
         * 收到字节块时追加到文本。
         */
        @Override
        public void onNext(ByteBuffer item) {
            byte[] bytes = new byte[item.remaining()];
            item.get(bytes);
            content.append(new String(bytes));
        }

        /**
         * 测试中不处理 publisher 错误。
         */
        @Override
        public void onError(Throwable throwable) {
            throw new IllegalStateException("Failed to read request body", throwable);
        }

        /**
         * 请求体读取完成时无需额外处理。
         */
        @Override
        public void onComplete() {
        }

        /**
         * 返回捕获到的请求体内容。
         */
        private String content() {
            return content.toString();
        }
    }
}
