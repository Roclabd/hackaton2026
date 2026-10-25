package blueprints.gate;

import blueprints.project.ProjectStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Досрочная остановка ({@link HangWatch}) не должна стирать расхождения, найденные до неё.
 * Программа при n ≤ 0 зацикливается (сторож срабатывает), иначе печатает n; кандидат печатает n + 1.
 * Раньше итог был INCOMPLETE с inputs = 0 и mismatches = 0, хотя расхождения уже были найдены.
 */
class HangKeepsMismatchTest {

    private static final String ORIGINAL = """
            import java.util.Scanner;

            public class Loop {
                public static void main(String[] args) {
                    int n = new Scanner(System.in).nextInt();
                    while (n <= 0) {
                    }
                    System.out.println(n);
                }
            }
            """;

    @Test
    void mismatchFoundBeforeHangStopIsKept() throws Exception {
        String candidate = ORIGINAL.replace("System.out.println(n);", "System.out.println(n + 1);");
        try (GateService gate = new GateService()) {
            String jobId = gate.start(new ProjectStore().create("Loop.java", ORIGINAL), ORIGINAL, candidate, Intent.REFACTOR);
            Verdict verdict = GateServiceTest.await(gate, jobId);

            assertEquals(VerdictStatus.MISMATCH, verdict.status(), verdict.incompleteReason());
            assertTrue(verdict.mismatches() > 0, "расхождения до остановки должны остаться");
            assertTrue(verdict.inputs() > 0, "статистика сравнений не обнуляется");
            assertNotNull(verdict.smallestMismatch());
        }
    }
}
