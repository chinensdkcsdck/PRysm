package com.hdg.prysm.diff;

import com.hdg.prysm.context.PrContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GithubPrDiffProviderTest {

    @Test
    void shouldFetchChangedFilesFromGithubApi() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_TOKEN", "ghs_test_token");
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = successfulResponse("""
                [
                  {
                    "filename": "src/main/java/App.java",
                    "status": "modified",
                    "additions": 12,
                    "deletions": 4,
                    "patch": "@@ -1 +1 @@"
                  },
                  {
                    "filename": "README.md",
                    "status": "added",
                    "additions": 3,
                    "deletions": 0
                  }
                ]
                """);
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(response);
        GithubPrDiffProvider provider = newProvider(environment, httpClient, 10, 1000);

        PrContext context = new PrContext("chinensdkcsdck", "PRysm", 4);
        PrDiff diff = provider.fetch(context);

        assertEquals(context, diff.getContext());
        assertEquals(2, diff.getFileCount());
        assertEquals(15, diff.getTotalAdditions());
        assertEquals(4, diff.getTotalDeletions());
        assertEquals("src/main/java/App.java", diff.getChangedFiles().get(0).getFilename());
        assertEquals(PrChangedFileStatus.MODIFIED, diff.getChangedFiles().get(0).getStatus());
        assertEquals("@@ -1 +1 @@", diff.getChangedFiles().get(0).getPatch());
        assertNull(diff.getChangedFiles().get(1).getPatch());

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), anyStringBodyHandler());
        HttpRequest request = requestCaptor.getValue();
        assertEquals("https://api.github.test/repos/chinensdkcsdck/PRysm/pulls/4/files?per_page=100&page=1", request.uri().toString());
        assertEquals("Bearer ghs_test_token", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/vnd.github+json", request.headers().firstValue("Accept").orElseThrow());
    }

    @Test
    void shouldRequireGithubTokenBeforeCallingApi() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        HttpClient httpClient = mock(HttpClient.class);
        GithubPrDiffProvider provider = newProvider(environment, httpClient, 10, 1000);

        PrContext context = new PrContext("chinensdkcsdck", "PRysm", 4);

        assertThrows(IllegalStateException.class, () -> provider.fetch(context));
        verify(httpClient, never()).send(any(HttpRequest.class), anyStringBodyHandler());
    }

    @Test
    void shouldLimitChangedFilesAndPatchCharacters() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_TOKEN", "ghs_test_token");
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = successfulResponse("""
                [
                  {
                    "filename": "first.java",
                    "status": "modified",
                    "additions": 5,
                    "deletions": 1,
                    "patch": "abcdef"
                  },
                  {
                    "filename": "second.java",
                    "status": "modified",
                    "additions": 7,
                    "deletions": 2,
                    "patch": "uvwxyz"
                  }
                ]
                """);
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(response);
        GithubPrDiffProvider provider = newProvider(environment, httpClient, 1, 4);

        PrDiff diff = provider.fetch(new PrContext("chinensdkcsdck", "PRysm", 4));

        assertEquals(1, diff.getFileCount());
        assertEquals("abcd", diff.getChangedFiles().get(0).getPatch());
    }

    @Test
    void shouldFetchNextPageWhenResponsePageIsFull() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_TOKEN", "ghs_test_token");
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> firstPageResponse = successfulResponse(fullPageResponseBody());
        HttpResponse<String> secondPageResponse = successfulResponse("""
                [
                  {
                    "filename": "last.java",
                    "status": "modified",
                    "additions": 1,
                    "deletions": 0,
                    "patch": "patch"
                  }
                ]
                """);
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler()))
                .thenReturn(firstPageResponse)
                .thenReturn(secondPageResponse);
        GithubPrDiffProvider provider = newProvider(environment, httpClient, 101, 10000);

        PrDiff diff = provider.fetch(new PrContext("chinensdkcsdck", "PRysm", 4));

        assertEquals(101, diff.getFileCount());
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, org.mockito.Mockito.times(2)).send(requestCaptor.capture(), anyStringBodyHandler());
        assertEquals("https://api.github.test/repos/chinensdkcsdck/PRysm/pulls/4/files?per_page=100&page=1", requestCaptor.getAllValues().get(0).uri().toString());
        assertEquals("https://api.github.test/repos/chinensdkcsdck/PRysm/pulls/4/files?per_page=100&page=2", requestCaptor.getAllValues().get(1).uri().toString());
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
        GithubPrDiffProvider provider = newProvider(environment, httpClient, 10, 1000);

        assertThrows(IllegalStateException.class, () -> provider.fetch(new PrContext("chinensdkcsdck", "PRysm", 4)));
    }

    private static GithubPrDiffProvider newProvider(
            MockEnvironment environment,
            HttpClient httpClient,
            int maxFiles,
            int maxPatchCharacters
    ) {
        return new GithubPrDiffProvider(
                environment,
                new ObjectMapper(),
                httpClient,
                "https://api.github.test/",
                maxFiles,
                maxPatchCharacters
        );
    }

    private static HttpResponse<String> successfulResponse(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static HttpResponse.BodyHandler<String> anyStringBodyHandler() {
        return org.mockito.ArgumentMatchers.any();
    }

    private static String fullPageResponseBody() {
        StringBuilder body = new StringBuilder("[");
        for (int index = 0; index < 100; index++) {
            if (index > 0) {
                body.append(",");
            }
            body.append("""
                    {
                      "filename": "file-%d.java",
                      "status": "modified",
                      "additions": 1,
                      "deletions": 0,
                      "patch": "patch"
                    }
                    """.formatted(index));
        }
        body.append("]");
        return body.toString();
    }
}
