package com.hdg.prysm.github;

import com.hdg.prysm.context.PrContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.OptionalLong;

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
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.apiBaseUrl = GithubApiSupport.trimTrailingSlash(apiBaseUrl);
    }

    public long createComment(PrContext context, String body) {
        if (context == null) {
            throw new IllegalArgumentException("Pull request context must not be null");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Pull request comment body must not be blank");
        }

        String token = GithubApiSupport.requireToken(environment, "write pull request comments");
        HttpRequest request = GithubApiSupport.requestBuilder(commentUri(context), token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(commentBody(body)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            GithubApiSupport.requireSuccess(response, "GitHub pull request comment");
            return readCommentId(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write GitHub pull request comment", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub pull request comment was interrupted", exception);
        }
    }

    public void updateComment(PrContext context, long commentId, String body) {
        if (context == null) {
            throw new IllegalArgumentException("Pull request context must not be null");
        }
        if (commentId <= 0) {
            throw new IllegalArgumentException("Pull request comment id must be positive");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Pull request comment body must not be blank");
        }

        String token = GithubApiSupport.requireToken(environment, "write pull request comments");
        HttpRequest request = GithubApiSupport.requestBuilder(commentUpdateUri(context, commentId), token)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(commentBody(body)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            GithubApiSupport.requireSuccess(response, "GitHub pull request comment update");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to update GitHub pull request comment", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub pull request comment update was interrupted", exception);
        }
    }

    public OptionalLong findExistingReviewComment(PrContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Pull request context must not be null");
        }

        String token = GithubApiSupport.requireToken(environment, "lookup pull request comments");
        HttpRequest request = GithubApiSupport.requestBuilder(commentUri(context), token)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            GithubApiSupport.requireSuccess(response, "GitHub pull request comments lookup");
            return readExistingReviewCommentId(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to lookup GitHub pull request comments", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub pull request comments lookup was interrupted", exception);
        }
    }

    private URI commentUri(PrContext context) {
        return URI.create(apiBaseUrl
                + "/repos/"
                + GithubApiSupport.encodePathSegment(context.getOwner())
                + "/"
                + GithubApiSupport.encodePathSegment(context.getRepository())
                + "/issues/"
                + context.getPullRequestNumber()
                + "/comments");
    }

    private URI commentUpdateUri(PrContext context, long commentId) {
        return URI.create(apiBaseUrl
                + "/repos/"
                + GithubApiSupport.encodePathSegment(context.getOwner())
                + "/"
                + GithubApiSupport.encodePathSegment(context.getRepository())
                + "/issues/comments/"
                + commentId);
    }

    private String commentBody(String body) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("body", body);
        return objectMapper.writeValueAsString(node);
    }

    private long readCommentId(String body) {
        JsonNode root = objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
        JsonNode id = root.get("id");
        if (id == null || !id.canConvertToLong() || id.asLong() <= 0) {
            throw new IllegalStateException("GitHub pull request comment response is missing id");
        }
        return id.asLong();
    }

    private OptionalLong readExistingReviewCommentId(String body) {
        JsonNode root = objectMapper.readTree(body == null || body.isBlank() ? "[]" : body);
        if (!root.isArray()) {
            throw new IllegalStateException("GitHub pull request comments response must be an array");
        }

        OptionalLong commentId = OptionalLong.empty();
        for (JsonNode comment : root) {
            JsonNode id = comment.get("id");
            JsonNode commentBody = comment.get("body");
            if (id != null
                    && id.canConvertToLong()
                    && id.asLong() > 0
                    && commentBody != null
                    && isReviewComment(commentBody.asText())) {
                commentId = OptionalLong.of(id.asLong());
            }
        }
        return commentId;
    }

    private static boolean isReviewComment(String body) {
        return body != null
                && (body.contains("## PRysm Fast Review Result")
                || body.contains("## PRysm Review Result"));
    }

}
