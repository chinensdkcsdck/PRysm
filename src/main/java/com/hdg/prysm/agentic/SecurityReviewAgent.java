package com.hdg.prysm.agentic;

import com.hdg.prysm.llm.LlmReviewRunner;
import com.hdg.prysm.mcp.AgenticRunStore;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SecurityReviewAgent extends PromptedReviewAgent {
    public SecurityReviewAgent(AgenticRunStore store, LlmReviewRunner runner) { super(store, runner); }
    @Override public ReviewAgentRole role() { return ReviewAgentRole.SECURITY; }
    @Override protected String instruction() {
        return "Review only security, secret, workflow and configuration risks. Require concrete code evidence and do not report style issues.";
    }
    @Override protected Set<String> acceptedCategories() { return Set.of("SECURITY", "SECRET", "WORKFLOW", "CONFIG"); }
}
