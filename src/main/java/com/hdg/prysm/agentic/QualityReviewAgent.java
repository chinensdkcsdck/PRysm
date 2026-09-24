package com.hdg.prysm.agentic;

import com.hdg.prysm.llm.LlmReviewRunner;
import com.hdg.prysm.mcp.AgenticRunStore;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class QualityReviewAgent extends PromptedReviewAgent {
    public QualityReviewAgent(AgenticRunStore store, LlmReviewRunner runner) { super(store, runner); }
    @Override public ReviewAgentRole role() { return ReviewAgentRole.QUALITY; }
    @Override protected String instruction() {
        return "Review only correctness, reliability and maintainability defects. Ignore documentation wording and require an actionable fix.";
    }
    @Override protected Set<String> acceptedCategories() { return Set.of("BUG", "MAINTAINABILITY"); }
}
