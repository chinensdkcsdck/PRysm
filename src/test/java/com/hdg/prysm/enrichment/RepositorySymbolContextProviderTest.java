package com.hdg.prysm.enrichment;

import com.hdg.prysm.context.PrContext;
import com.hdg.prysm.diff.PrChangedFile;
import com.hdg.prysm.diff.PrChangedFileStatus;
import com.hdg.prysm.diff.PrDiff;
import com.hdg.prysm.execution.ContextStatus;
import com.hdg.prysm.execution.ContextStatusCode;
import com.hdg.prysm.execution.PromptPayload;
import com.hdg.prysm.execution.ReviewExecutionInput;
import com.hdg.prysm.execution.ReviewTargetFile;
import com.hdg.prysm.review.PrReviewFileContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositorySymbolContextProviderTest {

    @TempDir
    Path repositoryRoot;

    @Test
    void shouldAddDefinitionsCallersAndTestsWithinBudget() throws IOException {
        write("src/main/java/PaymentGateway.java", """
                public class PaymentGateway {
                    public Receipt charge() {
                        return new Receipt();
                    }
                }
                """);
        write("src/main/java/CheckoutController.java", """
                public class CheckoutController {
                    public Receipt submit(OrderService service, PaymentGateway gateway) {
                        return service.checkout(gateway);
                    }
                }
                """);
        write("src/test/java/OrderServiceTest.java", """
                class OrderServiceTest {
                    private final OrderService service = new OrderService();
                }
                """);
        write("src/main/java/OrderService.java", """
                public class OrderService {
                    public Receipt checkout(PaymentGateway gateway) {
                        return gateway.charge();
                    }
                }
                """);

        RepositorySymbolContextProvider provider = new RepositorySymbolContextProvider(
                repositoryRoot,
                100,
                20,
                8,
                1,
                12000,
                262144
        );

        CrossFileContext context = provider.build(input());

        assertTrue(context.hasContent());
        assertTrue(context.promptFragment().contains("[定义] src/main/java/PaymentGateway.java"));
        assertTrue(context.promptFragment().contains("[直接调用方] src/main/java/CheckoutController.java"));
        assertTrue(context.promptFragment().contains("[相关测试] src/test/java/OrderServiceTest.java"));
        assertTrue(context.promptFragment().contains("Java AST 定义、直接调用方和相关测试"));
        assertTrue(context.promptFragment().length() <= 12000);
        assertEquals(false, context.truncated());
    }

    @Test
    void shouldUseAstAndIgnoreSymbolNamesInsideCommentsAndStrings() throws IOException {
        write("src/main/java/ChangedService.java", """
                public class ChangedService {
                    public void execute() {
                    }
                }
                """);
        write("src/main/java/RealCaller.java", """
                public class RealCaller {
                    void run(ChangedService service) {
                        service.execute();
                    }
                }
                """);
        write("src/main/java/Noise.java", """
                public class Noise {
                    String text = "execute()";
                    // execute();
                }
                """);

        RepositorySymbolContextProvider provider = new RepositorySymbolContextProvider(
                repositoryRoot, 100, 20, 8, 1, 12000, 262144
        );
        PrContext prContext = new PrContext("owner", "repo", 1);
        PrChangedFile changedFile = new PrChangedFile(
                "src/main/java/ChangedService.java",
                PrChangedFileStatus.MODIFIED,
                2,
                0,
                "@@ -1,0 +1,4 @@\n+public class ChangedService {\n+    public void execute() {\n+    }\n+}"
        );
        ReviewTargetFile targetFile = new ReviewTargetFile(
                changedFile,
                List.of(new PrReviewFileContext.Snippet(1, 4, "class ChangedService")),
                0,
                true,
                "selected"
        );
        ReviewExecutionInput input = new ReviewExecutionInput(
                prContext,
                new PrDiff(prContext, List.of(changedFile)),
                List.of(targetFile),
                new ContextStatus(ContextStatusCode.FULL, "ready"),
                new PromptPayload("system", "user", "{}")
        );

        CrossFileContext context = provider.build(input);

        assertTrue(context.promptFragment().contains("[直接调用方] src/main/java/RealCaller.java"));
        assertTrue(!context.promptFragment().contains("src/main/java/Noise.java"));
    }

    private ReviewExecutionInput input() {
        PrContext prContext = new PrContext("owner", "repo", 1);
        PrChangedFile changedFile = new PrChangedFile(
                "src/main/java/OrderService.java",
                PrChangedFileStatus.MODIFIED,
                4,
                0,
                """
                @@ -1,0 +1,4 @@
                +public class OrderService {
                +    public Receipt checkout(PaymentGateway gateway) {
                +        return gateway.charge();
                +    }
                """
        );
        ReviewTargetFile targetFile = new ReviewTargetFile(
                changedFile,
                List.of(new PrReviewFileContext.Snippet(
                        1,
                        4,
                        "public class OrderService {\n    public Receipt checkout(PaymentGateway gateway) {\n        return gateway.charge();\n    }"
                )),
                0,
                true,
                "selected"
        );
        return new ReviewExecutionInput(
                prContext,
                new PrDiff(prContext, List.of(changedFile)),
                List.of(targetFile),
                new ContextStatus(ContextStatusCode.FULL, "ready"),
                new PromptPayload("system", "user", "{}")
        );
    }

    private void write(String relativePath, String content) throws IOException {
        Path path = repositoryRoot.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
