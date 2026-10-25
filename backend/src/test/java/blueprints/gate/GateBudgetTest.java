package blueprints.gate;

import blueprints.inputs.InputGenerator;
import blueprints.project.ProjectStore;
import blueprints.runner.ProgramRunner;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Общий лимит на проверку: тяжёлая программа не держит проверку минутами, а получает INCOMPLETE с причиной.
 * Программа ниже читает ввод (≈40 входов × 3 прогона) и спит 1 с на прогон — без лимита это ~40 с,
 * с лимитом 2 с проверка обязана закончиться за секунды.
 */
class GateBudgetTest {

    private static final String SLOW = """
            import java.util.Scanner;

            public class Slow {
                public static void main(String[] args) throws Exception {
                    Scanner in = new Scanner(System.in);
                    String line = in.hasNextLine() ? in.nextLine() : "";
                    Thread.sleep(1000);
                    System.out.println("ok " + line.length());
                }
            }
            """;

    @Test
    void slowProgramStopsAtBudgetWithReadableReason() throws Exception {
        try (GateService gate = new GateService(new ProgramRunner(), new InputGenerator(), Duration.ofSeconds(2))) {
            long start = System.nanoTime();
            String jobId = gate.start(new ProjectStore().create("Slow.java", SLOW), SLOW, SLOW, Intent.REFACTOR);
            Verdict verdict = GateServiceTest.await(gate, jobId);
            long seconds = (System.nanoTime() - start) / 1_000_000_000;

            assertEquals(VerdictStatus.INCOMPLETE, verdict.status());
            assertTrue(verdict.incompleteReason().contains("общему лимиту времени 2 с"), verdict.incompleteReason());
            assertTrue(verdict.matched() < verdict.inputs(), "часть входов не должна быть сравнена");
            assertTrue(seconds < 20, "проверка шла " + seconds + " с — лимит не остановил прогоны");
        }
    }
}
