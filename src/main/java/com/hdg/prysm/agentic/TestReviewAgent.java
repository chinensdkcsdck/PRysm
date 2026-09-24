package com.hdg.prysm.agentic;

import com.hdg.prysm.llm.LlmReviewRunner;
import com.hdg.prysm.mcp.AgenticRunStore;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class TestReviewAgent extends PromptedReviewAgent {
    public TestReviewAgent(AgenticRunStore store, LlmReviewRunner runner) { super(store, runner); }
    @Override public ReviewAgentRole role() { return ReviewAgentRole.TEST; }
    @Override protected String instruction() {
        return "Review only missing or invalid tests and CI impact caused by this change. Do not request tests without naming the uncovered behavior.";
    }
    @Override protected Set<String> acceptedCategories() { return Set.of("TEST"); }
}
