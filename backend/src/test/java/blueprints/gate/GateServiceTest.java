package blueprints.gate;

import blueprints.inputs.InputGenerator;
import blueprints.project.Project;
import blueprints.project.ProjectStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Проверка на демо-файле MatchDemo: совпадение, расхождение на null, ошибка сборки. */
class GateServiceTest {

    private static final String BROKEN_EQUALS = "x.equals(y)";

    private static GateService gate;
    private static String matchDemo;
    private final ProjectStore store = new ProjectStore();

    @BeforeAll
    static void setUp() throws Exception {
        gate = new GateService();
        matchDemo = Files.readString(Path.of("../corpus/ours/MatchDemo/MatchDemo.java"), StandardCharsets.UTF_8);
    }

    @AfterAll
    static void tearDown() {
        gate.close();
    }

    static Verdict await(GateService gate, String jobId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            Verdict verdict = gate.job(jobId).orElseThrow();
            if (verdict.status() != VerdictStatus.RUNNING) {
                return verdict;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("проверка не закончилась за 2 минуты");
    }

    private Project project(String source) {
        return store.create("MatchDemo.java", source);
    }

    @Test
    void sameCodeMatches() throws Exception {
        long start = System.nanoTime();
        Verdict verdict = await(gate, gate.start(project(matchDemo), matchDemo, Intent.REFACTOR));
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println("[замер] MatchDemo, " + verdict.inputs() + " входов × 3 прогона: " + ms + " мс");

        assertEquals(VerdictStatus.MATCHED, verdict.status(), String.valueOf(verdict.incompleteReason()));
        assertEquals(Phase.DONE, verdict.phase());
        assertEquals(1.0, verdict.progress());
        assertTrue(verdict.inputs() >= 25, "около 30 входов: " + verdict.inputs());
        assertEquals(verdict.inputs(), verdict.checked());
        assertEquals(verdict.inputs(), verdict.matched(), "«Совпало на N» — все входы реально сравнены");
        assertEquals(0, verdict.mismatches());
        assertEquals(0, verdict.nondeterministic());
        assertNotNull(verdict.example());
        assertTrue(verdict.example().original().stdout().startsWith("Совпадений: "));
        assertTrue(verdict.groups().stream().anyMatch(g -> g.name().equals(InputGenerator.NULLS)
                && g.status() == VerdictStatus.MATCHED));
    }

    @Test
    void objectsEqualsToEqualsBreaksOnNull() throws Exception {
        String candidate = matchDemo.replace("Objects.equals(x, y)", BROKEN_EQUALS);
        assertNotEquals(matchDemo, candidate);
        Verdict verdict = await(gate, gate.start(project(matchDemo), candidate, Intent.REFACTOR));

        assertMismatchOnNull(verdict);
    }

    @Test
    void bugfixIntentGivesBehaviorChange() throws Exception {
        String candidate = matchDemo.replace("Objects.equals(x, y)", BROKEN_EQUALS);
        Verdict verdict = await(gate, gate.start(project(matchDemo), candidate, Intent.BUGFIX));
        assertEquals(VerdictStatus.BEHAVIOR_CHANGE, verdict.status());
    }

    @Test
    void explicitOriginalIsTheReferenceNotTheProjectVersion() throws Exception {
        // Проект уже на версии с правкой узла; эталон передан явно — сравнение не должно быть «правки с собой».
        String edited = matchDemo.replace("Objects.equals(x, y)", BROKEN_EQUALS);
        Verdict verdict = await(gate, gate.start(project(edited), matchDemo, edited, Intent.REFACTOR));
        assertMismatchOnNull(verdict);
    }

    @Test
    void syntaxErrorIsBuildError() throws Exception {
        String candidate = matchDemo.replace("total++;", "total++");
        Verdict verdict = await(gate, gate.start(project(matchDemo), candidate, Intent.REFACTOR));
        assertEquals(VerdictStatus.BUILD_ERROR, verdict.status());
        assertFalse(verdict.buildErrors().isEmpty());
        assertTrue(verdict.buildErrors().get(0).line() > 0);
    }

    @Test
    void typeErrorIsBuildErrorWithLine() throws Exception {
        String candidate = matchDemo.replace("total++;", "total = \"строка\";");
        Verdict verdict = await(gate, gate.start(project(matchDemo), candidate, Intent.REFACTOR));
        assertEquals(VerdictStatus.BUILD_ERROR, verdict.status());
        assertEquals(17, verdict.buildErrors().get(0).line(), verdict.buildErrors().toString());
    }

    @Test
    void hangingCandidateIsIncompleteNotMatched() throws Exception {
        String original = """
                public class Hang {
                    public static void main(String[] args) { System.out.println("ok"); }
                }
                """;
        String candidate = """
                public class Hang {
                    public static void main(String[] args) { while (true) { } }
                }
                """;
        Verdict verdict = await(gate, gate.start(store.create("Hang.java", original), original, candidate, Intent.REFACTOR));
        assertEquals(VerdictStatus.INCOMPLETE, verdict.status());
        assertTrue(verdict.incompleteReason().contains("лимит времени"), verdict.incompleteReason());
    }

    @Test
    void randomOutputIsNondeterministicAndNotCompared() throws Exception {
        String source = """
                public class Dice {
                    public static void main(String[] args) { System.out.println(System.nanoTime()); }
                }
                """;
        Verdict verdict = await(gate, gate.start(store.create("Dice.java", source), source, Intent.REFACTOR));
        assertEquals(1, verdict.nondeterministic());
        assertEquals(VerdictStatus.INCOMPLETE, verdict.status(), "сравнивать не с чем — не зелёный");
    }

    @Test
    void menuProgramEndingWithNoSuchElementIsComparable() throws Exception {
        // Меню ждёт ввод в цикле; вход кончился → NoSuchElementException. Это нормальный, сравнимый исход.
        String menu = """
                import java.util.*;
                public class Menu {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        List<String> items = new ArrayList<>();
                        while (true) {
                            String cmd = in.nextLine();
                            switch (cmd) {
                                case "add" -> items.add("x" + items.size());
                                case "list" -> System.out.println(items);
                                case "exit" -> System.exit(0);
                                default -> System.out.println("Неизвестная команда: " + cmd);
                            }
                        }
                    }
                }
                """;
        // Правка — в ветке, до которой доходит большинство входов: иначе вердикт честно INCOMPLETE по покрытию
        // (правка в «add» выполняется на одном входе — это проверяет ChangedCoverageGateTest).
        String candidate = menu.replace("\"Неизвестная команда: \" + cmd", "\"Неизвестная команда: \".concat(cmd)");
        assertNotEquals(menu, candidate);
        Verdict verdict = await(gate, gate.start(store.create("Menu.java", menu), menu, candidate, Intent.REFACTOR));
        assertEquals(VerdictStatus.MATCHED, verdict.status(), String.valueOf(verdict.incompleteReason()));
        assertTrue(verdict.groups().stream().anyMatch(g -> g.name().equals(InputGenerator.COMMANDS)));
    }

    @Test
    void bigMonolithWithoutInputMatchesItself() throws Exception {
        String miniLang = Files.readString(Path.of("../corpus/ours/MiniLang/MiniLang.java"), StandardCharsets.UTF_8);
        Verdict verdict = await(gate, gate.start(store.create("MiniLang.java", miniLang), miniLang, Intent.REFACTOR));
        assertEquals(VerdictStatus.MATCHED, verdict.status(), String.valueOf(verdict.incompleteReason()));
    }

    private static void assertMismatchOnNull(Verdict verdict) {
        assertEquals(VerdictStatus.MISMATCH, verdict.status());
        assertTrue(verdict.mismatches() > 0);
        RunPair smallest = verdict.smallestMismatch();
        assertNotNull(smallest);
        assertTrue(smallest.input().contains("null"), "самый маленький расходящийся вход содержит null: " + smallest.input());
        assertNull(smallest.original().exception());
        assertEquals("java.lang.NullPointerException", smallest.candidate().exception().type());
        assertTrue(smallest.differences().stream().anyMatch(d -> d.field().equals("exception")));
    }
}
