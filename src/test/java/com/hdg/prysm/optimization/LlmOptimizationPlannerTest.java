package com.hdg.prysm.optimization;

import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.diff.PrChangedFileStatus;
import com.hdg.prysm.diff.PrDiff;
import com.hdg.prysm.execution.ContextStatus;
import com.hdg.prysm.execution.ContextStatusCode;
import com.hdg.prysm.execution.PromptPayload;
import com.hdg.prysm.execution.ReviewExecutionInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmOptimizationPlannerTest {

    @Test
    void shouldUseBaselineWhenFastPathIsDisabled() {
        LlmOptimizationPlanner planner = new LlmOptimizationPlanner(
                properties(false),
                "qwen-plus"
        );

        LlmOptimizationDecision decision = planner.plan(input(file("README.md", 2, 1)));

        assertFalse(decision.isFastPathMatched());
        assertEquals("qwen-plus", decision.getEffectiveModel());
    }

    @Test
    void shouldUseFastModelForSmallDocumentationPullRequest() {
        LlmOptimizationPlanner planner = new LlmOptimizationPlanner(
                properties(true),
                "qwen-plus"
        );

        LlmOptimizationDecision decision = planner.plan(input(
                file("README.md", 2, 1),
                file(".github/workflows/prysm-review.yml", 1, 0)
        ));

        assertTrue(decision.isFastPathMatched());
        assertEquals("small_doc_or_workflow_pr", decision.getFastPathReason());
        assertEquals("qwen-plus", decision.getOriginalModel());
        assertEquals("qwen-turbo", decision.getEffectiveModel());
    }

    @Test
    void shouldKeepDefaultModelForCodePullRequest() {
        LlmOptimizationPlanner planner = new LlmOptimizationPlanner(
                properties(true),
                "qwen-plus"
        );

        LlmOptimizationDecision decision = planner.plan(input(file("src/main/java/App.java", 2, 1)));

        assertFalse(decision.isFastPathMatched());
        assertEquals("qwen-plus", decision.getEffectiveModel());
    }

    @Test
    void shouldKeepDefaultModelForLargeDocumentationPullRequest() {
        LlmOptimizationPlanner planner = new LlmOptimizationPlanner(
                properties(true),
                "qwen-plus"
        );

        LlmOptimizationDecision decision = planner.plan(input(file("docs/guide.md", 31, 0)));

        assertFalse(decision.isFastPathMatched());
        assertEquals("qwen-plus", decision.getEffectiveModel());
    }

    private static LlmOptimizationProperties properties(boolean fastPathEnabled) {
        return new LlmOptimizationProperties(
                "exp_2_fast_path",
                0,
                false,
                800,
                fastPathEnabled,
                "fast_model",
                "qwen-turbo",
                false
        );
    }

    private static ReviewExecutionInput input(PrChangedFile... files) {
        PrContext context = new PrContext("owner", "repo", 1);
        PrDiff diff = new PrDiff(context, List.of(files));
        return new ReviewExecutionInput(
                context,
                diff,
                List.of(),
                new ContextStatus(ContextStatusCode.FULL, "ready"),
                new PromptPayload("system", "user", "{}")
        );
    }

    private static PrChangedFile file(String filename, int additions, int deletions) {
        return new PrChangedFile(
                filename,
                PrChangedFileStatus.MODIFIED,
                additions,
                deletions,
                "@@ -1,1 +1,1 @@"
        );
    }
}
