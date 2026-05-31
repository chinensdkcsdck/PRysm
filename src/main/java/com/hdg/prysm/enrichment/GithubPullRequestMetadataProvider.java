package com.hdg.prysm.enrichment;

import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.github.GithubApiSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 GitHub REST API 获取 Pull Request 扩展上下文。
 *
 * 该类只读取 PR 标题、正文和少量 commit message，不读取文件内容。
 */
@Component
public class GithubPullRequestMetadataProvider implements PullRequestMetadataProvider {

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiBaseUrl;
    private final int maxBodyCharacters;
    private final int maxCommits;
    private final int maxCommitMessageCharacters;

    /**
     * 注入运行环境、JSON 解析器和元数据限制配置。
     */
    @Autowired
    public GithubPullRequestMetadataProvider(
            Environment environment,
            ObjectMapper objectMapper,
            @Value("${prysm.github.api-base-url:https://api.github.com}") String apiBaseUrl,
            @Value("${prysm.review.enrichment.max-body-chars:4000}") int maxBodyCharacters,
            @Value("${prysm.review.enrichment.max-commits:5}") int maxCommits,
            @Value("${prysm.review.enrichment.max-commit-message-chars:300}") int maxCommitMessageCharacters
    ) {
        this(
                environment,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                apiBaseUrl,
                maxBodyCharacters,
                maxCommits,
                maxCommitMessageCharacters
        );
    }

    /**
     * 用于测试时注入 HTTP Client，并统一校验配置值。
     */
    GithubPullRequestMetadataProvider(
            Environment environment,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            String apiBaseUrl,
            int maxBodyCharacters,
            int maxCommits,
            int maxCommitMessageCharacters
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
        if (maxBodyCharacters <= 0) {
            throw new IllegalArgumentException("Maximum body characters must be positive");
        }
        if (maxCommits < 0) {
            throw new IllegalArgumentException("Maximum commits must not be negative");
        }
        if (maxCommitMessageCharacters <= 0) {
            throw new IllegalArgumentException("Maximum commit message characters must be positive");
        }

        this.environment = environment;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.apiBaseUrl = GithubApiSupport.trimTrailingSlash(apiBaseUrl);
        this.maxBodyCharacters = maxBodyCharacters;
        this.maxCommits = maxCommits;
        this.maxCommitMessageCharacters = maxCommitMessageCharacters;
    }

    /**
     * 获取当前 Pull Request 的扩展上下文。
     */
    @Override
    public PullRequestMetadata fetch(PrContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Pull request context must not be null");
        }

        String token = GithubApiSupport.requireToken(environment, "fetch pull request metadata");
        JsonNode pullRequestNode = fetchJson(pullRequestUri(context), token);
        String title = readNullableText(pullRequestNode, "title");
        String body = limitText(readNullableText(pullRequestNode, "body"), maxBodyCharacters);
        List<String> commitMessages = maxCommits == 0 ? List.of() : fetchCommitMessages(context, token);
        String note = bodyWasTruncated(readNullableText(pullRequestNode, "body"), body)
                ? "pull request body truncated"
                : null;
        return new PullRequestMetadata(title, body, commitMessages, note);
    }

    /**
     * 获取少量 commit message。
     */
    private List<String> fetchCommitMessages(PrContext context, String token) {
        JsonNode commitsNode = fetchJson(commitsUri(context), token);
        if (!commitsNode.isArray()) {
            throw new IllegalStateException("GitHub commits response must be a JSON array");
        }

        List<String> commitMessages = new ArrayList<>();
        for (JsonNode commitNode : commitsNode) {
            if (commitMessages.size() >= maxCommits) {
                break;
            }
            String message = readNullableText(commitNode.path("commit"), "message");
            if (message != null && !message.isBlank()) {
                commitMessages.add(limitText(firstLine(message), maxCommitMessageCharacters));
            }
        }
        return commitMessages;
    }

    /**
     * 请求 GitHub API 并解析 JSON。
     */
    private JsonNode fetchJson(URI uri, String token) {
        HttpRequest request = GithubApiSupport.requestBuilder(uri, token)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            GithubApiSupport.requireSuccess(response, "GitHub metadata request");
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read GitHub metadata response", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub metadata request was interrupted", exception);
        }
    }

    /**
     * 拼接 Pull Request API 地址。
     */
    private URI pullRequestUri(PrContext context) {
        return URI.create(apiBaseUrl
                + "/repos/"
                + GithubApiSupport.encodePathSegment(context.getOwner())
                + "/"
                + GithubApiSupport.encodePathSegment(context.getRepository())
                + "/pulls/"
                + context.getPullRequestNumber());
    }

    /**
     * 拼接 Pull Request commits API 地址。
     */
    private URI commitsUri(PrContext context) {
        return URI.create(pullRequestUri(context) + "/commits?per_page=" + Math.max(1, maxCommits));
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
     * 返回 commit message 第一行。
     */
    private static String firstLine(String message) {
        return message.split("\\R", 2)[0];
    }

    /**
     * 按最大字符数裁剪文本。
     */
    private static String limitText(String value, int maxCharacters) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= maxCharacters) {
            return value;
        }
        return value.substring(0, maxCharacters);
    }

    /**
     * 判断 PR body 是否被裁剪。
     */
    private static boolean bodyWasTruncated(String originalBody, String limitedBody) {
        return originalBody != null && limitedBody != null && originalBody.length() > limitedBody.length();
    }

}
