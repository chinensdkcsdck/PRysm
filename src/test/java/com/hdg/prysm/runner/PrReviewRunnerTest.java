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
import com.hdg.prysm.enrichment.ReviewContextEnrichmentService;
import com.hdg.prysm.comment.ReviewCommentRenderer;
import com.hdg.prysm.execution.ContextStatus;
import com.hdg.prysm.execution.ContextStatusCode;
import com.hdg.prysm.execution.LlmReviewResult;
import com.hdg.prysm.execution.PromptPayload;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.RuleEngineResult;
import com.hdg.prysm.github.GithubPullRequestCommentClient;
import com.hdg.prysm.llm.LlmReviewRunner;
import com.hdg.prysm.optimization.LlmOptimizationContext;
import com.hdg.prysm.optimization.LlmOptimizationPlanner;
import com.hdg.prysm.optimization.LlmOptimizationProperties;
import com.hdg.prysm.quality.ReviewFindingQualityGate;
import com.hdg.prysm.review.PrReviewContext;
import com.hdg.prysm.review.PrReviewContextLoader;
import com.hdg.prysm.result.ReviewAggregationResult;
import com.hdg.prysm.result.ReviewResultAggregator;
import com.hdg.prysm.rule.RuleEngineRunner;
import com.hdg.prysm.selection.ReviewFileSelectionResult;
import com.hdg.prysm.selection.ReviewFileSelectionService;
import com.hdg.prysm.trace.TraceContext;
import com.hdg.prysm.trace.TraceRecorder;
import com.hdg.prysm.trace.TraceReporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.OptionalLong;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrReviewRunnerTest {

    /**
     * Runner 寮€鍏冲叧闂椂锛屼笉搴旇В鏋?PR 涓婁笅鏂囥€?
     */
    @Test
    void shouldSkipWhenRunnerIsDisabled() {
        PrContextResolver resolver = mock(PrContextResolver.class);
        PrDiffProvider diffProvider = mock(PrDiffProvider.class);
        PrReviewContextLoader reviewContextLoader = mock(PrReviewContextLoader.class);
        ReviewFileSelectionService selectionService = mock(ReviewFileSelectionService.class);
        ReviewContextBudgetService budgetService = mock(ReviewContextBudgetService.class);
        ReviewExecutionInputAssembler inputAssembler = mock(ReviewExecutionInputAssembler.class);
        ReviewContextEnrichmentService enrichmentService = mock(ReviewContextEnrichmentService.class);
        RuleEngineRunner ruleEngineRunner = mock(RuleEngineRunner.class);
        LlmReviewRunner llmReviewRunner = mock(LlmReviewRunner.class);
        ReviewResultAggregator aggregator = mock(ReviewResultAggregator.class);
        ReviewCommentRenderer commentRenderer = mock(ReviewCommentRenderer.class);
        GithubPullRequestCommentClient commentClient = mock(GithubPullRequestCommentClient.class);
        TraceRecorder traceRecorder = mock(TraceRecorder.class);
        TraceReporter traceReporter = mock(TraceReporter.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_ACTIONS", "true");
        PrReviewRunner runner = new PrReviewRunner(
                resolver,
                diffProvider,
                reviewContextLoader,
                selectionService,
                budgetService,
                inputAssembler,
                enrichmentService,
                ruleEngineRunner,
                llmReviewRunner,
                aggregator,
                commentRenderer,
                commentClient,
                new ReviewFindingQualityGate(),
                baselineOptimizationProperties(),
                baselineOptimizationPlanner(),
                new LlmOptimizationContext(),
                traceRecorder,
                traceReporter,
                environment,
                false,
                true,
                "test-model",
                "fast-model"
        );

        runner.run(new DefaultApplicationArguments());

        verify(resolver, never()).resolve();
        verify(diffProvider, never()).fetch(org.mockito.ArgumentMatchers.any());
        verify(reviewContextLoader, never()).load(org.mockito.ArgumentMatchers.any());
        verify(selectionService, never()).select(org.mockito.ArgumentMatchers.any());
        verify(budgetService, never()).allocate(org.mockito.ArgumentMatchers.any());
        verify(inputAssembler, never()).assemble(org.mockito.ArgumentMatchers.any());
        verify(enrichmentService, never()).enrich(org.mockito.ArgumentMatchers.any());
        verify(ruleEngineRunner, never()).run(org.mockito.ArgumentMatchers.any());
        verify(llmReviewRunner, never()).run(org.mockito.ArgumentMatchers.any());
        verify(aggregator, never()).aggregate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(commentRenderer, never()).render(org.mockito.ArgumentMatchers.any());
        verify(commentClient, never()).createComment(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(traceRecorder, never()).createTrace();
        verify(traceReporter, never()).report(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 闈?GitHub Actions 鐜涓紝鏈湴鍚姩涓嶅簲瑙﹀彂 PR 瑙ｆ瀽銆?
     */
    @Test
    void shouldSkipWhenNotRunningInGithubActions() {
        PrContextResolver resolver = mock(PrContextResolver.class);
        PrDiffProvider diffProvider = mock(PrDiffProvider.class);
        PrReviewContextLoader reviewContextLoader = mock(PrReviewContextLoader.class);
        ReviewFileSelectionService selectionService = mock(ReviewFileSelectionService.class);
        ReviewContextBudgetService budgetService = mock(ReviewContextBudgetService.class);
        ReviewExecutionInputAssembler inputAssembler = mock(ReviewExecutionInputAssembler.class);
        ReviewContextEnrichmentService enrichmentService = mock(ReviewContextEnrichmentService.class);
        RuleEngineRunner ruleEngineRunner = mock(RuleEngineRunner.class);
        LlmReviewRunner llmReviewRunner = mock(LlmReviewRunner.class);
        ReviewResultAggregator aggregator = mock(ReviewResultAggregator.class);
        ReviewCommentRenderer commentRenderer = mock(ReviewCommentRenderer.class);
        GithubPullRequestCommentClient commentClient = mock(GithubPullRequestCommentClient.class);
        TraceRecorder traceRecorder = mock(TraceRecorder.class);
        TraceReporter traceReporter = mock(TraceReporter.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_ACTIONS", "false");
        PrReviewRunner runner = new PrReviewRunner(
                resolver,
                diffProvider,
                reviewContextLoader,
                selectionService,
                budgetService,
                inputAssembler,
                enrichmentService,
                ruleEngineRunner,
                llmReviewRunner,
                aggregator,
                commentRenderer,
                commentClient,
                new ReviewFindingQualityGate(),
                baselineOptimizationProperties(),
                baselineOptimizationPlanner(),
                new LlmOptimizationContext(),
                traceRecorder,
                traceReporter,
                environment,
                true,
                true,
                "test-model",
                "fast-model"
        );

        runner.run(new DefaultApplicationArguments());

        verify(resolver, never()).resolve();
        verify(diffProvider, never()).fetch(org.mockito.ArgumentMatchers.any());
        verify(reviewContextLoader, never()).load(org.mockito.ArgumentMatchers.any());
        verify(selectionService, never()).select(org.mockito.ArgumentMatchers.any());
        verify(budgetService, never()).allocate(org.mockito.ArgumentMatchers.any());
        verify(inputAssembler, never()).assemble(org.mockito.ArgumentMatchers.any());
        verify(enrichmentService, never()).enrich(org.mockito.ArgumentMatchers.any());
        verify(ruleEngineRunner, never()).run(org.mockito.ArgumentMatchers.any());
        verify(llmReviewRunner, never()).run(org.mockito.ArgumentMatchers.any());
        verify(aggregator, never()).aggregate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(commentRenderer, never()).render(org.mockito.ArgumentMatchers.any());
        verify(commentClient, never()).createComment(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(traceRecorder, never()).createTrace();
        verify(traceReporter, never()).report(org.mockito.ArgumentMatchers.any());
    }

    /**
     * GitHub Actions 鐜涓惎鐢?Runner 鏃讹紝搴斾覆璧蜂笂涓嬫枃鍔犺浇銆侀绠椼€佽緭鍏ョ粍瑁呭拰鎵╁睍涓婁笅鏂囬棬绂併€?
     */
    @Test
    void shouldRunReviewContextSelectionBudgetInputAssemblyAndEnrichmentWhenRunningInGithubActions() {
        PrContextResolver resolver = mock(PrContextResolver.class);
        PrDiffProvider diffProvider = mock(PrDiffProvider.class);
        PrReviewContextLoader reviewContextLoader = mock(PrReviewContextLoader.class);
        ReviewFileSelectionService selectionService = mock(ReviewFileSelectionService.class);
        ReviewContextBudgetService budgetService = mock(ReviewContextBudgetService.class);
        ReviewExecutionInputAssembler inputAssembler = mock(ReviewExecutionInputAssembler.class);
        ReviewContextEnrichmentService enrichmentService = mock(ReviewContextEnrichmentService.class);
        RuleEngineRunner ruleEngineRunner = mock(RuleEngineRunner.class);
        LlmReviewRunner llmReviewRunner = mock(LlmReviewRunner.class);
        ReviewResultAggregator aggregator = mock(ReviewResultAggregator.class);
        ReviewCommentRenderer commentRenderer = mock(ReviewCommentRenderer.class);
        GithubPullRequestCommentClient commentClient = mock(GithubPullRequestCommentClient.class);
        TraceRecorder traceRecorder = new TraceRecorder();
        TraceReporter traceReporter = mock(TraceReporter.class);
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
        ReviewExecutionInput enrichedInput = new ReviewExecutionInput(
                context,
                diff,
                List.of(),
                new ContextStatus(ContextStatusCode.LIMITED, "metadata unavailable"),
                new PromptPayload("system", "enriched user", "{}")
        );
        RuleEngineResult ruleResult = new RuleEngineResult(List.of(), "no rule findings");
        LlmReviewResult llmResult = new LlmReviewResult(List.of(), "no llm findings", null);
        ReviewAggregationResult aggregationResult = new ReviewAggregationResult(
                List.of(),
                0,
                0,
                0,
                ruleResult.getSummary(),
                llmResult.getSummary()
        );
        when(resolver.resolve()).thenReturn(context);
        when(diffProvider.fetch(context)).thenReturn(diff);
        when(reviewContextLoader.load(diff)).thenReturn(reviewContext);
        when(selectionService.select(reviewContext)).thenReturn(selectionResult);
        when(budgetService.allocate(selectionResult)).thenReturn(budgetResult);
        when(inputAssembler.assemble(budgetResult)).thenReturn(executionInput);
        when(enrichmentService.enrich(executionInput)).thenReturn(enrichedInput);
        when(ruleEngineRunner.run(enrichedInput)).thenReturn(ruleResult);
        when(llmReviewRunner.run(enrichedInput)).thenReturn(llmResult);
        when(aggregator.aggregate(
                org.mockito.ArgumentMatchers.eq(enrichedInput),
                org.mockito.ArgumentMatchers.eq(ruleResult),
                org.mockito.ArgumentMatchers.any(LlmReviewResult.class)
        )).thenReturn(aggregationResult);
        when(commentRenderer.renderFastReview(aggregationResult)).thenReturn("fast review comment");
        when(commentRenderer.render(aggregationResult)).thenReturn("review comment");
        when(commentClient.findExistingReviewComment(context)).thenReturn(OptionalLong.empty());
        when(commentClient.createComment(context, "fast review comment")).thenReturn(12345L);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_ACTIONS", "true");
        PrReviewRunner runner = new PrReviewRunner(
                resolver,
                diffProvider,
                reviewContextLoader,
                selectionService,
                budgetService,
                inputAssembler,
                enrichmentService,
                ruleEngineRunner,
                llmReviewRunner,
                aggregator,
                commentRenderer,
                commentClient,
                new ReviewFindingQualityGate(),
                baselineOptimizationProperties(),
                baselineOptimizationPlanner(),
                new LlmOptimizationContext(),
                traceRecorder,
                traceReporter,
                environment,
                true,
                true,
                "test-model",
                "fast-model"
        );

        runner.run(new DefaultApplicationArguments());

        verify(resolver).resolve();
        verify(diffProvider).fetch(context);
        verify(reviewContextLoader).load(diff);
        verify(selectionService).select(reviewContext);
        verify(budgetService).allocate(selectionResult);
        verify(inputAssembler).assemble(budgetResult);
        verify(enrichmentService).enrich(executionInput);
        verify(ruleEngineRunner).run(enrichedInput);
        verify(llmReviewRunner, times(2)).run(enrichedInput);
        verify(aggregator, times(2)).aggregate(
                org.mockito.ArgumentMatchers.eq(enrichedInput),
                org.mockito.ArgumentMatchers.eq(ruleResult),
                org.mockito.ArgumentMatchers.any(LlmReviewResult.class)
        );
        verify(commentRenderer).render(aggregationResult);
        verify(commentRenderer).renderFastReview(aggregationResult);
        verify(commentClient).createComment(context, "fast review comment");
        verify(commentClient).updateComment(context, 12345L, "review comment");
        verify(traceReporter).report(org.mockito.ArgumentMatchers.any(TraceContext.class));
    }

    @Test
    void shouldReuseExistingReviewCommentForFastReview() {
        PrContextResolver resolver = mock(PrContextResolver.class);
        PrDiffProvider diffProvider = mock(PrDiffProvider.class);
        PrReviewContextLoader reviewContextLoader = mock(PrReviewContextLoader.class);
        ReviewFileSelectionService selectionService = mock(ReviewFileSelectionService.class);
        ReviewContextBudgetService budgetService = mock(ReviewContextBudgetService.class);
        ReviewExecutionInputAssembler inputAssembler = mock(ReviewExecutionInputAssembler.class);
        ReviewContextEnrichmentService enrichmentService = mock(ReviewContextEnrichmentService.class);
        RuleEngineRunner ruleEngineRunner = mock(RuleEngineRunner.class);
        LlmReviewRunner llmReviewRunner = mock(LlmReviewRunner.class);
        ReviewResultAggregator aggregator = mock(ReviewResultAggregator.class);
        ReviewCommentRenderer commentRenderer = mock(ReviewCommentRenderer.class);
        GithubPullRequestCommentClient commentClient = mock(GithubPullRequestCommentClient.class);
        TraceRecorder traceRecorder = new TraceRecorder();
        TraceReporter traceReporter = mock(TraceReporter.class);

        PrContext context = new PrContext("chinensdkcsdck", "PRysm", 3);
        PrDiff diff = new PrDiff(
                context,
                List.of(new PrChangedFile("README.md", PrChangedFileStatus.MODIFIED, 1, 1, "@@ -1 +1 @@"))
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
        ReviewExecutionInput enrichedInput = new ReviewExecutionInput(
                context,
                diff,
                List.of(),
                new ContextStatus(ContextStatusCode.LIMITED, "metadata unavailable"),
                new PromptPayload("system", "enriched user", "{}")
        );
        RuleEngineResult ruleResult = new RuleEngineResult(List.of(), "no rule findings");
        LlmReviewResult llmResult = new LlmReviewResult(List.of(), "no llm findings", null);
        ReviewAggregationResult aggregationResult = new ReviewAggregationResult(
                List.of(),
                0,
                0,
                0,
                ruleResult.getSummary(),
                llmResult.getSummary()
        );
        when(resolver.resolve()).thenReturn(context);
        when(diffProvider.fetch(context)).thenReturn(diff);
        when(reviewContextLoader.load(diff)).thenReturn(reviewContext);
        when(selectionService.select(reviewContext)).thenReturn(selectionResult);
        when(budgetService.allocate(selectionResult)).thenReturn(budgetResult);
        when(inputAssembler.assemble(budgetResult)).thenReturn(executionInput);
        when(enrichmentService.enrich(executionInput)).thenReturn(enrichedInput);
        when(ruleEngineRunner.run(enrichedInput)).thenReturn(ruleResult);
        when(llmReviewRunner.run(enrichedInput)).thenReturn(llmResult);
        when(aggregator.aggregate(
                org.mockito.ArgumentMatchers.eq(enrichedInput),
                org.mockito.ArgumentMatchers.eq(ruleResult),
                org.mockito.ArgumentMatchers.any(LlmReviewResult.class)
        )).thenReturn(aggregationResult);
        when(commentRenderer.renderFastReview(aggregationResult)).thenReturn("fast review comment");
        when(commentRenderer.render(aggregationResult)).thenReturn("review comment");
        when(commentClient.findExistingReviewComment(context)).thenReturn(OptionalLong.of(12345L));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_ACTIONS", "true");
        PrReviewRunner runner = new PrReviewRunner(
                resolver,
                diffProvider,
                reviewContextLoader,
                selectionService,
                budgetService,
                inputAssembler,
                enrichmentService,
                ruleEngineRunner,
                llmReviewRunner,
                aggregator,
                commentRenderer,
                commentClient,
                new ReviewFindingQualityGate(),
                baselineOptimizationProperties(),
                baselineOptimizationPlanner(),
                new LlmOptimizationContext(),
                traceRecorder,
                traceReporter,
                environment,
                true,
                true,
                "test-model",
                "fast-model"
        );

        runner.run(new DefaultApplicationArguments());

        verify(commentClient).findExistingReviewComment(context);
        verify(commentClient, never()).createComment(context, "fast review comment");
        verify(commentClient).updateComment(context, 12345L, "fast review comment");
        verify(commentClient).updateComment(context, 12345L, "review comment");
    }

    /**
     * 璇勮鍥炲啓鍏抽棴鏃讹紝浠嶅畬鎴?PR12 鑱氬悎鍜屾覆鏌擄紝浣嗕笉璋冪敤 GitHub 鍥炲啓銆?
     */
    @Test
    void shouldSkipGithubCommentWhenCommentWritingIsDisabled() {
        PrContextResolver resolver = mock(PrContextResolver.class);
        PrDiffProvider diffProvider = mock(PrDiffProvider.class);
        PrReviewContextLoader reviewContextLoader = mock(PrReviewContextLoader.class);
        ReviewFileSelectionService selectionService = mock(ReviewFileSelectionService.class);
        ReviewContextBudgetService budgetService = mock(ReviewContextBudgetService.class);
        ReviewExecutionInputAssembler inputAssembler = mock(ReviewExecutionInputAssembler.class);
        ReviewContextEnrichmentService enrichmentService = mock(ReviewContextEnrichmentService.class);
        RuleEngineRunner ruleEngineRunner = mock(RuleEngineRunner.class);
        LlmReviewRunner llmReviewRunner = mock(LlmReviewRunner.class);
        ReviewResultAggregator aggregator = mock(ReviewResultAggregator.class);
        ReviewCommentRenderer commentRenderer = mock(ReviewCommentRenderer.class);
        GithubPullRequestCommentClient commentClient = mock(GithubPullRequestCommentClient.class);
        TraceRecorder traceRecorder = new TraceRecorder();
        TraceReporter traceReporter = mock(TraceReporter.class);
        PrContext context = new PrContext("chinensdkcsdck", "PRysm", 3);
        PrDiff diff = new PrDiff(context, List.of());
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
        ReviewExecutionInput enrichedInput = new ReviewExecutionInput(
                context,
                diff,
                List.of(),
                new ContextStatus(ContextStatusCode.SKIPPED, "no files selected"),
                new PromptPayload("system", "user", "{}")
        );
        RuleEngineResult ruleResult = new RuleEngineResult(List.of(), "no rule findings");
        LlmReviewResult llmResult = new LlmReviewResult(List.of(), "no llm findings", null);
        ReviewAggregationResult aggregationResult = new ReviewAggregationResult(
                List.of(),
                0,
                0,
                0,
                ruleResult.getSummary(),
                llmResult.getSummary()
        );
        when(resolver.resolve()).thenReturn(context);
        when(diffProvider.fetch(context)).thenReturn(diff);
        when(reviewContextLoader.load(diff)).thenReturn(reviewContext);
        when(selectionService.select(reviewContext)).thenReturn(selectionResult);
        when(budgetService.allocate(selectionResult)).thenReturn(budgetResult);
        when(inputAssembler.assemble(budgetResult)).thenReturn(executionInput);
        when(enrichmentService.enrich(executionInput)).thenReturn(enrichedInput);
        when(ruleEngineRunner.run(enrichedInput)).thenReturn(ruleResult);
        when(llmReviewRunner.run(enrichedInput)).thenReturn(llmResult);
        when(aggregator.aggregate(
                org.mockito.ArgumentMatchers.eq(enrichedInput),
                org.mockito.ArgumentMatchers.eq(ruleResult),
                org.mockito.ArgumentMatchers.any(LlmReviewResult.class)
        )).thenReturn(aggregationResult);
        when(commentRenderer.renderFastReview(aggregationResult)).thenReturn("fast review comment");
        when(commentRenderer.render(aggregationResult)).thenReturn("review comment");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_ACTIONS", "true");
        PrReviewRunner runner = new PrReviewRunner(
                resolver,
                diffProvider,
                reviewContextLoader,
                selectionService,
                budgetService,
                inputAssembler,
                enrichmentService,
                ruleEngineRunner,
                llmReviewRunner,
                aggregator,
                commentRenderer,
                commentClient,
                new ReviewFindingQualityGate(),
                baselineOptimizationProperties(),
                baselineOptimizationPlanner(),
                new LlmOptimizationContext(),
                traceRecorder,
                traceReporter,
                environment,
                true,
                false,
                "test-model",
                "fast-model"
        );

        runner.run(new DefaultApplicationArguments());

        verify(enrichmentService).enrich(executionInput);
        verify(llmReviewRunner).run(enrichedInput);
        verify(aggregator).aggregate(
                org.mockito.ArgumentMatchers.eq(enrichedInput),
                org.mockito.ArgumentMatchers.eq(ruleResult),
                org.mockito.ArgumentMatchers.any(LlmReviewResult.class)
        );
        verify(commentRenderer, never()).renderFastReview(aggregationResult);
        verify(commentRenderer).render(aggregationResult);
        verify(commentClient, never()).createComment(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(traceReporter).report(org.mockito.ArgumentMatchers.any(TraceContext.class));
    }

    /**
     * 鎵ц闃舵澶辫触鏃讹紝涔熷簲閫氳繃 finally 杈撳嚭 trace summary銆?
     */
    @Test
    void shouldReportTraceWhenReviewFails() {
        PrContextResolver resolver = mock(PrContextResolver.class);
        PrDiffProvider diffProvider = mock(PrDiffProvider.class);
        PrReviewContextLoader reviewContextLoader = mock(PrReviewContextLoader.class);
        ReviewFileSelectionService selectionService = mock(ReviewFileSelectionService.class);
        ReviewContextBudgetService budgetService = mock(ReviewContextBudgetService.class);
        ReviewExecutionInputAssembler inputAssembler = mock(ReviewExecutionInputAssembler.class);
        ReviewContextEnrichmentService enrichmentService = mock(ReviewContextEnrichmentService.class);
        RuleEngineRunner ruleEngineRunner = mock(RuleEngineRunner.class);
        LlmReviewRunner llmReviewRunner = mock(LlmReviewRunner.class);
        ReviewResultAggregator aggregator = mock(ReviewResultAggregator.class);
        ReviewCommentRenderer commentRenderer = mock(ReviewCommentRenderer.class);
        GithubPullRequestCommentClient commentClient = mock(GithubPullRequestCommentClient.class);
        TraceRecorder traceRecorder = new TraceRecorder();
        TraceReporter traceReporter = mock(TraceReporter.class);
        RuntimeException failure = new IllegalStateException("diff failed");
        when(resolver.resolve()).thenReturn(new PrContext("chinensdkcsdck", "PRysm", 3));
        when(diffProvider.fetch(org.mockito.ArgumentMatchers.any())).thenThrow(failure);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GITHUB_ACTIONS", "true");
        PrReviewRunner runner = new PrReviewRunner(
                resolver,
                diffProvider,
                reviewContextLoader,
                selectionService,
                budgetService,
                inputAssembler,
                enrichmentService,
                ruleEngineRunner,
                llmReviewRunner,
                aggregator,
                commentRenderer,
                commentClient,
                new ReviewFindingQualityGate(),
                baselineOptimizationProperties(),
                baselineOptimizationPlanner(),
                new LlmOptimizationContext(),
                traceRecorder,
                traceReporter,
                environment,
                true,
                true,
                "test-model",
                "fast-model"
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments())
        );

        verify(traceReporter, times(1)).report(org.mockito.ArgumentMatchers.any(TraceContext.class));
    }

    private static LlmOptimizationProperties baselineOptimizationProperties() {
        return new LlmOptimizationProperties(
                "baseline",
                0,
                false,
                800,
                false,
                "fast_model",
                "qwen-turbo",
                false
        );
    }

    private static LlmOptimizationPlanner baselineOptimizationPlanner() {
        return new LlmOptimizationPlanner(baselineOptimizationProperties(), "test-model");
    }
}

