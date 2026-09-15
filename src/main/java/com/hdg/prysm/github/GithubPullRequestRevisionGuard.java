package com.hdg.prysm.github;

import com.hdg.prysm.context.PrContext;
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

/**
 * Reads the latest GitHub PR head revision immediately before comment publication.
 */
@Component
public class GithubPullRequestRevisionGuard implements PullRequestRevisionGuard {

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiBaseUrl;

    @Autowired
    public GithubPullRequestRevisionGuard(
            Environment environment,
            ObjectMapper objectMapper,
            @Value("${prysm.github.api-base-url:https://api.github.com}") String apiBaseUrl
    ) {
        this(
                environment,
                objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                apiBaseUrl
        );
    }

    GithubPullRequestRevisionGuard(
            Environment environment,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            String apiBaseUrl
    ) {
        if (environment == null || objectMapper == null || httpClient == null) {
            throw new IllegalArgumentException("GitHub revision guard dependencies must not be null");
        }
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.apiBaseUrl = GithubApiSupport.trimTrailingSlash(apiBaseUrl);
    }

    @Override
    public boolean isCurrent(PrContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Pull request context must not be null");
        }
        if (context.getTargetRevision() == null) {
            return true;
        }

        String token = GithubApiSupport.requireToken(environment, "verify pull request revision");
        HttpRequest request = GithubApiSupport.requestBuilder(pullRequestUri(context), token).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            GithubApiSupport.requireSuccess(response, "GitHub pull request revision lookup");
            JsonNode root = objectMapper.readTree(response.body());
            String currentRevision = root.path("head").path("sha").asText();
            if (currentRevision.isBlank()) {
                throw new IllegalStateException("GitHub pull request response is missing head.sha");
            }
            return context.getTargetRevision().equalsIgnoreCase(currentRevision);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read GitHub pull request revision", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub pull request revision lookup was interrupted", exception);
        }
    }

    private URI pullRequestUri(PrContext context) {
        return URI.create(apiBaseUrl
                + "/repos/"
                + GithubApiSupport.encodePathSegment(context.getOwner())
                + "/"
                + GithubApiSupport.encodePathSegment(context.getRepository())
                + "/pulls/"
                + context.getPullRequestNumber());
    }
}
