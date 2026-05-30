package com.hdg.prysm.runner;

import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.context.PrContextResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrReviewRunnerTest {

    /**
     * Runner 开关关闭时，不应解析 PR 上下文。
     */
    @Test
    void shouldSkipWhenRunnerIsDisabled() {
        PrContextResolver resolver = mock(PrContextResolver.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_ACTIONS", "true");
        PrReviewRunner runner = new PrReviewRunner(resolver, environment, false);

        runner.run(new DefaultApplicationArguments());

        verify(resolver, never()).resolve();
    }

    /**
     * 非 GitHub Actions 环境中，本地启动不应触发 PR 解析。
     */
    @Test
    void shouldSkipWhenNotRunningInGithubActions() {
        PrContextResolver resolver = mock(PrContextResolver.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_ACTIONS", "false");
        PrReviewRunner runner = new PrReviewRunner(resolver, environment, true);

        runner.run(new DefaultApplicationArguments());

        verify(resolver, never()).resolve();
    }

    /**
     * GitHub Actions 环境中启用 Runner 时，应解析当前 PR 上下文。
     */
    @Test
    void shouldResolveContextWhenRunningInGithubActions() {
        PrContextResolver resolver = mock(PrContextResolver.class);
        when(resolver.resolve()).thenReturn(new PrContext("chinensdkcsdck", "PRysm", 3));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_ACTIONS", "true");
        PrReviewRunner runner = new PrReviewRunner(resolver, environment, true);

        runner.run(new DefaultApplicationArguments());

        verify(resolver).resolve();
    }
}
