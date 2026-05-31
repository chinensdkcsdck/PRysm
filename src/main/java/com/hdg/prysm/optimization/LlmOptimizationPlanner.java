package com.hdg.prysm.optimization;

import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.execution.ReviewExecutionInput;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Computes per-review LLM optimization decisions.
 */
@Component
public class LlmOptimizationPlanner {

    private static final int SMALL_PR_MAX_FILES = 5;
    private static final int SMALL_PR_MAX_CHANGED_LINES = 80;
    private static final String FAST_MODEL_MODE = "fast_model";
    private static final String FAST_PATH_REASON = "small_doc_or_workflow_pr";

    private final LlmOptimizationProperties properties;
    private final String defaultModel;

    public LlmOptimizationPlanner(
            LlmOptimizationProperties properties,
            @Value("${prysm.llm.model:qwen-plus}") String defaultModel
    ) {
        if (properties == null) {
            throw new IllegalArgumentException("Optimization properties must not be null");
        }
        if (defaultModel == null || defaultModel.isBlank()) {
            throw new IllegalArgumentException("Default LLM model must not be blank");
        }
        this.properties = properties;
        this.defaultModel = defaultModel.trim();
    }

    public LlmOptimizationDecision plan(ReviewExecutionInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Review execution input must not be null");
        }
        if (!properties.isFastPathEnabled() || !FAST_MODEL_MODE.equals(properties.getFastPathMode())) {
            return LlmOptimizationDecision.baseline(defaultModel);
        }
        if (!matchesSmallDocOrWorkflowPr(input)) {
            return LlmOptimizationDecision.baseline(defaultModel);
        }
        return LlmOptimizationDecision.fastPath(defaultModel, properties.getFastModel(), FAST_PATH_REASON);
    }

    private static boolean matchesSmallDocOrWorkflowPr(ReviewExecutionInput input) {
        if (input.getDiff().getFileCount() == 0 || input.getDiff().getFileCount() > SMALL_PR_MAX_FILES) {
            return false;
        }
        int changedLines = input.getDiff().getTotalAdditions() + input.getDiff().getTotalDeletions();
        if (changedLines > SMALL_PR_MAX_CHANGED_LINES) {
            return false;
        }
        return input.getDiff().getChangedFiles().stream()
                .map(PrChangedFile::getFilename)
                .allMatch(LlmOptimizationPlanner::isDocConfigOrWorkflowFile);
    }

    private static boolean isDocConfigOrWorkflowFile(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String normalized = filename.replace('\\', '/').toLowerCase(Locale.ROOT);
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1);
        return baseName.startsWith("readme")
                || normalized.endsWith(".md")
                || normalized.startsWith("docs/")
                || normalized.endsWith(".yml")
                || normalized.endsWith(".yaml")
                || normalized.startsWith(".github/workflows/");
    }
}
