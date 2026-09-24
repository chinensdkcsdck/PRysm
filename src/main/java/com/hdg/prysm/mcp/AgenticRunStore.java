package com.hdg.prysm.mcp;

import com.hdg.prysm.agentic.AgenticExecutionBudget;
import com.hdg.prysm.execution.LlmReviewResult;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.RuleEngineResult;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.hdg.prysm.agentic.ReviewAgentRole.*;

@Component
public class AgenticRunStore {

    public static final class RunContext implements java.io.Serializable {
        private final ReviewExecutionInput input;
        private final RuleEngineResult rules;
        private final AgenticExecutionBudget budget;
        private final Map<com.hdg.prysm.agentic.ReviewAgentRole, LlmReviewResult> results = new EnumMap<>(com.hdg.prysm.agentic.ReviewAgentRole.class);

        public RunContext(ReviewExecutionInput input, RuleEngineResult rules, AgenticExecutionBudget budget) {
            this.input = input;
            this.rules = rules;
            this.budget = budget;
        }

        public ReviewExecutionInput input() { return input; }
        public RuleEngineResult rules() { return rules; }
        public AgenticExecutionBudget budget() { return budget; }
        public synchronized void put(com.hdg.prysm.agentic.ReviewAgentRole role, LlmReviewResult result) { results.put(role, result); }
        public synchronized Map<com.hdg.prysm.agentic.ReviewAgentRole, LlmReviewResult> results() { return Map.copyOf(results); }
    }

    private final Map<String, RunContext> runs = new ConcurrentHashMap<>();
    private final Path directory;

    public AgenticRunStore(@Value("${prysm.agentic.checkpoint-directory:${java.io.tmpdir}/prysm-checkpoints}") String directory) {
        this.directory = Path.of(directory).resolve("run-inputs");
        try {
            Files.createDirectories(this.directory);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot create agentic run store", exception);
        }
    }

    public void put(String runId, RunContext context) {
        runs.put(runId, context);
        write(runId, context);
    }
    public RunContext require(String runId) {
        RunContext context = runs.computeIfAbsent(runId, this::read);
        if (context == null) throw new IllegalStateException("Unknown agentic run: " + runId);
        return context;
    }
    public void remove(String runId) {
        runs.remove(runId);
        try {
            Files.deleteIfExists(file(runId));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot delete agentic run input", exception);
        }
    }

    private void write(String runId, RunContext context) {
        try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(file(runId)))) {
            output.writeObject(context);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot persist agentic run input", exception);
        }
    }

    private RunContext read(String runId) {
        Path path = file(runId);
        if (!Files.exists(path)) return null;
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(path))) {
            return (RunContext) input.readObject();
        } catch (java.io.IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("Cannot restore agentic run input", exception);
        }
    }

    private Path file(String runId) {
        String safe = runId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return directory.resolve(safe + ".bin");
    }
}
