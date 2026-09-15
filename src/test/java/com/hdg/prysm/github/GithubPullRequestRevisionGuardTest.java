package com.hdg.prysm.github;

import com.hdg.prysm.context.PrContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GithubPullRequestRevisionGuardTest {

    private static final String REVIEWED_REVISION = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void shouldAcceptUnchangedPullRequestRevision() throws Exception {
        HttpClient httpClient = clientReturning(REVIEWED_REVISION);
        GithubPullRequestRevisionGuard guard = newGuard(httpClient);

        boolean current = guard.isCurrent(new PrContext("owner", "repo", 7, REVIEWED_REVISION));

        assertTrue(current);
        verify(httpClient).send(any(HttpRequest.class), anyStringBodyHandler());
    }

    @Test
    void shouldRejectStalePullRequestRevision() throws Exception {
        HttpClient httpClient = clientReturning("abcdef0123456789abcdef0123456789abcdef01");
        GithubPullRequestRevisionGuard guard = newGuard(httpClient);

        boolean current = guard.isCurrent(new PrContext("owner", "repo", 7, REVIEWED_REVISION));

        assertFalse(current);
    }

    @Test
    void shouldKeepLegacyContextCompatibleWithoutRemoteLookup() {
        HttpClient httpClient = mock(HttpClient.class);
        GithubPullRequestRevisionGuard guard = newGuard(httpClient);

        assertTrue(guard.isCurrent(new PrContext("owner", "repo", 7)));
    }

    private static GithubPullRequestRevisionGuard newGuard(HttpClient client) {
        return new GithubPullRequestRevisionGuard(
                new MockEnvironment().withProperty("GITHUB_TOKEN", "test-token"),
                new ObjectMapper(),
                client,
                "https://api.github.test"
        );
    }

    private static HttpClient clientReturning(String revision) throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"head\":{\"sha\":\"" + revision + "\"}}");
        when(client.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(response);
        return client;
    }

    private static HttpResponse.BodyHandler<String> anyStringBodyHandler() {
        return org.mockito.ArgumentMatchers.any();
    }
}
