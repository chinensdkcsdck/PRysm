package com.hdg.prysm.llm;

import com.hdg.prysm.execution.LlmReviewResult;
import com.hdg.prysm.execution.ReviewExecutionInput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PR11 的 LLM Review 执行入口。
 *
 * 该类只消费 ReviewExecutionInput，不重新组装 prompt，也不聚合规则引擎结果。
 */
@Component
public class LlmReviewRunner {

    private final LlmReviewClient client;
    private final LlmReviewResponseParser parser;
    private final boolean enabled;

    /**
     * 注入模型客户端、响应解析器和 LLM Review 开关。
     */
    @Autowired
    public LlmReviewRunner(
            LlmReviewClient client,
            LlmReviewResponseParser parser,
            @Value("${prysm.llm.enabled:true}") boolean enabled
    ) {
        if (client == null) {
            throw new IllegalArgumentException("LLM review client must not be null");
        }
        if (parser == null) {
            throw new IllegalArgumentException("LLM response parser must not be null");
        }

        this.client = client;
        this.parser = parser;
        this.enabled = enabled;
    }

    /**
     * 执行 LLM Review，并将模型输出转换成统一的 LlmReviewResult。
     */
    public LlmReviewResult run(ReviewExecutionInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Review execution input must not be null");
        }
        if (!enabled) {
            return new LlmReviewResult(List.of(), "LLM review is disabled.", null);
        }
        if (!input.getContextStatus().canRunLlmReview()) {
            return new LlmReviewResult(
                    List.of(),
                    skippedSummary(input.getContextStatus().getReason()),
                    null
            );
        }

        try {
            LlmReviewClientResponse response = client.review(input.getPromptPayload());
            return parser.parse(response.getContent()).withTokenUsage(response.getTokenUsage());
        } catch (RuntimeException exception) {
            return new LlmReviewResult(List.of(), "LLM review failed: " + exception.getMessage(), null);
        }
    }

    /**
     * 生成上下文不足时的跳过说明。
     */
    private static String skippedSummary(String reason) {
        if (reason == null || reason.isBlank()) {
            return "LLM review skipped because context is not sufficient.";
        }
        return "LLM review skipped because context is not sufficient: " + reason;
    }
}
