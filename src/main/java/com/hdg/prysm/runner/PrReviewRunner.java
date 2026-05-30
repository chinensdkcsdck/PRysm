package com.hdg.prysm.runner;

import com.hdg.prysm.assembly.ReviewExecutionInputAssembler;
import com.hdg.prysm.budget.ReviewContextBudgetResult;
import com.hdg.prysm.budget.ReviewContextBudgetService;
import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.context.PrContextResolver;
import com.hdg.prysm.diff.PrDiff;
import com.hdg.prysm.diff.PrDiffProvider;
import com.hdg.prysm.comment.ReviewCommentRenderer;
import com.hdg.prysm.execution.LlmReviewResult;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.RuleEngineResult;
import com.hdg.prysm.github.GithubPullRequestCommentClient;
import com.hdg.prysm.llm.LlmReviewRunner;
import com.hdg.prysm.review.PrReviewContext;
import com.hdg.prysm.review.PrReviewContextLoader;
import com.hdg.prysm.result.ReviewAggregationResult;
import com.hdg.prysm.result.ReviewResultAggregator;
import com.hdg.prysm.rule.RuleEngineRunner;
import com.hdg.prysm.selection.ReviewFileSelectionResult;
import com.hdg.prysm.selection.ReviewFileSelectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

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
    private final RuleEngineRunner ruleEngineRunner;
    private final LlmReviewRunner llmReviewRunner;
    private final ReviewResultAggregator reviewResultAggregator;
    private final ReviewCommentRenderer reviewCommentRenderer;
    private final GithubPullRequestCommentClient githubPullRequestCommentClient;
    private final Environment environment;
    private final boolean runnerEnabled;
    private final boolean commentEnabled;

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
            RuleEngineRunner ruleEngineRunner,
            LlmReviewRunner llmReviewRunner,
            ReviewResultAggregator reviewResultAggregator,
            ReviewCommentRenderer reviewCommentRenderer,
            GithubPullRequestCommentClient githubPullRequestCommentClient,
            Environment environment,
            @Value("${prysm.runner.enabled:true}") boolean runnerEnabled,
            @Value("${prysm.comment.enabled:true}") boolean commentEnabled
    ) {
        this.prContextResolver = prContextResolver;
        this.prDiffProvider = prDiffProvider;
        this.prReviewContextLoader = prReviewContextLoader;
        this.reviewFileSelectionService = reviewFileSelectionService;
        this.reviewContextBudgetService = reviewContextBudgetService;
        this.reviewExecutionInputAssembler = reviewExecutionInputAssembler;
        this.ruleEngineRunner = ruleEngineRunner;
        this.llmReviewRunner = llmReviewRunner;
        this.reviewResultAggregator = reviewResultAggregator;
        this.reviewCommentRenderer = reviewCommentRenderer;
        this.githubPullRequestCommentClient = githubPullRequestCommentClient;
        this.environment = environment;
        this.runnerEnabled = runnerEnabled;
        this.commentEnabled = commentEnabled;
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

        PrContext context = prContextResolver.resolve();
        log.info(
                "Resolved pull request context: owner={}, repository={}, pullRequestNumber={}",
                context.getOwner(),
                context.getRepository(),
                context.getPullRequestNumber()
        );

        PrDiff diff = prDiffProvider.fetch(context);
        log.info(
                "Fetched pull request diff: files={}, additions={}, deletions={}",
                diff.getFileCount(),
                diff.getTotalAdditions(),
                diff.getTotalDeletions()
        );

        PrReviewContext reviewContext = prReviewContextLoader.load(diff);
        log.info(
                "Loaded review context: files={}, filesWithSnippets={}",
                reviewContext.getFileCount(),
                reviewContext.getFilesWithSnippetsCount()
        );

        ReviewFileSelectionResult selectionResult = reviewFileSelectionService.select(reviewContext);
        log.info(
                "Selected review files: selected={}, rejected={}",
                selectionResult.getSelectedFiles().size(),
                selectionResult.getRejectedFiles().size()
        );

        ReviewContextBudgetResult budgetResult = reviewContextBudgetService.allocate(selectionResult);
        log.info(
                "Allocated review context budget: selected={}, skipped={}, usedCharacters={}, remainingCharacters={}, truncated={}",
                budgetResult.getSelectedFiles().size(),
                budgetResult.getSkippedFiles().size(),
                budgetResult.getUsedCharacters(),
                budgetResult.getRemainingCharacters(),
                budgetResult.isTruncated()
        );

        ReviewExecutionInput executionInput = reviewExecutionInputAssembler.assemble(budgetResult);
        log.info(
                "Assembled review execution input: files={}, contextStatus={}, promptCharacters={}",
                executionInput.getFiles().size(),
                executionInput.getContextStatus().getCode(),
                executionInput.getPromptPayload().getUserPrompt().length()
        );

        RuleEngineResult ruleResult = ruleEngineRunner.run(executionInput);
        log.info(
                "Rule engine completed: findings={}, summary={}",
                ruleResult.getFindings().size(),
                ruleResult.getSummary()
        );

        LlmReviewResult llmResult = llmReviewRunner.run(executionInput);
        log.info(
                "LLM review completed: findings={}, summary={}",
                llmResult.getFindings().size(),
                llmResult.getSummary()
        );

        ReviewAggregationResult aggregationResult = reviewResultAggregator.aggregate(
                executionInput,
                ruleResult,
                llmResult
        );
        log.info(
                "Aggregated review findings: findings={}, duplicatesRemoved={}",
                aggregationResult.getFindings().size(),
                aggregationResult.getDuplicateCount()
        );

        String commentBody = reviewCommentRenderer.render(aggregationResult);
        if (!commentEnabled) {
            log.info("Pull request comment writing is disabled.");
            return;
        }

        githubPullRequestCommentClient.createComment(executionInput.getPrContext(), commentBody);
        log.info("Wrote aggregated review comment to pull request.");
    }
}
