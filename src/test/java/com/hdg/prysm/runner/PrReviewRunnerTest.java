package com.hdg.prysm.runner;

import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.context.PrContextResolver;
import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.diff.PrChangedFileStatus;
import com.hdg.prysm.diff.PrDiff;
import com.hdg.prysm.diff.PrDiffProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

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
        PrDiffProvider diffProvider = mock(PrDiffProvider.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_ACTIONS", "true");
        PrReviewRunner runner = new PrReviewRunner(resolver, diffProvider, environment, false);

        runner.run(new DefaultApplicationArguments());

        verify(resolver, never()).resolve();
        verify(diffProvider, never()).fetch(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 非 GitHub Actions 环境中，本地启动不应触发 PR 解析。
     */
    @Test
    void shouldSkipWhenNotRunningInGithubActions() {
        PrContextResolver resolver = mock(PrContextResolver.class);
        PrDiffProvider diffProvider = mock(PrDiffProvider.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_ACTIONS", "false");
        PrReviewRunner runner = new PrReviewRunner(resolver, diffProvider, environment, true);

        runner.run(new DefaultApplicationArguments());

        verify(resolver, never()).resolve();
        verify(diffProvider, never()).fetch(org.mockito.ArgumentMatchers.any());
    }

    /**
     * GitHub Actions 环境中启用 Runner 时，应解析当前 PR 上下文。
     */
    @Test
    void shouldResolveContextWhenRunningInGithubActions() {
        PrContextResolver resolver = mock(PrContextResolver.class);
        PrDiffProvider diffProvider = mock(PrDiffProvider.class);
        PrContext context = new PrContext("chinensdkcsdck", "PRysm", 3);
        when(resolver.resolve()).thenReturn(context);
        when(diffProvider.fetch(context)).thenReturn(new PrDiff(
                context,
                List.of(new PrChangedFile("README.md", PrChangedFileStatus.MODIFIED, 1, 1, "patch"))
        ));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_ACTIONS", "true");
        PrReviewRunner runner = new PrReviewRunner(resolver, diffProvider, environment, true);

        runner.run(new DefaultApplicationArguments());

        verify(resolver).resolve();
        verify(diffProvider).fetch(context);
    }
}
