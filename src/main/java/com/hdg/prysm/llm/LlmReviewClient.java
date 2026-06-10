package com.hdg.prysm.llm;

import com.hdg.prysm.execution.PromptPayload;

/**
 * LLM Review 模型调用接口。
 */
public interface LlmReviewClient {

    /**
     * 基于 prompt 载荷调用模型，并返回模型生成的原始 JSON 文本。
     */
    LlmReviewClientResponse review(PromptPayload promptPayload);
}
