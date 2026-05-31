package com.hdg.prysm.diff;

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
 * 从 GitHub REST API 获取 Pull Request 的变更文件列表。
 *
 * 这里只负责 PR4 的 diff 获取，不读取完整文件内容，也不调用 AI。
 */
@Component
public class GithubPrDiffProvider implements PrDiffProvider {

    private static final int PER_PAGE = 100;

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiBaseUrl;
    private final int maxFiles;
    private final int maxPatchCharacters;

    /**
     * 注入运行环境、JSON 解析器和 diff 获取相关配置。
     */
    @Autowired
    public GithubPrDiffProvider(
            Environment environment,
            ObjectMapper objectMapper,
            @Value("${prysm.github.api-base-url:https://api.github.com}") String apiBaseUrl,
            @Value("${prysm.diff.max-files:100}") int maxFiles,
            @Value("${prysm.diff.max-patch-characters:60000}") int maxPatchCharacters
    ) {
        this(
                environment,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                apiBaseUrl,
                maxFiles,
                maxPatchCharacters
        );
    }

    /**
     * 用于测试时注入 HTTP Client，并统一校验配置值。
     */
    GithubPrDiffProvider(
            Environment environment,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            String apiBaseUrl,
            int maxFiles,
            int maxPatchCharacters
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
        if (maxFiles <= 0) {
            throw new IllegalArgumentException("Maximum changed file count must be positive");
        }
        if (maxPatchCharacters < 0) {
            throw new IllegalArgumentException("Maximum patch character count must not be negative");
        }

        this.environment = environment;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.apiBaseUrl = GithubApiSupport.trimTrailingSlash(apiBaseUrl);
        this.maxFiles = maxFiles;
        this.maxPatchCharacters = maxPatchCharacters;
    }

    /**
     * 获取当前 Pull Request 的变更文件，并按配置限制文件数量和 patch 总长度。
     */
    @Override
    public PrDiff fetch(PrContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Pull request context must not be null");
        }

        String token = GithubApiSupport.requireToken(environment, "fetch pull request diff");
        List<PrChangedFile> changedFiles = new ArrayList<>();
        int usedPatchCharacters = 0;

        for (int page = 1; changedFiles.size() < maxFiles; page++) {
            JsonNode pageData = fetchChangedFilesPage(context, token, page);
            if (!pageData.isArray()) {
                throw new IllegalStateException("GitHub changed files response must be a JSON array");
            }
            if (pageData.size() == 0) {
                break;
            }

            for (JsonNode fileNode : pageData) {
                if (changedFiles.size() >= maxFiles) {
                    break;
                }

                String patch = limitPatch(readNullableText(fileNode, "patch"), maxPatchCharacters - usedPatchCharacters);
                changedFiles.add(new PrChangedFile(
                        readRequiredText(fileNode, "filename"),
                        PrChangedFileStatus.fromGithubValue(readRequiredText(fileNode, "status")),
                        readRequiredInt(fileNode, "additions"),
                        readRequiredInt(fileNode, "deletions"),
                        patch
                ));
                usedPatchCharacters += patch == null ? 0 : patch.length();
            }

            if (pageData.size() < PER_PAGE) {
                break;
            }
        }

        return new PrDiff(context, changedFiles);
    }

    /**
     * 请求 GitHub changed files API 的指定分页。
     */
    private JsonNode fetchChangedFilesPage(PrContext context, String token, int page) {
        HttpRequest request = GithubApiSupport.requestBuilder(changedFilesUri(context, page), token)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            GithubApiSupport.requireSuccess(response, "GitHub changed files request");
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read GitHub changed files response", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub changed files request was interrupted", exception);
        }
    }

    /**
     * 拼接当前 Pull Request changed files API 的请求地址。
     */
    private URI changedFilesUri(PrContext context, int page) {
        return URI.create(apiBaseUrl
                + "/repos/"
                + GithubApiSupport.encodePathSegment(context.getOwner())
                + "/"
                + GithubApiSupport.encodePathSegment(context.getRepository())
                + "/pulls/"
                + context.getPullRequestNumber()
                + "/files?per_page="
                + PER_PAGE
                + "&page="
                + page);
    }

    /**
     * 读取 GitHub 响应中的必填字符串字段。
     */
    private static String readRequiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalStateException("GitHub changed file is missing required field: " + fieldName);
        }
        return value.asText();
    }

    /**
     * 读取 GitHub 响应中的可选字符串字段。
     */
    private static String readNullableText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    /**
     * 读取 GitHub 响应中的必填数字字段。
     */
    private static int readRequiredInt(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.canConvertToInt()) {
            throw new IllegalStateException("GitHub changed file is missing required numeric field: " + fieldName);
        }
        return value.asInt();
    }

    /**
     * 按剩余字符数裁剪 patch，避免后续 review 输入过大。
     */
    private static String limitPatch(String patch, int remainingCharacters) {
        if (patch == null || patch.isBlank() || remainingCharacters == 0) {
            return null;
        }
        if (patch.length() <= remainingCharacters) {
            return patch;
        }
        return patch.substring(0, remainingCharacters);
    }

}
