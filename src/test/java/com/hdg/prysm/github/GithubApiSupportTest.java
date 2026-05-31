package com.hdg.prysm.github;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GithubApiSupportTest {

    @Test
    void shouldBuildGithubApiRequestHeaders() {
        HttpRequest request = GithubApiSupport.requestBuilder(
                URI.create("https://api.github.test/repos/owner/repo"),
                "ghs_test_token"
        ).GET().build();

        assertEquals("application/vnd.github+json", request.headers().firstValue("Accept").orElseThrow());
        assertEquals("Bearer ghs_test_token", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("2022-11-28", request.headers().firstValue("X-GitHub-Api-Version").orElseThrow());
    }

    @Test
    void shouldReadRequiredGithubToken() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_TOKEN", "ghs_test_token");

        assertEquals("ghs_test_token", GithubApiSupport.requireToken(environment, "fetch data"));
    }

    @Test
    void shouldRejectMissingGithubToken() {
        MockEnvironment environment = new MockEnvironment();

        assertThrows(IllegalStateException.class, () -> GithubApiSupport.requireToken(environment, "fetch data"));
    }

    @Test
    void shouldNormalizeBaseUrlAndEncodePathSegment() {
        assertEquals("https://api.github.test", GithubApiSupport.trimTrailingSlash(" https://api.github.test/ "));
        assertEquals("repo%20name", GithubApiSupport.encodePathSegment("repo name"));
    }

    @Test
    void shouldRejectNonSuccessResponseStatus() {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(403);

        assertThrows(
                IllegalStateException.class,
                () -> GithubApiSupport.requireSuccess(response, "GitHub request")
        );
    }
}
