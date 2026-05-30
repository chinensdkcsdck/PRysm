package com.hdg.prysm.result;

import com.hdg.prysm.execution.LlmReviewResult;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewFinding;
import com.hdg.prysm.execution.RuleEngineResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * PR12 aggregation entry point.
 */
@Component
public class ReviewResultAggregator {

    private final ReviewFindingDeduplicator deduplicator;
    private final ReviewFindingSorter sorter;

    @Autowired
    public ReviewResultAggregator(
            ReviewFindingDeduplicator deduplicator,
            ReviewFindingSorter sorter
    ) {
        if (deduplicator == null) {
            throw new IllegalArgumentException("Review finding deduplicator must not be null");
        }
        if (sorter == null) {
            throw new IllegalArgumentException("Review finding sorter must not be null");
        }

        this.deduplicator = deduplicator;
        this.sorter = sorter;
    }

    public ReviewAggregationResult aggregate(
            ReviewExecutionInput input,
            RuleEngineResult ruleResult,
            LlmReviewResult llmResult
    ) {
        if (input == null) {
            throw new IllegalArgumentException("Review execution input must not be null");
        }
        if (ruleResult == null) {
            throw new IllegalArgumentException("Rule engine result must not be null");
        }
        if (llmResult == null) {
            throw new IllegalArgumentException("LLM review result must not be null");
        }

        List<ReviewFinding> combined = new ArrayList<>();
        combined.addAll(ruleResult.getFindings());
        combined.addAll(llmResult.getFindings());

        ReviewFindingDeduplicator.DeduplicationResult deduplicated = deduplicator.deduplicate(combined);
        List<ReviewFinding> sorted = sorter.sort(deduplicated.findings());
        return new ReviewAggregationResult(
                sorted,
                ruleResult.getFindings().size(),
                llmResult.getFindings().size(),
                deduplicated.duplicateCount(),
                ruleResult.getSummary(),
                llmResult.getSummary()
        );
    }
}
