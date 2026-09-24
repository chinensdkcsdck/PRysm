package com.hdg.prysm.evaluation;

import com.hdg.prysm.execution.PromptPayload;
import com.hdg.prysm.execution.ReviewFinding;
import com.hdg.prysm.llm.LlmReviewClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class LlmReviewJudge {

    private final LlmReviewClient client;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public LlmReviewJudge(
            LlmReviewClient client,
            ObjectMapper objectMapper,
            @Value("${prysm.agentic.judge-enabled:false}") boolean enabled
    ) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public ReviewEvaluation.JudgeEvaluation evaluate(List<ReviewFinding> findings) {
        if (!enabled || findings.isEmpty()) return null;
        String evidence = findings.stream().limit(20)
                .map(finding -> finding.getFilePath() + ":" + finding.getLine() + " | "
                        + finding.getTitle() + " | " + finding.getMessage() + " | " + finding.getSuggestion())
                .reduce((left, right) -> left + "\n" + right).orElse("");
        PromptPayload prompt = new PromptPayload(
                "You are an independent code-review evaluator. Score only the supplied findings. Do not add new findings.",
                "Evaluate whether each finding has sufficient evidence and an actionable suggestion.\n" + evidence,
                "{\"evidenceScore\":1,\"actionabilityScore\":1,\"reason\":\"short explanation\"} where scores are integers from 1 to 5"
        );
        JsonNode root = objectMapper.readTree(client.review(prompt).getContent());
        return new ReviewEvaluation.JudgeEvaluation(
                bounded(root.path("evidenceScore").asInt(1)),
                bounded(root.path("actionabilityScore").asInt(1)),
                root.path("reason").asText("")
        );
    }

    private static int bounded(int score) { return Math.max(1, Math.min(5, score)); }
}
