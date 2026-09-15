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
        assertTrue(context.promptFragment().contains("追踪范围: Java 直接定义、直接调用方和相关测试（1 层）"));
        assertTrue(context.promptFragment().length() <= 12000);
        assertEquals(false, context.truncated());
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
