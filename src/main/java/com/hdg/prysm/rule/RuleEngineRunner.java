package com.hdg.prysm.rule;

import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewFinding;
import com.hdg.prysm.execution.ReviewTargetFile;
import com.hdg.prysm.execution.RuleEngineResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * PR10 的规则引擎执行入口。
 *
 * 该类只消费 ReviewExecutionInput，不重新做文件过滤、优先级排序、预算分配或上下文组装。
 */
@Component
public class RuleEngineRunner {

    private final List<RuleEngine> ruleEngines;
    private final boolean enabled;

    /**
     * 注入所有可用规则引擎和规则检查开关。
     */
    @Autowired
    public RuleEngineRunner(
            List<RuleEngine> ruleEngines,
            @Value("${prysm.rule.enabled:true}") boolean enabled
    ) {
        this.ruleEngines = ruleEngines == null ? List.of() : List.copyOf(ruleEngines);
        this.enabled = enabled;
    }

    /**
     * 执行所有已注册的规则引擎，并合并成统一的规则结果。
     */
    public RuleEngineResult run(ReviewExecutionInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Review execution input must not be null");
        }
        if (!enabled) {
            return new RuleEngineResult(List.of(), "Rule engine is disabled.");
        }

        List<ReviewTargetFile> selectedFiles = selectedFiles(input);
        if (selectedFiles.isEmpty()) {
            return new RuleEngineResult(List.of(), "No selected files for rule engine review.");
        }
        if (ruleEngines.isEmpty()) {
            return new RuleEngineResult(List.of(), "No rule engines are configured.");
        }

        ReviewExecutionInput selectedInput = new ReviewExecutionInput(
                input.getPrContext(),
                input.getDiff(),
                selectedFiles,
                input.getContextStatus(),
                input.getPromptPayload()
        );

        List<ReviewFinding> findings = new ArrayList<>();
        StringJoiner summary = new StringJoiner(" ");
        for (RuleEngine ruleEngine : ruleEngines) {
            runRuleEngine(ruleEngine, selectedInput, findings, summary);
        }

        return new RuleEngineResult(findings, summary.toString());
    }

    /**
     * 筛选最终进入规则检查阶段的文件，并保留上游已经确定的顺序。
     */
    private static List<ReviewTargetFile> selectedFiles(ReviewExecutionInput input) {
        return input.getFiles().stream()
                .filter(ReviewTargetFile::isSelected)
                .toList();
    }

    /**
     * 执行单个规则引擎；单个引擎失败时降级记录，不中断整个 Review。
     */
    private static void runRuleEngine(
            RuleEngine ruleEngine,
            ReviewExecutionInput input,
            List<ReviewFinding> findings,
            StringJoiner summary
    ) {
        try {
            RuleEngineResult result = ruleEngine.run(input);
            findings.addAll(result.getFindings());
            if (result.getSummary() != null && !result.getSummary().isBlank()) {
                summary.add(result.getSummary());
            }
        } catch (RuntimeException exception) {
            summary.add("Rule engine failed: " + exception.getMessage());
        }
    }
}
