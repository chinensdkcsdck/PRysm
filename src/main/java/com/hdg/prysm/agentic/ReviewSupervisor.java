package com.hdg.prysm.agentic;

import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.RuleEngineResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class ReviewSupervisor {

    public List<ReviewAgentRole> plan(ReviewExecutionInput input, RuleEngineResult rules) {
        List<ReviewAgentRole> roles = new ArrayList<>();
        roles.add(ReviewAgentRole.CONTEXT);
        boolean documentationOnly = input.getFiles().stream()
                .allMatch(file -> isDocumentation(file.getChangedFile().getFilename()));
        boolean securityRelevant = rules.getFindings().stream().anyMatch(this::isSecurityFinding)
                || input.getFiles().stream().anyMatch(file -> isSecurityPath(file.getChangedFile().getFilename()));
        boolean testChanged = input.getFiles().stream().anyMatch(file -> isTestPath(file.getChangedFile().getFilename()));

        if (securityRelevant) roles.add(ReviewAgentRole.SECURITY);
        if (!documentationOnly) roles.add(ReviewAgentRole.QUALITY);
        if (!documentationOnly || testChanged) roles.add(ReviewAgentRole.TEST);
        return List.copyOf(roles);
    }

    private boolean isSecurityFinding(com.hdg.prysm.execution.ReviewFinding finding) {
        String category = normalize(finding.getCategory());
        return category.contains("SECURITY") || category.contains("SECRET")
                || category.contains("CONFIG") || category.contains("WORKFLOW");
    }

    private boolean isDocumentation(String path) {
        String normalized = normalizePath(path);
        return normalized.endsWith(".md") || normalized.startsWith("docs/");
    }

    private boolean isSecurityPath(String path) {
        String normalized = normalizePath(path);
        return normalized.contains("security") || normalized.contains("auth")
                || normalized.endsWith(".yml") || normalized.endsWith(".yaml")
                || normalized.contains(".github/workflows/");
    }

    private boolean isTestPath(String path) {
        String normalized = normalizePath(path);
        return normalized.contains("/test/") || normalized.endsWith("test.java");
    }

    private static String normalize(String value) { return value == null ? "" : value.toUpperCase(Locale.ROOT); }
    private static String normalizePath(String value) { return value == null ? "" : value.replace('\\', '/').toLowerCase(Locale.ROOT); }
}
