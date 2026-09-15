package com.hdg.prysm.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs an offline labeled-dataset evaluation without invoking GitHub or an LLM.
 */
@Component
@ConditionalOnProperty(name = "prysm.evaluation.enabled", havingValue = "true")
public class ReviewEvaluationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReviewEvaluationRunner.class);

    private final ObjectMapper objectMapper;
    private final ReviewEvaluationService evaluationService;
    private final Path datasetPath;
    private final Path outputPath;
    private final int lineTolerance;

    public ReviewEvaluationRunner(
            ObjectMapper objectMapper,
            ReviewEvaluationService evaluationService,
            @Value("${prysm.evaluation.dataset}") String datasetPath,
            @Value("${prysm.evaluation.output:}") String outputPath,
            @Value("${prysm.evaluation.line-tolerance:2}") int lineTolerance
    ) {
        this.objectMapper = objectMapper;
        this.evaluationService = evaluationService;
        this.datasetPath = Path.of(datasetPath).toAbsolutePath().normalize();
        this.outputPath = outputPath == null || outputPath.isBlank()
                ? null : Path.of(outputPath).toAbsolutePath().normalize();
        this.lineTolerance = lineTolerance;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        EvaluationDataset dataset = objectMapper.readValue(datasetPath.toFile(), EvaluationDataset.class);
        EvaluationReport report = evaluationService.evaluate(dataset, lineTolerance);
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        log.info("PRysm evaluation report:\n{}", json);
        if (outputPath != null) {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputPath, json + System.lineSeparator());
        }
    }
}
