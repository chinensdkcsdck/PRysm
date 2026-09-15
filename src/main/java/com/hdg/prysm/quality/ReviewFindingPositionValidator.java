package com.hdg.prysm.quality;

import com.hdg.prysm.execution.LlmReviewResult;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Validates model locations against the exact target-side diff before findings are published.
 */
@Component
public class ReviewFindingPositionValidator {

    public LlmReviewResult validate(ReviewExecutionInput input, LlmReviewResult result) {
        if (input == null) {
            throw new IllegalArgumentException("Review execution input must not be null");
        }
        if (result == null) {
            throw new IllegalArgumentException("LLM review result must not be null");
        }

        ReviewableLineIndex index = new ReviewableLineIndex(input.getDiff());
        List<ReviewFinding> validated = new ArrayList<>();
        int relocated = 0;
        int downgraded = 0;
        int rejected = 0;

        for (ReviewFinding finding : result.getFindings()) {
            if (!index.containsFile(finding.getFilePath())) {
                rejected++;
                continue;
            }

            String path = index.canonicalPath(finding.getFilePath());
            Integer requestedLine = primaryLine(finding);
            if (index.isReviewable(path, requestedLine)) {
                validated.add(finding.withLocation(path, requestedLine));
                continue;
            }

            OptionalInt relocatedLine = index.relocate(path, finding.getCodeSnippet());
            if (relocatedLine.isPresent()) {
                validated.add(finding.withLocation(path, relocatedLine.getAsInt()));
                relocated++;
                continue;
            }

            validated.add(finding.withLocation(path, null));
            downgraded++;
        }

        String summary = validationSummary(result.getSummary(), relocated, downgraded, rejected);
        return new LlmReviewResult(validated, summary, result.getRawResponse(), result.getTokenUsage());
    }

    private static Integer primaryLine(ReviewFinding finding) {
        if (finding.getLine() != null) {
            return finding.getLine();
        }
        if (finding.getEndLine() != null) {
            return finding.getEndLine();
        }
        return finding.getStartLine();
    }

    private static String validationSummary(String original, int relocated, int downgraded, int rejected) {
        if (relocated == 0 && downgraded == 0 && rejected == 0) {
            return original;
        }
        String prefix = original == null || original.isBlank() ? "" : original + " ";
        return prefix + "位置校验：重新定位 " + relocated
                + " 条，降级为文件级 " + downgraded
                + " 条，拒绝未知文件 " + rejected + " 条。";
    }
}
