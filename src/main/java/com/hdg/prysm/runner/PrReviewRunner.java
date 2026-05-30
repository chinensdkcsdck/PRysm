package com.hdg.prysm.runner;

import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.context.PrContextResolver;
import com.hdg.prysm.diff.PrDiff;
import com.hdg.prysm.diff.PrDiffProvider;
import com.hdg.prysm.review.PrReviewContext;
import com.hdg.prysm.review.PrReviewContextLoader;
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
    private final Environment environment;
    private final boolean runnerEnabled;

    /**
     * 注入 PR 上下文解析器、运行环境和 Runner 开关。
     */
    public PrReviewRunner(
            PrContextResolver prContextResolver,
            PrDiffProvider prDiffProvider,
            PrReviewContextLoader prReviewContextLoader,
            Environment environment,
            @Value("${prysm.runner.enabled:true}") boolean runnerEnabled
    ) {
        this.prContextResolver = prContextResolver;
        this.prDiffProvider = prDiffProvider;
        this.prReviewContextLoader = prReviewContextLoader;
        this.environment = environment;
        this.runnerEnabled = runnerEnabled;
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
    }
}
