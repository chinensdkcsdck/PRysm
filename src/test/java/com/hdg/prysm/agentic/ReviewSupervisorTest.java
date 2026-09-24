package com.hdg.prysm.agentic;

import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewTargetFile;
import com.hdg.prysm.execution.RuleEngineResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewSupervisorTest {

    private final ReviewSupervisor supervisor = new ReviewSupervisor();

    @Test
    void routesSourceChangesToQualityAndTestAgents() {
        ReviewExecutionInput input = inputWithPath("src/main/java/example/PaymentService.java");

        List<ReviewAgentRole> roles = supervisor.plan(input, new RuleEngineResult(List.of(), "none"));

        assertThat(roles).containsExactly(
                ReviewAgentRole.CONTEXT,
                ReviewAgentRole.QUALITY,
                ReviewAgentRole.TEST
        );
    }

    @Test
    void routesWorkflowChangesToSecurityAgent() {
        ReviewExecutionInput input = inputWithPath(".github/workflows/release.yml");

        List<ReviewAgentRole> roles = supervisor.plan(input, new RuleEngineResult(List.of(), "none"));

        assertThat(roles).contains(ReviewAgentRole.CONTEXT, ReviewAgentRole.SECURITY, ReviewAgentRole.QUALITY);
    }

    @Test
    void skipsSpecializedAgentsForDocumentationOnlyChanges() {
        ReviewExecutionInput input = inputWithPath("docs/setup.md");

        List<ReviewAgentRole> roles = supervisor.plan(input, new RuleEngineResult(List.of(), "none"));

        assertThat(roles).containsExactly(ReviewAgentRole.CONTEXT);
    }

    private static ReviewExecutionInput inputWithPath(String path) {
        PrChangedFile changedFile = mock(PrChangedFile.class);
        when(changedFile.getFilename()).thenReturn(path);
        ReviewTargetFile targetFile = mock(ReviewTargetFile.class);
        when(targetFile.getChangedFile()).thenReturn(changedFile);
        ReviewExecutionInput input = mock(ReviewExecutionInput.class);
        when(input.getFiles()).thenReturn(List.of(targetFile));
        return input;
    }
}
