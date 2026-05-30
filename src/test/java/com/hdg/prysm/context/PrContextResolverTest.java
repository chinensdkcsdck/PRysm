package com.hdg.prysm.context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrContextResolverTest {

    @TempDir
    Path tempDir;

    /**
     * 覆盖 GitHub Actions 提供完整仓库和 PR 事件信息的正常路径。
     */
    @Test
    void shouldResolvePrContextFromGithubActionsEnvironment() throws IOException {
        Path eventFile = writePullRequestEvent(7);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_REPOSITORY", "chinensdkcsdck/PRysm")
                .withProperty("GITHUB_EVENT_PATH", eventFile.toString());
        PrContextResolver resolver = newResolver(environment);

        PrContext context = resolver.resolve();

        assertEquals("chinensdkcsdck", context.getOwner());
        assertEquals("PRysm", context.getRepository());
        assertEquals(7, context.getPullRequestNumber());
        assertEquals("chinensdkcsdck/PRysm", context.fullRepositoryName());
    }

    /**
     * 缺少仓库环境变量时，解析器应拒绝继续生成上下文。
     */
    @Test
    void shouldRejectMissingRepositoryEnvironment() throws IOException {
        Path eventFile = writePullRequestEvent(7);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_EVENT_PATH", eventFile.toString());
        PrContextResolver resolver = newResolver(environment);

        assertThrows(IllegalStateException.class, resolver::resolve);
    }

    /**
     * 缺少事件文件路径时，解析器应拒绝继续读取 PR 编号。
     */
    @Test
    void shouldRejectMissingEventPathEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_REPOSITORY", "chinensdkcsdck/PRysm");
        PrContextResolver resolver = newResolver(environment);

        assertThrows(IllegalStateException.class, resolver::resolve);
    }

    /**
     * 仓库名不是 owner/repository 格式时，应在解析入口失败。
     */
    @Test
    void shouldRejectInvalidRepositoryFormat() throws IOException {
        Path eventFile = writePullRequestEvent(7);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_REPOSITORY", "PRysm")
                .withProperty("GITHUB_EVENT_PATH", eventFile.toString());
        PrContextResolver resolver = newResolver(environment);

        assertThrows(IllegalStateException.class, resolver::resolve);
    }

    /**
     * 事件文件路径无效时，不能生成 PR 上下文。
     */
    @Test
    void shouldRejectMissingEventFile() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_REPOSITORY", "chinensdkcsdck/PRysm")
                .withProperty("GITHUB_EVENT_PATH", tempDir.resolve("missing-event.json").toString());
        PrContextResolver resolver = newResolver(environment);

        assertThrows(IllegalStateException.class, resolver::resolve);
    }

    /**
     * 非 pull_request 事件缺少 PR 编号，不能生成 PR 上下文。
     */
    @Test
    void shouldRejectEventFileWithoutPullRequestNumber() throws IOException {
        Path eventFile = tempDir.resolve("event.json");
        Files.writeString(eventFile, """
                {
                  "push": {
                    "ref": "refs/heads/main"
                  }
                }
                """);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_REPOSITORY", "chinensdkcsdck/PRysm")
                .withProperty("GITHUB_EVENT_PATH", eventFile.toString());
        PrContextResolver resolver = newResolver(environment);

        assertThrows(IllegalStateException.class, resolver::resolve);
    }

    /**
     * PR 编号不是正整数时，不能生成 PR 上下文。
     */
    @Test
    void shouldRejectInvalidPullRequestNumber() throws IOException {
        Path eventFile = writePullRequestEvent(0);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_REPOSITORY", "chinensdkcsdck/PRysm")
                .withProperty("GITHUB_EVENT_PATH", eventFile.toString());
        PrContextResolver resolver = newResolver(environment);

        assertThrows(IllegalStateException.class, resolver::resolve);
    }

    /**
     * 创建使用测试环境变量的解析器实例。
     */
    private PrContextResolver newResolver(MockEnvironment environment) {
        return new PrContextResolver(environment, new ObjectMapper());
    }

    /**
     * 写入只包含 PR 编号的最小 GitHub pull_request 事件文件。
     */
    private Path writePullRequestEvent(int pullRequestNumber) throws IOException {
        Path eventFile = tempDir.resolve("event.json");
        Files.writeString(eventFile, """
                {
                  "pull_request": {
                    "number": %d
                  }
                }
                """.formatted(pullRequestNumber));
        return eventFile;
    }
}
