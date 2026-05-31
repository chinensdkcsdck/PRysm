package com.hdg.prysm.github;

import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * GitHub REST API 客户端共用的请求构建和响应校验工具。
 */
public final class GithubApiSupport {

    private static final String ACCEPT_HEADER = "application/vnd.github+json";
    private static final String API_VERSION = "2022-11-28";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private GithubApiSupport() {
    }

    /**
     * 读取 GitHub API token，缺失时按具体动作生成错误信息。
     */
    public static String requireToken(Environment environment, String action) {
        if (environment == null) {
            throw new IllegalArgumentException("Environment must not be null");
        }

        String token = environment.getProperty("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("GITHUB_TOKEN must be configured to " + action);
        }
        return token;
    }

    /**
     * 构建带有 GitHub REST API 通用请求头的请求。
     */
    public static HttpRequest.Builder requestBuilder(URI uri, String token) {
        if (uri == null) {
            throw new IllegalArgumentException("GitHub request URI must not be null");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("GitHub token must not be blank");
        }

        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", ACCEPT_HEADER)
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", API_VERSION);
    }

    /**
     * 校验 GitHub API 响应状态码，非 2xx 时直接失败。
     */
    public static void requireSuccess(HttpResponse<?> response, String requestDescription) {
        if (response == null) {
            throw new IllegalArgumentException("GitHub response must not be null");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(requestDescription + " failed with status " + response.statusCode());
        }
    }

    /**
     * 去掉配置中的结尾斜杠，避免拼接 URL 时出现重复斜杠。
     */
    public static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("GitHub API base URL must not be blank");
        }

        String trimmed = value.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * 编码 URL path segment，避免 owner 或 repo 名包含特殊字符时拼接出错。
     */
    public static String encodePathSegment(String value) {
        if (value == null) {
            throw new IllegalArgumentException("GitHub path segment must not be null");
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
