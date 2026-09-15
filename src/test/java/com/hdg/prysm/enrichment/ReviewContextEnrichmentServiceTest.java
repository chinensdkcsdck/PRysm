package com.hdg.prysm.enrichment;

import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.diff.PrChangedFileStatus;
import com.hdg.prysm.diff.PrDiff;
import com.hdg.prysm.execution.ContextStatus;
import com.hdg.prysm.execution.ContextStatusCode;
import com.hdg.prysm.execution.PromptPayload;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewTargetFile;
import com.hdg.prysm.review.PrReviewFileContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewContextEnrichmentServiceTest {

    /**
     * 应将 PR 标题、正文和 commit message 注入 user prompt。
     */
    @Test
    void shouldInjectPullRequestMetadataIntoPrompt() {
        ReviewContextEnrichmentService service = new ReviewContextEnrichmentService(
                context -> new PullRequestMetadata(
                        "Add review input",
                        "This PR assembles review input.",
                        List.of("feat: add input assembly", "test: add coverage"),
                        null
                ),
                1,
                1
        );

        ReviewExecutionInput enrichedInput = service.enrich(newInput(
                ContextStatusCode.FULL,
                "ready",
                List.of(newTargetFile(true)),
                "基础 prompt"
        ));

        String userPrompt = enrichedInput.getPromptPayload().getUserPrompt();
        assertEquals(ContextStatusCode.FULL, enrichedInput.getContextStatus().getCode());
        assertTrue(userPrompt.contains("扩展上下文"));
        assertTrue(userPrompt.contains("- PR 标题: Add review input"));
        assertTrue(userPrompt.contains("PR 正文"));
        assertTrue(userPrompt.contains("This PR assembles review input."));
        assertTrue(userPrompt.contains("- feat: add input assembly"));
        assertTrue(userPrompt.contains("基础 prompt"));
    }

    @Test
    void shouldInjectBoundedCrossFileContextIntoPrompt() {
        ReviewContextEnrichmentService service = new ReviewContextEnrichmentService(
                context -> new PullRequestMetadata("Title", "Body", List.of(), null),
                input -> new CrossFileContext(
                        "跨文件依赖上下文\n依赖片段 1 [定义] src/Dependency.java\n",
                        1,
                        1,
                        false
                ),
                1,
                1
        );

        ReviewExecutionInput enrichedInput = service.enrich(newInput(
                ContextStatusCode.FULL,
                "ready",
                List.of(newTargetFile(true)),
                "基础 prompt"
        ));

        assertTrue(enrichedInput.getPromptPayload().getUserPrompt().contains("跨文件依赖上下文"));
        assertTrue(enrichedInput.getPromptPayload().getUserPrompt().contains("src/Dependency.java"));
    }

    /**
     * 基础输入已被标记为 SKIPPED 时，应保留 SKIPPED 状态。
     */
    @Test
    void shouldKeepSkippedStatus() {
        AtomicBoolean metadataFetched = new AtomicBoolean(false);
        ReviewContextEnrichmentService service = new ReviewContextEnrichmentService(
                context -> {
                    metadataFetched.set(true);
                    return new PullRequestMetadata("Title", "Body", List.of("commit"), null);
                },
                1,
                1
        );

        ReviewExecutionInput enrichedInput = service.enrich(newInput(
                ContextStatusCode.SKIPPED,
                "没有文件进入 Review 执行输入",
                List.of(),
                "基础 prompt"
        ));

        assertEquals(ContextStatusCode.SKIPPED, enrichedInput.getContextStatus().getCode());
        assertEquals("没有文件进入 Review 执行输入", enrichedInput.getContextStatus().getReason());
        assertEquals(false, metadataFetched.get());
    }

    /**
     * 可审查文件数量不足时，应标记为 INSUFFICIENT，避免 LLM 硬审。
     */
    @Test
    void shouldMarkInsufficientWhenReviewableFilesAreMissing() {
        ReviewContextEnrichmentService service = new ReviewContextEnrichmentService(
                context -> new PullRequestMetadata("Title", "Body", List.of("commit"), null),
                1,
                1
        );

        ReviewExecutionInput enrichedInput = service.enrich(newInput(
                ContextStatusCode.FULL,
                "ready",
                List.of(newTargetFile(false)),
                "基础 prompt"
        ));

        assertEquals(ContextStatusCode.INSUFFICIENT, enrichedInput.getContextStatus().getCode());
        assertEquals("可审查文件数量不足", enrichedInput.getContextStatus().getReason());
    }

    /**
     * prompt 太短时，应标记为 INSUFFICIENT。
     */
    @Test
    void shouldMarkInsufficientWhenPromptIsTooSmall() {
        ReviewContextEnrichmentService service = new ReviewContextEnrichmentService(
                context -> new PullRequestMetadata(null, null, List.of(), null),
                1,
                1000
        );

        ReviewExecutionInput enrichedInput = service.enrich(newInput(
                ContextStatusCode.FULL,
                "ready",
                List.of(newTargetFile(true)),
                "short"
        ));

        assertEquals(ContextStatusCode.INSUFFICIENT, enrichedInput.getContextStatus().getCode());
        assertEquals("Review prompt 上下文过少", enrichedInput.getContextStatus().getReason());
    }

    /**
     * 缺少扩展上下文时，应降级为 LIMITED 但仍允许后续 LLM 审查。
     */
    @Test
    void shouldMarkLimitedWhenMetadataIsMissing() {
        ReviewContextEnrichmentService service = new ReviewContextEnrichmentService(
                context -> new PullRequestMetadata(null, null, List.of(), null),
                1,
                1
        );

        ReviewExecutionInput enrichedInput = service.enrich(newInput(
                ContextStatusCode.FULL,
                "ready",
                List.of(newTargetFile(true)),
                "基础 prompt"
        ));

        assertEquals(ContextStatusCode.LIMITED, enrichedInput.getContextStatus().getCode());
        assertTrue(enrichedInput.getContextStatus().canRunLlmReview());
        assertTrue(enrichedInput.getContextStatus().getReason().contains("未获取到 PR 扩展上下文"));
    }

    /**
     * 扩展上下文获取失败时，应降级为 LIMITED，避免打断后续 Review。
     */
    @Test
    void shouldMarkLimitedWhenMetadataFetchFails() {
        ReviewContextEnrichmentService service = new ReviewContextEnrichmentService(
                context -> {
                    throw new IllegalStateException("GitHub metadata unavailable");
                },
                1,
                1
        );

        ReviewExecutionInput enrichedInput = service.enrich(newInput(
                ContextStatusCode.FULL,
                "ready",
                List.of(newTargetFile(true)),
                "基础 prompt"
        ));

        assertEquals(ContextStatusCode.LIMITED, enrichedInput.getContextStatus().getCode());
        assertTrue(enrichedInput.getPromptPayload().getUserPrompt().contains("PR 扩展上下文获取失败"));
        assertTrue(enrichedInput.getContextStatus().canRunLlmReview());
    }

    /**
     * 空输入应直接失败。
     */
    @Test
    void shouldRejectNullInput() {
        ReviewContextEnrichmentService service = new ReviewContextEnrichmentService(
                context -> new PullRequestMetadata("Title", null, List.of(), null),
                1,
                1
        );

        assertThrows(IllegalArgumentException.class, () -> service.enrich(null));
    }

    /**
     * 创建测试用执行输入。
     */
    private ReviewExecutionInput newInput(
            ContextStatusCode statusCode,
            String reason,
            List<ReviewTargetFile> targetFiles,
            String userPrompt
    ) {
        PrContext context = new PrContext("owner", "repo", 9);
        PrDiff diff = new PrDiff(
                context,
                targetFiles.stream()
                        .map(ReviewTargetFile::getChangedFile)
                        .toList()
        );
        return new ReviewExecutionInput(
                context,
                diff,
                targetFiles,
                new ContextStatus(statusCode, reason),
                new PromptPayload("system", userPrompt, "{}")
        );
    }

    /**
     * 创建测试用目标文件。
     */
    private ReviewTargetFile newTargetFile(boolean selected) {
        PrChangedFile changedFile = new PrChangedFile(
                "src/App.java",
                PrChangedFileStatus.MODIFIED,
                1,
                0,
                "@@ -1,1 +1,1 @@\n+class App {}"
        );
        return new ReviewTargetFile(
                changedFile,
                List.of(new PrReviewFileContext.Snippet(1, 1, "class App {}")),
                10,
                selected,
                selected ? "selected" : "skipped"
        );
    }
}
