package com.hdg.prysm.agentic;

import java.util.Map;

public interface ReviewAgent {
    ReviewAgentRole role();
    Map<String, Object> execute(String runId);
}
