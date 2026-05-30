package com.hdg.prysm.github;

import com.hdg.prysm.context.PrContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GithubPullRequestCommentClientTest {

    @Test
    void shouldCreatePullRequestIssueComment() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_TOKEN", "ghs_test_token");
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(201);
        when(response.body()).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(response);
        GithubPullRequestCommentClient client = newClient(environment, httpClient);

        client.createComment(new PrContext("chinensdkcsdck", "PRysm", 12), "review body");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), anyStringBodyHandler());
        HttpRequest request = requestCaptor.getValue();
        assertEquals("https://api.github.test/repos/chinensdkcsdck/PRysm/issues/12/comments", request.uri().toString());
        assertEquals("POST", request.method());
        assertEquals("Bearer ghs_test_token", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/vnd.github+json", request.headers().firstValue("Accept").orElseThrow());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElseThrow());
    }

    @Test
    void shouldRequireGithubTokenBeforeCallingApi() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        HttpClient httpClient = mock(HttpClient.class);
        GithubPullRequestCommentClient client = newClient(environment, httpClient);

        assertThrows(
                IllegalStateException.class,
                () -> client.createComment(new PrContext("chinensdkcsdck", "PRysm", 12), "review body")
        );
        verify(httpClient, never()).send(any(HttpRequest.class), anyStringBodyHandler());
    }

    @Test
    void shouldRejectGithubApiFailureStatus() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_TOKEN", "ghs_test_token");
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(403);
        when(response.body()).thenReturn("{\"message\":\"forbidden\"}");
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(response);
        GithubPullRequestCommentClient client = newClient(environment, httpClient);

        assertThrows(
                IllegalStateException.class,
                () -> client.createComment(new PrContext("chinensdkcsdck", "PRysm", 12), "review body")
        );
    }

    private static GithubPullRequestCommentClient newClient(
            MockEnvironment environment,
            HttpClient httpClient
    ) {
        return new GithubPullRequestCommentClient(
                environment,
                new ObjectMapper(),
                httpClient,
                "https://api.github.test/"
        );
    }

    private static HttpResponse.BodyHandler<String> anyStringBodyHandler() {
        return org.mockito.ArgumentMatchers.any();
    }
}
