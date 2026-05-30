package com.hdg.prysm.runner;

import com.hdg.prysm.assembly.ReviewExecutionInputAssembler;
import com.hdg.prysm.budget.ReviewContextBudgetResult;
import com.hdg.prysm.budget.ReviewContextBudgetService;
import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.context.PrContextResolver;
import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.diff.PrChangedFileStatus;
import com.hdg.prysm.diff.PrDiff;
import com.hdg.prysm.diff.PrDiffProvider;
import com.hdg.prysm.execution.ContextStatus;
import com.hdg.prysm.execution.ContextStatusCode;
import com.hdg.prysm.execution.PromptPayload;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.review.PrReviewContext;
import com.hdg.prysm.review.PrReviewContextLoader;
import com.hdg.prysm.selection.ReviewFileSelectionResult;
import com.hdg.prysm.selection.ReviewFileSelectionService;
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
        PrReviewContextLoader reviewContextLoader = mock(PrReviewContextLoader.class);
        ReviewFileSelectionService selectionService = mock(ReviewFileSelectionService.class);
        ReviewContextBudgetService budgetService = mock(ReviewContextBudgetService.class);
        ReviewExecutionInputAssembler inputAssembler = mock(ReviewExecutionInputAssembler.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_ACTIONS", "true");
        PrReviewRunner runner = new PrReviewRunner(
                resolver,
                diffProvider,
                reviewContextLoader,
                selectionService,
                budgetService,
                inputAssembler,
                environment,
                false
        );

        runner.run(new DefaultApplicationArguments());

        verify(resolver, never()).resolve();
        verify(diffProvider, never()).fetch(org.mockito.ArgumentMatchers.any());
        verify(reviewContextLoader, never()).load(org.mockito.ArgumentMatchers.any());
        verify(selectionService, never()).select(org.mockito.ArgumentMatchers.any());
        verify(budgetService, never()).allocate(org.mockito.ArgumentMatchers.any());
        verify(inputAssembler, never()).assemble(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 非 GitHub Actions 环境中，本地启动不应触发 PR 解析。
     */
    @Test
    void shouldSkipWhenNotRunningInGithubActions() {
        PrContextResolver resolver = mock(PrContextResolver.class);
        PrDiffProvider diffProvider = mock(PrDiffProvider.class);
        PrReviewContextLoader reviewContextLoader = mock(PrReviewContextLoader.class);
        ReviewFileSelectionService selectionService = mock(ReviewFileSelectionService.class);
        ReviewContextBudgetService budgetService = mock(ReviewContextBudgetService.class);
        ReviewExecutionInputAssembler inputAssembler = mock(ReviewExecutionInputAssembler.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_ACTIONS", "false");
        PrReviewRunner runner = new PrReviewRunner(
                resolver,
                diffProvider,
                reviewContextLoader,
                selectionService,
                budgetService,
                inputAssembler,
                environment,
                true
        );

        runner.run(new DefaultApplicationArguments());

        verify(resolver, never()).resolve();
        verify(diffProvider, never()).fetch(org.mockito.ArgumentMatchers.any());
        verify(reviewContextLoader, never()).load(org.mockito.ArgumentMatchers.any());
        verify(selectionService, never()).select(org.mockito.ArgumentMatchers.any());
        verify(budgetService, never()).allocate(org.mockito.ArgumentMatchers.any());
        verify(inputAssembler, never()).assemble(org.mockito.ArgumentMatchers.any());
    }

    /**
     * GitHub Actions 环境中启用 Runner 时，应串起 PR5、PR6、PR7 和 PR8。
     */
    @Test
    void shouldRunReviewContextSelectionBudgetAndInputAssemblyWhenRunningInGithubActions() {
        PrContextResolver resolver = mock(PrContextResolver.class);
        PrDiffProvider diffProvider = mock(PrDiffProvider.class);
        PrReviewContextLoader reviewContextLoader = mock(PrReviewContextLoader.class);
        ReviewFileSelectionService selectionService = mock(ReviewFileSelectionService.class);
        ReviewContextBudgetService budgetService = mock(ReviewContextBudgetService.class);
        ReviewExecutionInputAssembler inputAssembler = mock(ReviewExecutionInputAssembler.class);
        PrContext context = new PrContext("chinensdkcsdck", "PRysm", 3);
        PrDiff diff = new PrDiff(
                context,
                List.of(new PrChangedFile("README.md", PrChangedFileStatus.MODIFIED, 1, 1, "patch"))
        );
        PrReviewContext reviewContext = new PrReviewContext(diff, List.of());
        ReviewFileSelectionResult selectionResult = new ReviewFileSelectionResult(reviewContext, List.of());
        ReviewContextBudgetResult budgetResult = new ReviewContextBudgetResult(selectionResult, List.of(), 32000);
        ReviewExecutionInput executionInput = new ReviewExecutionInput(
                context,
                diff,
                List.of(),
                new ContextStatus(ContextStatusCode.SKIPPED, "no files selected"),
                new PromptPayload("system", "user", "{}")
        );
        when(resolver.resolve()).thenReturn(context);
        when(diffProvider.fetch(context)).thenReturn(diff);
        when(reviewContextLoader.load(diff)).thenReturn(reviewContext);
        when(selectionService.select(reviewContext)).thenReturn(selectionResult);
        when(budgetService.allocate(selectionResult)).thenReturn(budgetResult);
        when(inputAssembler.assemble(budgetResult)).thenReturn(executionInput);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_ACTIONS", "true");
        PrReviewRunner runner = new PrReviewRunner(
                resolver,
                diffProvider,
                reviewContextLoader,
                selectionService,
                budgetService,
                inputAssembler,
                environment,
                true
        );

        runner.run(new DefaultApplicationArguments());

        verify(resolver).resolve();
        verify(diffProvider).fetch(context);
        verify(reviewContextLoader).load(diff);
        verify(selectionService).select(reviewContext);
        verify(budgetService).allocate(selectionResult);
        verify(inputAssembler).assemble(budgetResult);
    }
}
