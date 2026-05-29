package com.hdg.prysm.context;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 从 GitHub Actions 环境中解析当前 PR 的运行上下文。
 *
 * 依赖 GITHUB_REPOSITORY 定位仓库，并从 GITHUB_EVENT_PATH 指向的事件文件读取 PR 编号。
 */
@Component
public class PrContextResolver {

    private static final String GITHUB_REPOSITORY = "GITHUB_REPOSITORY";
    private static final String GITHUB_EVENT_PATH = "GITHUB_EVENT_PATH";

    private final Environment environment;
    private final ObjectMapper objectMapper;

    /**
     * 注入环境配置和 JSON 解析器。
     */
    public PrContextResolver(Environment environment, ObjectMapper objectMapper) {
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析当前 Pull Request 的 owner、repo 和 PR 编号。
     */
    public PrContext resolve() {
        String repositoryValue = requireEnvironment(GITHUB_REPOSITORY);
        String eventPathValue = requireEnvironment(GITHUB_EVENT_PATH);

        String[] repositoryParts = parseRepository(repositoryValue);
        int pullRequestNumber = readPullRequestNumber(Path.of(eventPathValue));

        return new PrContext(repositoryParts[0], repositoryParts[1], pullRequestNumber);
    }

    /**
     * 读取必需的环境变量，缺失时立即失败。
     */
    private String requireEnvironment(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required GitHub Actions environment variable: " + name);
        }
        return value;
    }

    /**
     * 解析 owner/repository 格式的仓库名。
     */
    private String[] parseRepository(String repositoryValue) {
        String[] parts = repositoryValue.split("/", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalStateException(
                    "GITHUB_REPOSITORY must use owner/repository format, actual value: " + repositoryValue
            );
        }
        return parts;
    }

    /**
     * 从 GitHub 事件文件中读取 PR 编号。
     */
    private int readPullRequestNumber(Path eventPath) {
        if (!Files.isRegularFile(eventPath)) {
            throw new IllegalStateException("GitHub event file does not exist: " + eventPath);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(Files.newBufferedReader(eventPath));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read GitHub event file: " + eventPath, e);
        }

        JsonNode numberNode = root.path("pull_request").path("number");
        if (!numberNode.canConvertToInt() || numberNode.asInt() <= 0) {
            throw new IllegalStateException("GitHub event file does not contain a valid pull_request.number");
        }
        return numberNode.asInt();
    }
}
