package com.hdg.prysm.runner;

import com.hdg.prysm.assembly.ReviewExecutionInputAssembler;
import com.hdg.prysm.budget.ReviewContextBudgetResult;
import com.hdg.prysm.budget.ReviewContextBudgetService;
import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.context.PrContextResolver;
import com.hdg.prysm.diff.PrDiff;
import com.hdg.prysm.diff.PrDiffProvider;
import com.hdg.prysm.enrichment.ReviewContextEnrichmentService;
import com.hdg.prysm.comment.ReviewCommentRenderer;
import com.hdg.prysm.execution.LlmReviewResult;
import com.hdg.prysm.execution.LlmTokenUsage;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.RuleEngineResult;
import com.hdg.prysm.github.GithubPullRequestCommentClient;
import com.hdg.prysm.llm.LlmReviewRunner;
import com.hdg.prysm.optimization.LlmOptimizationContext;
import com.hdg.prysm.optimization.LlmOptimizationDecision;
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
import com.hdg.prysm.trace.TraceSpan;
import com.hdg.prysm.trace.TraceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.OptionalLong;

/**
 * Prysm 的一次性任务入口。
 *
 * 在 Spring Boot 启动完成后触发 PR 审查流程。
 */
@Component
public class PrReviewRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PrReviewRunner.class);

    private final PrContextResolver prContextResolver;
    private final PrDiffProvider prDiffProvider;
    private final PrReviewContextLoader prReviewContextLoader;
    private final ReviewFileSelectionService reviewFileSelectionService;
    private final ReviewContextBudgetService reviewContextBudgetService;
    private final ReviewExecutionInputAssembler reviewExecutionInputAssembler;
    private final ReviewContextEnrichmentService reviewContextEnrichmentService;
    private final RuleEngineRunner ruleEngineRunner;
    private final LlmReviewRunner llmReviewRunner;
    private final ReviewResultAggregator reviewResultAggregator;
    private final ReviewCommentRenderer reviewCommentRenderer;
    private final GithubPullRequestCommentClient githubPullRequestCommentClient;
    private final ReviewFindingQualityGate reviewFindingQualityGate;
    private final LlmOptimizationProperties optimizationProperties;
    private final LlmOptimizationPlanner optimizationPlanner;
    private final LlmOptimizationContext optimizationContext;
    private final TraceRecorder traceRecorder;
    private final TraceReporter traceReporter;
    private final Environment environment;
    private final boolean runnerEnabled;
    private final boolean commentEnabled;
    private final String llmModel;
    private final String fastModel;
    private final ReviewMode reviewMode;

    /**
     * 注入 PR 上下文解析器、运行环境和 Runner 开关。
     */
    public PrReviewRunner(
            PrContextResolver prContextResolver,
            PrDiffProvider prDiffProvider,
            PrReviewContextLoader prReviewContextLoader,
            ReviewFileSelectionService reviewFileSelectionService,
            ReviewContextBudgetService reviewContextBudgetService,
            ReviewExecutionInputAssembler reviewExecutionInputAssembler,
            ReviewContextEnrichmentService reviewContextEnrichmentService,
            RuleEngineRunner ruleEngineRunner,
            LlmReviewRunner llmReviewRunner,
            ReviewResultAggregator reviewResultAggregator,
            ReviewCommentRenderer reviewCommentRenderer,
            GithubPullRequestCommentClient githubPullRequestCommentClient,
            ReviewFindingQualityGate reviewFindingQualityGate,
            LlmOptimizationProperties optimizationProperties,
            LlmOptimizationPlanner optimizationPlanner,
            LlmOptimizationContext optimizationContext,
            TraceRecorder traceRecorder,
            TraceReporter traceReporter,
            Environment environment,
            @Value("${prysm.runner.enabled:true}") boolean runnerEnabled,
            @Value("${prysm.comment.enabled:true}") boolean commentEnabled,
            @Value("${prysm.llm.model:unknown}") String llmModel,
            @Value("${prysm.optimization.fast-path.fast-model:qwen-turbo}") String fastModel,
            @Value("${prysm.review.mode:full}") String reviewMode
    ) {
        this.prContextResolver = prContextResolver;
        this.prDiffProvider = prDiffProvider;
        this.prReviewContextLoader = prReviewContextLoader;
        this.reviewFileSelectionService = reviewFileSelectionService;
        this.reviewContextBudgetService = reviewContextBudgetService;
        this.reviewExecutionInputAssembler = reviewExecutionInputAssembler;
        this.reviewContextEnrichmentService = reviewContextEnrichmentService;
        this.ruleEngineRunner = ruleEngineRunner;
        this.llmReviewRunner = llmReviewRunner;
        this.reviewResultAggregator = reviewResultAggregator;
        this.reviewCommentRenderer = reviewCommentRenderer;
        this.githubPullRequestCommentClient = githubPullRequestCommentClient;
        this.reviewFindingQualityGate = reviewFindingQualityGate;
        this.optimizationProperties = optimizationProperties;
        this.optimizationPlanner = optimizationPlanner;
        this.optimizationContext = optimizationContext;
        this.traceRecorder = traceRecorder;
        this.traceReporter = traceReporter;
        this.environment = environment;
        this.runnerEnabled = runnerEnabled;
        this.commentEnabled = commentEnabled;
        this.llmModel = llmModel;
        this.fastModel = fastModel;
        this.reviewMode = ReviewMode.from(reviewMode);
    }

    /**
     * 在 GitHub Actions 环境中解析 PR 上下文；本地运行或关闭 Runner 时跳过。
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!runnerEnabled) {
            log.info("Prysm runner is disabled.");
            return;
        }
        if (!Boolean.parseBoolean(environment.getProperty("GITHUB_ACTIONS", "false"))) {
            log.info("Prysm runner skipped because the application is not running in GitHub Actions.");
            return;
        }

        TraceContext trace = traceRecorder.createTrace();
        try {
            runReview(trace);
        } finally {
            traceReporter.report(trace);
        }
    }

    private void runReview(TraceContext trace) {
        PrContext context = traceRecorder.record(trace, "pr_context_parse", prContextResolver::resolve, span -> {
        });
        trace.getSpans().getLast()
                .put("owner", context.getOwner())
                .put("repository", context.getRepository())
                .put("pullRequestNumber", context.getPullRequestNumber());
        log.info(
                "Resolved pull request context: owner={}, repository={}, pullRequestNumber={}",
                context.getOwner(),
                context.getRepository(),
                context.getPullRequestNumber()
        );

        PrDiff diff = traceRecorder.record(trace, "github_diff_fetch", () -> prDiffProvider.fetch(context), span -> {
        });
        trace.getSpans().getLast()
                .put("changedFiles", diff.getFileCount())
                .put("additions", diff.getTotalAdditions())
                .put("deletions", diff.getTotalDeletions());
        log.info(
                "Fetched pull request diff: files={}, additions={}, deletions={}",
                diff.getFileCount(),
                diff.getTotalAdditions(),
                diff.getTotalDeletions()
        );

        PrReviewContext reviewContext = traceRecorder.record(trace, "snippet_build", () -> prReviewContextLoader.load(diff), span -> {
        });
        trace.getSpans().getLast()
                .put("fileCount", reviewContext.getFileCount())
                .put("filesWithSnippets", reviewContext.getFilesWithSnippetsCount());
        log.info(
                "Loaded review context: files={}, filesWithSnippets={}",
                reviewContext.getFileCount(),
                reviewContext.getFilesWithSnippetsCount()
        );

        ReviewFileSelectionResult selectionResult = traceRecorder.record(
                trace,
                "file_filter",
                () -> reviewFileSelectionService.select(reviewContext),
                span -> {
                }
        );
        trace.getSpans().getLast()
                .put("selectedFiles", selectionResult.getSelectedFiles().size())
                .put("rejectedFiles", selectionResult.getRejectedFiles().size());
        log.info(
                "Selected review files: selected={}, rejected={}",
                selectionResult.getSelectedFiles().size(),
                selectionResult.getRejectedFiles().size()
        );

        ReviewContextBudgetResult budgetResult = traceRecorder.record(
                trace,
                "context_budget",
                () -> reviewContextBudgetService.allocate(selectionResult),
                span -> {
                }
        );
        trace.getSpans().getLast()
                .put("selectedFiles", budgetResult.getSelectedFiles().size())
                .put("skippedFiles", budgetResult.getSkippedFiles().size())
                .put("usedCharacters", budgetResult.getUsedCharacters())
                .put("remainingCharacters", budgetResult.getRemainingCharacters())
                .put("truncated", budgetResult.isTruncated());
        log.info(
                "Allocated review context budget: selected={}, skipped={}, usedCharacters={}, remainingCharacters={}, truncated={}",
                budgetResult.getSelectedFiles().size(),
                budgetResult.getSkippedFiles().size(),
                budgetResult.getUsedCharacters(),
                budgetResult.getRemainingCharacters(),
                budgetResult.isTruncated()
        );

        ReviewExecutionInput executionInput = traceRecorder.record(
                trace,
                "review_input_assembly",
                () -> reviewExecutionInputAssembler.assemble(budgetResult),
                span -> {
                }
        );
        trace.getSpans().getLast()
                .put("files", executionInput.getFiles().size())
                .put("contextStatus", executionInput.getContextStatus().getCode().name())
                .put("promptCharacters", executionInput.getPromptPayload().getUserPrompt().length());
        log.info(
                "Assembled review execution input: files={}, contextStatus={}, promptCharacters={}",
                executionInput.getFiles().size(),
                executionInput.getContextStatus().getCode(),
                executionInput.getPromptPayload().getUserPrompt().length()
        );

        ReviewExecutionInput enrichedInput = traceRecorder.record(
                trace,
                "context_enrichment",
                () -> reviewContextEnrichmentService.enrich(executionInput),
                span -> {
                }
        );
        TraceSpan enrichmentSpan = trace.getSpans().getLast();
        int promptCharacters = enrichedInput.getPromptPayload().getUserPrompt().length();
        enrichmentSpan
                .put("files", enrichedInput.getFiles().size())
                .put("contextStatus", enrichedInput.getContextStatus().getCode().name())
                .put("promptCharacters", promptCharacters)
                .put("promptCharactersDelta", promptCharacters - executionInput.getPromptPayload().getUserPrompt().length());
        if (enrichedInput.getContextStatus().getCode().name().equals("LIMITED")) {
            enrichmentSpan.finish(TraceStatus.DEGRADED, enrichmentSpan.getEndedAt());
        }
        log.info(
                "Enriched review execution input: files={}, contextStatus={}, promptCharacters={}",
                enrichedInput.getFiles().size(),
                enrichedInput.getContextStatus().getCode(),
                enrichedInput.getPromptPayload().getUserPrompt().length()
        );

        RuleEngineResult ruleResult = traceRecorder.record(
                trace,
                "rule_engine",
                () -> ruleEngineRunner.run(enrichedInput),
                span -> {
                }
        );
        TraceSpan ruleSpan = trace.getSpans().getLast();
        ruleSpan.put("ruleFindings", ruleResult.getFindings().size());
        if (isDegradedSummary(ruleResult.getSummary())) {
            ruleSpan.finish(TraceStatus.DEGRADED, ruleSpan.getEndedAt());
        }
        log.info(
                "Rule engine completed: findings={}, summary={}",
                ruleResult.getFindings().size(),
                ruleResult.getSummary()
        );

        Long fastCommentId = null;
        if (reviewMode.runsFastReview()) {
            fastCommentId = commentEnabled
                    ? writeFastReviewComment(trace, enrichedInput, ruleResult)
                    : null;
            if (!reviewMode.runsDeepReview()) {
                log.info("Prysm review completed in {} mode.", reviewMode.configValue);
                return;
            }
        }

        LlmOptimizationDecision optimizationDecision = optimizationPlanner.plan(enrichedInput);
        optimizationContext.setCurrentDecision(optimizationDecision);
        LlmReviewResult rawLlmResult;
        rawLlmResult = traceRecorder.record(
                trace,
                "llm_review_deep",
                () -> llmReviewRunner.run(enrichedInput),
                span -> {
                }
        );
        int rawDeepFindings = rawLlmResult.getFindings().size();
        LlmReviewResult llmResult = reviewFindingQualityGate.filterDeepReview(enrichedInput, rawLlmResult);
        TraceSpan llmSpan = trace.getSpans().getLast();
        int llmPromptCharacters = enrichedInput.getPromptPayload().getUserPrompt().length();
        llmSpan
                .put("llmFindings", llmResult.getFindings().size())
                .put("rawLlmFindings", rawDeepFindings)
                .put("filteredLlmFindings", rawDeepFindings - llmResult.getFindings().size())
                .put("modelName", llmModel)
                .put("effectiveModel", optimizationDecision.getEffectiveModel())
                .put("optimizationGroup", optimizationProperties.getGroup())
                .put("rolloutPercent", optimizationProperties.getRolloutPercent())
                .put("maxOutputTokensEnabled", optimizationProperties.isMaxOutputTokensEnabled())
                .put("maxOutputTokens", optimizationProperties.getMaxOutputTokens())
                .put("fastPathEnabled", optimizationProperties.isFastPathEnabled())
                .put("fastPathMode", optimizationProperties.getFastPathMode())
                .put("fastModel", optimizationProperties.getFastModel())
                .put("fastPathMatched", optimizationDecision.isFastPathMatched())
                .put("fastPathReason", optimizationDecision.getFastPathReason())
                .put("compactPromptEnabled", optimizationProperties.isCompactPromptEnabled())
                .put("originalPromptCharacters", optimizationContext.getOriginalPromptCharacters())
                .put("compactPromptCharacters", optimizationContext.getCompactPromptCharacters())
                .put("promptCharactersSaved", optimizationContext.getPromptCharactersSaved())
                .put("promptCompactRatio", optimizationContext.getPromptCompactRatio())
                .put("promptCharacters", llmPromptCharacters)
                .put("estimatedPromptTokens", estimateTokens(llmPromptCharacters));
        putTokenUsage(llmSpan, llmResult);
        if (isSkippedLlmResult(llmResult)) {
            llmSpan.finish(TraceStatus.SKIPPED, llmSpan.getEndedAt());
        } else if (isDegradedSummary(llmResult.getSummary())) {
            llmSpan.finish(TraceStatus.DEGRADED, llmSpan.getEndedAt());
        }
        log.info(
                "Deep LLM review completed: findings={}, summary={}",
                llmResult.getFindings().size(),
                llmResult.getSummary()
        );

        ReviewAggregationResult aggregationResult = traceRecorder.record(
                trace,
                "result_aggregate",
                () -> reviewResultAggregator.aggregate(enrichedInput, ruleResult, llmResult),
                span -> {
                }
        );
        trace.getSpans().getLast()
                .put("findings", aggregationResult.getFindings().size())
                .put("ruleFindings", aggregationResult.getRuleFindingCount())
                .put("llmFindings", aggregationResult.getLlmFindingCount())
                .put("duplicatesRemoved", aggregationResult.getDuplicateCount())
                .put("severityCritical", severityCount(aggregationResult, "CRITICAL"))
                .put("severityHigh", severityCount(aggregationResult, "HIGH"))
                .put("severityMedium", severityCount(aggregationResult, "MEDIUM"))
                .put("severityLow", severityCount(aggregationResult, "LOW"))
                .put("severityInfo", severityCount(aggregationResult, "INFO"));
        log.info(
                "Aggregated review findings: findings={}, duplicatesRemoved={}",
                aggregationResult.getFindings().size(),
                aggregationResult.getDuplicateCount()
        );

        TraceSpan commentSpan = traceRecorder.start(trace, "github_comment_update");
        try {
            String commentBody = reviewCommentRenderer.render(aggregationResult);
            commentSpan
                    .put("commentLength", commentBody.length())
                    .put("enabled", commentEnabled);
            if (!commentEnabled) {
                commentSpan.put("commentWritten", false);
                commentSpan.finish(TraceStatus.SKIPPED, java.time.Instant.now());
                log.info("Pull request comment writing is disabled.");
                return;
            }
            if (fastCommentId == null) {
                OptionalLong existingCommentId = githubPullRequestCommentClient.findExistingReviewComment(enrichedInput.getPrContext());
                if (existingCommentId.isPresent()) {
                    fastCommentId = existingCommentId.getAsLong();
                    githubPullRequestCommentClient.updateComment(enrichedInput.getPrContext(), fastCommentId, commentBody);
                    commentSpan.put("commentReused", true);
                    commentSpan.put("commentUpdated", true);
                } else {
                    fastCommentId = githubPullRequestCommentClient.createComment(enrichedInput.getPrContext(), commentBody);
                    commentSpan.put("commentCreated", true);
                }
            } else {
                githubPullRequestCommentClient.updateComment(enrichedInput.getPrContext(), fastCommentId, commentBody);
                commentSpan.put("commentUpdated", true);
            }
            commentSpan.put("commentId", fastCommentId);
            commentSpan.put("commentWritten", true);
            commentSpan.finish(TraceStatus.SUCCESS, java.time.Instant.now());
            log.info("Updated aggregated review comment on pull request.");
        } catch (RuntimeException exception) {
            commentSpan.put("commentWritten", false);
            commentSpan.fail(exception, java.time.Instant.now());
            throw exception;
        }
    }

    private Long writeFastReviewComment(
            TraceContext trace,
            ReviewExecutionInput enrichedInput,
            RuleEngineResult ruleResult
    ) {
        LlmReviewResult rawFastLlmResult;
        optimizationContext.forceEffectiveModel(fastModel);
        try {
            rawFastLlmResult = traceRecorder.record(
                    trace,
                    "llm_review_fast",
                    () -> llmReviewRunner.run(enrichedInput),
                    span -> {
                    }
            );
        } finally {
            optimizationContext.clearForcedEffectiveModel();
        }
        int rawFastFindings = rawFastLlmResult.getFindings().size();
        LlmReviewResult fastLlmResult = reviewFindingQualityGate.filterFastReview(enrichedInput, rawFastLlmResult);
        TraceSpan fastLlmSpan = trace.getSpans().getLast();
        int promptCharacters = enrichedInput.getPromptPayload().getUserPrompt().length();
        fastLlmSpan
                .put("llmFindings", fastLlmResult.getFindings().size())
                .put("rawLlmFindings", rawFastFindings)
                .put("filteredLlmFindings", rawFastFindings - fastLlmResult.getFindings().size())
                .put("modelName", llmModel)
                .put("effectiveModel", fastModel)
                .put("optimizationGroup", "fast_comment")
                .put("promptCharacters", promptCharacters)
                .put("estimatedPromptTokens", estimateTokens(promptCharacters));
        putTokenUsage(fastLlmSpan, fastLlmResult);
        log.info(
                "Fast LLM review completed: findings={}, summary={}",
                fastLlmResult.getFindings().size(),
                fastLlmResult.getSummary()
        );

        ReviewAggregationResult fastAggregationResult = traceRecorder.record(
                trace,
                "result_aggregate_fast",
                () -> reviewResultAggregator.aggregate(enrichedInput, ruleResult, fastLlmResult),
                span -> {
                }
        );
        trace.getSpans().getLast()
                .put("findings", fastAggregationResult.getFindings().size())
                .put("ruleFindings", fastAggregationResult.getRuleFindingCount())
                .put("llmFindings", fastAggregationResult.getLlmFindingCount())
                .put("duplicatesRemoved", fastAggregationResult.getDuplicateCount());

        TraceSpan commentSpan = traceRecorder.start(trace, "github_comment_fast");
        try {
            String commentBody = reviewCommentRenderer.renderFastReview(fastAggregationResult);
            commentSpan
                    .put("commentLength", commentBody.length())
                    .put("enabled", commentEnabled);
            if (!commentEnabled) {
                commentSpan.put("commentWritten", false);
                commentSpan.finish(TraceStatus.SKIPPED, java.time.Instant.now());
                return null;
            }
            OptionalLong existingCommentId = githubPullRequestCommentClient.findExistingReviewComment(enrichedInput.getPrContext());
            long commentId;
            if (existingCommentId.isPresent()) {
                commentId = existingCommentId.getAsLong();
                githubPullRequestCommentClient.updateComment(enrichedInput.getPrContext(), commentId, commentBody);
                commentSpan.put("commentReused", true);
            } else {
                commentId = githubPullRequestCommentClient.createComment(enrichedInput.getPrContext(), commentBody);
                commentSpan.put("commentCreated", true);
            }
            commentSpan.put("commentId", commentId);
            commentSpan.put("commentWritten", true);
            commentSpan.finish(TraceStatus.SUCCESS, java.time.Instant.now());
            log.info("Wrote fast review comment to pull request.");
            return commentId;
        } catch (RuntimeException exception) {
            commentSpan.put("commentWritten", false);
            commentSpan.fail(exception, java.time.Instant.now());
            throw exception;
        }
    }

    private static int estimateTokens(int characters) {
        return Math.max(0, (int) Math.ceil(characters / 4.0));
    }

    private static void putTokenUsage(TraceSpan span, LlmReviewResult result) {
        LlmTokenUsage usage = result.getTokenUsage();
        if (usage == null) {
            span.put("tokenSource", "estimated");
            return;
        }

        span
                .put("tokenSource", "provider")
                .put("promptTokens", usage.getPromptTokens())
                .put("completionTokens", usage.getCompletionTokens())
                .put("totalTokens", usage.getTotalTokens());
    }

    private static boolean isSkippedLlmResult(LlmReviewResult result) {
        String summary = result.getSummary();
        return summary != null && (summary.contains("disabled") || summary.contains("skipped"));
    }

    private static boolean isDegradedSummary(String summary) {
        return summary != null && summary.toLowerCase(java.util.Locale.ROOT).contains("failed");
    }

    private static long severityCount(ReviewAggregationResult result, String severity) {
        return result.getFindings().stream()
                .filter(finding -> severity.equalsIgnoreCase(finding.getSeverity()))
                .count();
    }

    private enum ReviewMode {
        FAST("fast"),
        DEEP("deep"),
        FULL("full");

        private final String configValue;

        ReviewMode(String configValue) {
            this.configValue = configValue;
        }

        private boolean runsFastReview() {
            return this == FAST || this == FULL;
        }

        private boolean runsDeepReview() {
            return this == DEEP || this == FULL;
        }

        private static ReviewMode from(String value) {
            if (value == null || value.isBlank()) {
                return FULL;
            }
            String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
            for (ReviewMode mode : values()) {
                if (mode.configValue.equals(normalized)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unsupported Prysm review mode: " + value);
        }
    }
}
