package com.hdg.prysm.result;

import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.diff.PrDiff;
import com.hdg.prysm.execution.ContextStatus;
import com.hdg.prysm.execution.ContextStatusCode;
import com.hdg.prysm.execution.LlmReviewResult;
import com.hdg.prysm.execution.PromptPayload;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewFinding;
import com.hdg.prysm.execution.RuleEngineResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewResultAggregatorTest {

    @Test
    void shouldMergeDeduplicateAndSortRuleAndLlmFindings() {
        ReviewFinding lowRule = finding("builtin", "LOW", "src/B.java", 30, "Debug output", "BUILTIN_DEBUG");
        ReviewFinding highLlm = finding("llm", "HIGH", "src/A.java", 20, "Null pointer risk", "LLM_REVIEW");
        ReviewFinding duplicateRule = finding("builtin", "HIGH", "src/A.java", 10, "Conflict marker", "BUILTIN_CONFLICT");
        ReviewFinding duplicateLlm = finding("llm", "HIGH", "src/A.java", 10, "Conflict marker", "BUILTIN_CONFLICT");
        RuleEngineResult ruleResult = new RuleEngineResult(List.of(lowRule, duplicateRule), "rules done");
        LlmReviewResult llmResult = new LlmReviewResult(List.of(highLlm, duplicateLlm), "llm done", "{}");
        ReviewResultAggregator aggregator = new ReviewResultAggregator(
                new ReviewFindingDeduplicator(),
                new ReviewFindingSorter()
        );

        ReviewAggregationResult result = aggregator.aggregate(input(), ruleResult, llmResult);

        assertEquals(3, result.getFindings().size());
        assertEquals(2, result.getRuleFindingCount());
        assertEquals(2, result.getLlmFindingCount());
        assertEquals(1, result.getDuplicateCount());
        assertEquals(duplicateRule, result.getFindings().get(0));
        assertEquals(highLlm, result.getFindings().get(1));
        assertEquals(lowRule, result.getFindings().get(2));
        assertEquals("rules done", result.getRuleSummary());
        assertEquals("llm done", result.getLlmSummary());
    }

    @Test
    void shouldHandleEmptyResults() {
        ReviewResultAggregator aggregator = new ReviewResultAggregator(
                new ReviewFindingDeduplicator(),
                new ReviewFindingSorter()
        );

        ReviewAggregationResult result = aggregator.aggregate(
                input(),
                new RuleEngineResult(List.of(), "no rule findings"),
                new LlmReviewResult(List.of(), "no llm findings", null)
        );

        assertEquals(0, result.getFindings().size());
        assertEquals(0, result.getDuplicateCount());
    }

    private static ReviewExecutionInput input() {
        PrContext context = new PrContext("owner", "repo", 12);
        return new ReviewExecutionInput(
                context,
                new PrDiff(context, List.of()),
                List.of(),
                new ContextStatus(ContextStatusCode.FULL, "ok"),
                new PromptPayload("system", "user", "{}")
        );
    }

    private static ReviewFinding finding(
            String source,
            String severity,
            String filePath,
            int line,
            String title,
            String ruleId
    ) {
        return new ReviewFinding(
                source,
                severity,
                filePath,
                line,
                line,
                "RIGHT",
                line,
                "RIGHT",
                title,
                "message",
                "suggestion",
                ruleId
        );
    }
}
