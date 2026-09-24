package com.hdg.prysm.execution;

import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.diff.PrDiff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 规则检查、LLM Review 和结果回写共同使用的执行输入。
 *
 * 该对象是 A 线和 B 线的稳定对接边界。上游负责完成过滤、排序、
 * 预算分配和 prompt 上下文组装；下游只基于该对象执行审查和回写。
 */
public class ReviewExecutionInput implements java.io.Serializable {

    private final PrContext prContext;
    private final PrDiff diff;
    private final List<ReviewTargetFile> files;
    private final ContextStatus contextStatus;
    private final PromptPayload promptPayload;

    /**
     * 创建一次完整 Review 执行所需的输入。
     */
    public ReviewExecutionInput(
            PrContext prContext,
            PrDiff diff,
            List<ReviewTargetFile> files,
            ContextStatus contextStatus,
            PromptPayload promptPayload
    ) {
        if (prContext == null) {
            throw new IllegalArgumentException("Pull request context must not be null");
        }
        if (diff == null) {
            throw new IllegalArgumentException("Pull request diff must not be null");
        }
        if (files == null) {
            throw new IllegalArgumentException("Review target files must not be null");
        }
        if (files.stream().anyMatch(file -> file == null)) {
            throw new IllegalArgumentException("Review target files must not contain null values");
        }
        if (contextStatus == null) {
            throw new IllegalArgumentException("Context status must not be null");
        }
        if (promptPayload == null) {
            throw new IllegalArgumentException("Prompt payload must not be null");
        }

        this.prContext = prContext;
        this.diff = diff;
        this.files = Collections.unmodifiableList(new ArrayList<>(files));
        this.contextStatus = contextStatus;
        this.promptPayload = promptPayload;
    }

    /**
     * 返回当前审查对应的 PR 上下文。
     */
    public PrContext getPrContext() {
        return prContext;
    }

    /**
     * 返回原始 PR diff。
     */
    public PrDiff getDiff() {
        return diff;
    }

    /**
     * 返回已经完成过滤、排序和预算裁剪的目标文件列表。
     */
    public List<ReviewTargetFile> getFiles() {
        return files;
    }

    /**
     * 返回当前上下文是否足够支持 AI 审查。
     */
    public ContextStatus getContextStatus() {
        return contextStatus;
    }

    /**
     * 返回已经组装好的 prompt 载荷。
     */
    public PromptPayload getPromptPayload() {
        return promptPayload;
    }
}
