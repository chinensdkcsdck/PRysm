package com.hdg.prysm.rule;

import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.RuleEngineResult;

/**
 * 单个确定性规则引擎的统一执行接口。
 */
public interface RuleEngine {

    /**
     * 基于已经组装好的 Review 执行输入运行规则检查。
     */
    RuleEngineResult run(ReviewExecutionInput input);
}
