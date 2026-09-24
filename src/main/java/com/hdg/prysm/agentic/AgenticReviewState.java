package com.hdg.prysm.agentic;

import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;
import com.hdg.prysm.execution.LlmReviewResult;

public class AgenticReviewState extends AgentState {

    public AgenticReviewState(Map<String, Object> initData) {
        super(initData);
    }

    public String runId() { return value("runId", ""); }

    @SuppressWarnings("unchecked")
    public List<String> selectedAgents() { return value("selectedAgents", List::of); }

    @SuppressWarnings("unchecked")
    public List<String> completedAgents() { return value("completedAgents", List::of); }

    public int toolCalls() { return value("toolCalls", 0); }
    public int usedTokens() { return value("usedTokens", 0); }
    public boolean budgetExceeded() { return value("budgetExceeded", false); }

    @SuppressWarnings("unchecked")
    public List<LlmReviewResult> agentResults() { return value("agentResults", List::of); }
}
