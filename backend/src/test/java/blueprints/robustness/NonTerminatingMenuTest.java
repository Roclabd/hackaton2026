package blueprints.robustness;

import blueprints.gate.GateService;
import blueprints.gate.Intent;
import blueprints.gate.Verdict;
import blueprints.gate.VerdictStatus;
import blueprints.project.Project;
import blueprints.project.ProjectStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Меню, которое на конце ввода не выходит, а ждёт вечно. Раньше проверка такой программы шла минутами
 * (каждый из ~120 прогонов — таймаут 5 с) и в интерфейсе выглядела зависшей. Теперь — досрочная остановка
 * после нескольких таймаутов эталона подряд и сравнение только сценариев с командой выхода.
 */
class NonTerminatingMenuTest {

    /** С запасом: решение после первых таймаутов (~5 с), сценарии с выходом — ещё пара секунд. */
    private static final long LIMIT_MS = 30_000;

    private static final String MENU = """
            import java.util.Scanner;
            public class Menu {
                public static void main(String[] args) {
                    Scanner sc = new Scanner(System.in);
                    int total = 0;
                    while (true) {
                        if (!sc.hasNextLine()) continue;
                        String cmd = sc.nextLine().trim();
                        if (cmd.equals("выход")) { System.out.println("итого " + total); break; }
                        switch (cmd) {
                            case "плюс" -> total += 1;
                            case "минус" -> total -= 1;
                            default -> System.out.println("?" + cmd);
                        }
                    }
                }
            }
            """;

    private static GateService gate;
    private final ProjectStore store = new ProjectStore();

    @BeforeAll
    static void setUp() {
        gate = new GateService();
    }

    @AfterAll
    static void tearDown() {
        gate.close();
    }

    @Test
    void menuWithoutEndOfInputExitIsIncompleteQuickly() throws Exception {
        Timed timed = check("Menu.java", MENU, MENU, Intent.REFACTOR);
        Verdict verdict = timed.verdict();
        assertEquals(VerdictStatus.INCOMPLETE, verdict.status());
        String reason = verdict.incompleteReason();
        assertNotNull(reason);
        assertTrue(reason.startsWith("Программа не завершается на входах без команды выхода: проверено "), reason);
        assertTrue(reason.contains("«выход»"), reason);
        assertTrue(verdict.matched() > 0, "сценарии с командой выхода сравнены: " + reason);
        assertTrue(timed.ms() < LIMIT_MS, "проверка заняла " + timed.ms() + " мс");
    }

    @Test
    void differenceOnExitScenariosIsStillFound() throws Exception {
        String candidate = MENU.replace("total += 1", "total += 2");
        Verdict verdict = check("Menu.java", MENU, candidate, Intent.REFACTOR).verdict();
        assertEquals(VerdictStatus.MISMATCH, verdict.status(), String.valueOf(verdict.incompleteReason()));
        assertTrue(verdict.smallestMismatch().input().contains("плюс"), verdict.smallestMismatch().input());
    }

    @Test
    void loopWithoutAnyExitCommandSaysSo() throws Exception {
        String endless = """
                import java.util.Scanner;
                public class Endless {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        while (true) {
                            if (sc.hasNextLine()) System.out.println(sc.nextLine().length());
                        }
                    }
                }
                """;
        Timed timed = check("Endless.java", endless, endless, Intent.REFACTOR);
        assertEquals(VerdictStatus.INCOMPLETE, timed.verdict().status());
        assertTrue(timed.verdict().incompleteReason().contains("проверено 0 из"), timed.verdict().incompleteReason());
        assertTrue(timed.verdict().incompleteReason().contains("найти не удалось"), timed.verdict().incompleteReason());
        assertTrue(timed.ms() < LIMIT_MS, "проверка заняла " + timed.ms() + " мс");
    }

    @Test
    void bookingFromCorpusIsIncompleteQuickly() throws Exception {
        String source = Files.readString(Path.of("../corpus/robustness/Booking/Booking.java"), StandardCharsets.UTF_8);
        Timed timed = check("Booking.java", source, source, Intent.REFACTOR);
        System.out.println("[замер] Booking (меню без выхода на конце ввода): " + timed.ms() + " мс, "
                + timed.verdict().incompleteReason());
        assertEquals(VerdictStatus.INCOMPLETE, timed.verdict().status());
        assertTrue(timed.verdict().matched() > 0, timed.verdict().incompleteReason());
        assertTrue(timed.ms() < LIMIT_MS, "проверка заняла " + timed.ms() + " мс");
    }

    private record Timed(Verdict verdict, long ms) {}

    private Timed check(String fileName, String original, String candidate, Intent intent) throws Exception {
        Project project = store.create(fileName, original);
        long start = System.nanoTime();
        String jobId = gate.start(project, original, candidate, intent);
        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            Verdict verdict = gate.job(jobId).orElseThrow();
            if (verdict.status() != VerdictStatus.RUNNING) {
                return new Timed(verdict, (System.nanoTime() - start) / 1_000_000);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("проверка не закончилась за 2 минуты");
    }
}
