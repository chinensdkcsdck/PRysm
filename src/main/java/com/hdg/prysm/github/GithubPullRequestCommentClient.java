package com.hdg.prysm.github;

import com.hdg.prysm.context.PrContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Writes the aggregated PR12 review result as one GitHub pull request comment.
 */
@Component
public class GithubPullRequestCommentClient {

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiBaseUrl;

    @Autowired
    public GithubPullRequestCommentClient(
            Environment environment,
            ObjectMapper objectMapper,
            @Value("${prysm.github.api-base-url:https://api.github.com}") String apiBaseUrl
    ) {
        this(
                environment,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                apiBaseUrl
        );
    }

    GithubPullRequestCommentClient(
            Environment environment,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            String apiBaseUrl
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
            throw new IllegalArgumentException("GitHub API base URL must not be blank");
        }

        this.environment = environment;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.apiBaseUrl = trimTrailingSlash(apiBaseUrl);
    }

    public void createComment(PrContext context, String body) {
        if (context == null) {
            throw new IllegalArgumentException("Pull request context must not be null");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Pull request comment body must not be blank");
        }

        String token = requireGithubToken();
        HttpRequest request = HttpRequest.newBuilder(commentUri(context))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(commentBody(body)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("GitHub pull request comment failed with status " + response.statusCode());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write GitHub pull request comment", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub pull request comment was interrupted", exception);
        }
    }

    private URI commentUri(PrContext context) {
        return URI.create(apiBaseUrl
                + "/repos/"
                + encodePathSegment(context.getOwner())
                + "/"
                + encodePathSegment(context.getRepository())
                + "/issues/"
                + context.getPullRequestNumber()
                + "/comments");
    }

    private String commentBody(String body) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("body", body);
        return objectMapper.writeValueAsString(node);
    }

    private String requireGithubToken() {
        String token = environment.getProperty("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("GITHUB_TOKEN must be configured to write pull request comments");
        }
        return token;
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
