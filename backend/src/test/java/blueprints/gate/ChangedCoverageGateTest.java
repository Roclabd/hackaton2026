package blueprints.gate;

import blueprints.diag.FindingsService;
import blueprints.instrument.ChangedCoverage;
import blueprints.parse.SourceParser;
import blueprints.project.ProjectStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Покрытие изменённого кода в вердикте: аудит v4, дефект №1 (Discount), и демо питча (MatchDemo). */
class ChangedCoverageGateTest {

    private static GateService gate;
    private static String matchDemo;
    private final ProjectStore store = new ProjectStore();

    @BeforeAll
    static void setUp() throws Exception {
        gate = new GateService();
        matchDemo = read("../corpus/ours/MatchDemo/MatchDemo.java");
    }

    @AfterAll
    static void tearDown() {
        gate.close();
    }

    /** Как проба аудита Discount, но до оптовой ветки не доходит ни один сгенерированный вход. */
    private static final String RARE_DISCOUNT = """
            import java.util.Scanner;
            public class Discount {
                static int price(int qty) {
                    if (qty % 1000 == 777) {
                        return qty * 90;          // особая цена
                    }
                    return qty * 100;
                }
                public static void main(String[] args) {
                    Scanner in = new Scanner(System.in);
                    while (in.hasNextInt()) {
                        int qty = in.nextInt();
                        System.out.println("qty=" + qty + " price=" + price(qty));
                    }
                }
            }
            """;

    @Test
    void changeNotReachedByInputsIsIncompleteNotMatched() throws Exception {
        String candidate = RARE_DISCOUNT.replace("qty * 90", "qty * 80");
        Verdict verdict = GateServiceTest.await(gate,
                gate.start(store.create("Discount.java", RARE_DISCOUNT), RARE_DISCOUNT, candidate, Intent.REFACTOR));

        assertEquals(VerdictStatus.INCOMPLETE, verdict.status(), String.valueOf(verdict.incompleteReason()));
        assertEquals(0, verdict.mismatches(), "расхождений нет — входы просто не дошли до правки");
        ChangedCoverage coverage = (ChangedCoverage) verdict.changedCovered();
        assertEquals(2, coverage.lines(), "изменённый return измерен в обеих версиях");
        assertEquals(0, coverage.covered());
        assertEquals(0, coverage.minHits());
        assertEquals(verdict.matched(), coverage.inputs(), "покрытие — по сравнённым входам");
        assertEquals("Изменённый код выполнился только на 0 из " + verdict.matched() + " входов: проверка не затронула "
                + "изменение. Добавьте вход, который доходит до строки 5", verdict.incompleteReason());
    }

    @Test
    void menuBranchReachedByOneInputIsIncomplete() throws Exception {
        String menu = """
                import java.util.*;
                public class Menu {
                    public static void main(String[] args) {
                        int seen = 0;
                        Scanner in = new Scanner(System.in);
                        List<String> items = new ArrayList<>();
                        while (true) {
                            String cmd = in.nextLine();
                            switch (cmd) {
                                case "add" -> {
                                    if (seen == 0 && !in.hasNextLine()) items.add("x" + items.size());
                                }
                                case "list" -> System.out.println(items);
                                default -> System.out.println("Неизвестная команда: " + cmd);
                            }
                            seen++;
                        }
                    }
                }
                """;
        String candidate = menu.replace("\"x\" + items.size()", "\"x\".concat(String.valueOf(items.size()))");
        Verdict verdict = GateServiceTest.await(gate,
                gate.start(store.create("Menu.java", menu), menu, candidate, Intent.REFACTOR));
        assertEquals(VerdictStatus.INCOMPLETE, verdict.status(), String.valueOf(verdict.incompleteReason()));
        ChangedCoverage coverage = (ChangedCoverage) verdict.changedCovered();
        assertEquals(1, coverage.minHits(), "только одиночная команда add, без командной последовательности");
        assertTrue(verdict.incompleteReason().endsWith("до строки 11"), verdict.incompleteReason());
    }

    @Test
    void auditDiscountProbeIsNeverMatched() throws Exception {
        // Аудит v4, дефект №1: было MATCHED на 27 входах, хотя изменённая строка ни разу не выполнялась.
        String original = read("src/test/resources/regressions/gate/Discount.java");
        String candidate = read("src/test/resources/regressions/gate/Discount.candidate.java");
        Verdict verdict = GateServiceTest.await(gate,
                gate.start(store.create("Discount.java", original), original, candidate, Intent.REFACTOR));
        System.out.println("[замер] Discount (аудит): " + verdict.status() + ", " + verdict.changedCovered());
        assertNotEquals(VerdictStatus.MATCHED, verdict.status());
        ChangedCoverage coverage = (ChangedCoverage) verdict.changedCovered();
        if (verdict.status() == VerdictStatus.MISMATCH) {
            // Сейчас генератор даёт и большие количества: расходящиеся входы — ровно те, что дошли до правки.
            assertEquals(verdict.mismatches(), coverage.inputsReached(), coverage.toString());
        } else {
            assertEquals(0, coverage.covered(), coverage.toString());
        }
    }

    @Test
    void matchDemoPatchIsMatchedWithCoverage() throws Exception {
        String patched = new FindingsService().find(SourceParser.parseOrThrow(matchDemo), matchDemo)
                .getFirst().patches().getFirst().source();
        Verdict verdict = GateServiceTest.await(gate,
                gate.start(store.create("MatchDemo.java", matchDemo), matchDemo, patched, Intent.REFACTOR));
        System.out.println("[замер] MatchDemo + R1: " + verdict.durationMs() + " мс, " + verdict.changedCovered());

        assertEquals(VerdictStatus.MATCHED, verdict.status(), String.valueOf(verdict.incompleteReason()));
        ChangedCoverage coverage = (ChangedCoverage) verdict.changedCovered();
        assertNotNull(coverage);
        assertTrue(coverage.lines() > 0);
        assertEquals(coverage.lines(), coverage.covered(), "вся заплатка выполнилась");
        assertTrue(coverage.minHits() >= CoverageRule.MIN_INPUTS, coverage.toString());
    }

    @Test
    void matchDemoBrokenEqualsIsStillMismatch() throws Exception {
        String candidate = matchDemo.replace("Objects.equals(x, y)", "x.equals(y)");
        Verdict verdict = GateServiceTest.await(gate,
                gate.start(store.create("MatchDemo.java", matchDemo), matchDemo, candidate, Intent.REFACTOR));
        assertEquals(VerdictStatus.MISMATCH, verdict.status());
        assertNotNull(verdict.changedCovered(), "покрытие показывается и при расхождении");
    }

    @Test
    void failedInstrumentationOfRealChangeIsIncomplete() throws Exception {
        String original = RARE_DISCOUNT + "\nclass __BlueprintsCoverage {}\n";
        String candidate = original.replace("qty * 90", "qty * 80");
        Verdict verdict = check(original, candidate, Intent.REFACTOR);
        assertEquals(0, verdict.mismatches(), "редкая ветка не достигнута входами");
        assertTrue(verdict.matched() >= 3, "чистые программы действительно сравнены");
        assertEquals(VerdictStatus.INCOMPLETE, verdict.status());
        assertTrue(verdict.incompleteReason().contains("со счётчиками не собирается"), verdict.incompleteReason());
    }

    @Test
    void visitedCandidateChangeCannotHideUnvisitedDeletedCode() throws Exception {
        String candidate = RARE_DISCOUNT.replace("        if (qty % 1000 == 777) {\n"
                + "            return qty * 90;          // особая цена\n        }\n", "")
                .replace("qty * 100", "100 * qty");
        assertFalse(candidate.contains("qty * 90"));
        Verdict verdict = check(RARE_DISCOUNT, candidate, Intent.REFACTOR);
        assertEquals(VerdictStatus.INCOMPLETE, verdict.status());
        ChangedCoverage coverage = (ChangedCoverage) verdict.changedCovered();
        assertNotNull(coverage);
        assertTrue(coverage.statements().stream().anyMatch(s -> !s.onOriginal() && s.inputs() >= 3),
                "посещаемая правка кандидата измерена");
        assertTrue(coverage.statements().stream().anyMatch(s -> s.onOriginal() && s.line() == 5 && s.inputs() == 0),
                "удалённый return эталона не выполнялся");
        assertTrue(verdict.incompleteReason().contains("эталона"), verdict.incompleteReason());
    }

    @Test
    void fieldAndSignatureChangesAreIncompleteEvenBesideVisitedStatement() throws Exception {
        String original = RARE_DISCOUNT.replace("public class Discount {", "public class Discount {\n    static int limit = 1;");
        for (String candidate : java.util.List.of(original.replace("limit = 1", "limit = 2"),
                original.replace("limit = 1", "limit = 2").replace("qty * 100", "100 * qty"),
                original.replace("String[] args", "String[] argv"))) {
            Verdict verdict = check(original, candidate, Intent.REFACTOR);
            assertEquals(VerdictStatus.INCOMPLETE, verdict.status());
            assertTrue(verdict.incompleteReason().contains("объявления"), verdict.incompleteReason());
        }
    }

    @Test
    void measurementFailureDoesNotReplaceCleanMismatchOrBehaviorChange() throws Exception {
        String original = RARE_DISCOUNT + "\nclass __BlueprintsCoverage {}\n";
        String candidate = original.replace("qty * 100", "qty * 101");
        assertEquals(VerdictStatus.MISMATCH, check(original, candidate, Intent.REFACTOR).status());
        assertEquals(VerdictStatus.BEHAVIOR_CHANGE, check(original, candidate, Intent.BUGFIX).status());
        assertEquals(VerdictStatus.BUILD_ERROR, check(original, candidate.replace("qty * 101", "qty *"), Intent.REFACTOR).status());
    }

    private Verdict check(String original, String candidate, Intent intent) throws Exception {
        return GateServiceTest.await(gate,
                gate.start(store.create("Discount.java", original), original, candidate, intent));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
