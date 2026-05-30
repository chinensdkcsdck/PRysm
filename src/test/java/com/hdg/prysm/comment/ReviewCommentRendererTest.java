package com.hdg.prysm.comment;

import com.hdg.prysm.execution.ReviewFinding;
import com.hdg.prysm.result.ReviewAggregationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewCommentRendererTest {

    @Test
    void shouldRenderGroupedReviewComment() {
        ReviewAggregationResult result = new ReviewAggregationResult(
                List.of(new ReviewFinding(
                        "builtin",
                        "HIGH",
                        "src/App.java",
                        12,
                        12,
                        "RIGHT",
                        12,
                        "RIGHT",
                        "Merge conflict marker found",
                        "The changed code contains a merge conflict marker.",
                        "Resolve the marker.",
                        "BUILTIN_CONFLICT_MARKER"
                )),
                1,
                0,
                0,
                "Built-in rules found 1 issue.",
                "LLM review skipped."
        );

        String markdown = new ReviewCommentRenderer().render(result);

        assertTrue(markdown.contains("## PRysm Review Result"));
        assertTrue(markdown.contains("Found 1 issue(s). Rule findings: 1, LLM findings: 0, duplicates removed: 0."));
        assertTrue(markdown.contains("### src/App.java"));
        assertTrue(markdown.contains("**[HIGH] Merge conflict marker found** (line 12)"));
        assertTrue(markdown.contains("Source: `builtin` / Rule: `BUILTIN_CONFLICT_MARKER`"));
        assertTrue(markdown.contains("Suggestion: Resolve the marker."));
    }

    @Test
    void shouldRenderEmptyReviewComment() {
        ReviewAggregationResult result = new ReviewAggregationResult(
                List.of(),
                0,
                0,
                0,
                "No rule findings.",
                "No LLM findings."
        );

        String markdown = new ReviewCommentRenderer().render(result);

        assertTrue(markdown.contains("Found 0 issue(s)."));
        assertTrue(markdown.contains("No actionable findings were reported."));
    }
}
