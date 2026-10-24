package blueprints.instrument;

import blueprints.runner.CompileResult;
import blueprints.runner.InMemoryCompiler;
import blueprints.runner.ProgramRunner;
import blueprints.runner.RunResult;
import blueprints.runner.WorkerProtocol;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Где встают счётчики и что копия со счётчиками ведёт себя как оригинал (проверено прогоном). */
class CoverageInstrumenterTest {

    /** Номера строк важны: тесты ссылаются на них. */
    static final String PROBE = """
            import java.util.Scanner;
            public class Probe {
                private final int size;
                Probe() {
                    this(7);
                }
                Probe(int size) {
                    this.size = size;
                }
                static String kind(int n) {
                    if (n < 0) return "neg";
                    if (n == 0)
                        return "zero";
                    String word = switch (n) {
                        case 1 -> "one";
                        default -> { yield "many"; }
                    };
                    outer:
                    for (int i = 0; i < n; i++) {
                        for (int j = 0; j < n; j++) {
                            if (i * j > 2) continue outer;
                        }
                    }
                    return word;
                }
                public static void main(String[] args) {
                    Scanner in = new Scanner(System.in);
                    while (in.hasNextInt()) {
                        int n = in.nextInt();
                        System.out.println(n + " " + kind(n) + " " + new Probe().size);
                    }
                    Object o = null;
                    if (args.length == 0) o.hashCode();
                }
            }
            """;
    private static final String COUNTER = "__BlueprintsCoverage.HITS";

    private final InMemoryCompiler compiler = new InMemoryCompiler();

    @Test
    void ifWithoutBracesCountsConditionAndWrapsBody() {
        InstrumentedSource copy = instrument(11);
        assertEquals(List.of(11, 11), copy.statementLines(), "сам if и его return на одной строке");
        assertTrue(copy.source().contains(COUNTER + "[0]++; if (n < 0) { " + COUNTER + "[1]++; return \"neg\"; }"),
                copy.source());
        assertCompiles(copy);
    }

    @Test
    void changedBodyOnItsOwnLineCountsOnlyTheBody() {
        InstrumentedSource copy = instrument(13);
        assertEquals(List.of(13), copy.statementLines(), "if на строке 12 не считается — изменилось только тело");
        assertTrue(copy.source().contains("{ " + COUNTER + "[0]++; return \"zero\"; }"), copy.source());
        assertCompiles(copy);
    }

    @Test
    void changedConditionCountsTheIfItself() {
        InstrumentedSource copy = instrument(12);
        assertEquals(List.of(12), copy.statementLines());
        assertTrue(copy.source().contains(COUNTER + "[0]++; if (n == 0)"), copy.source());
        assertCompiles(copy);
    }

    @Test
    void constructorCallGetsCounterAfterIt() {
        InstrumentedSource copy = instrument(5);
        assertTrue(copy.source().contains("this(7); " + COUNTER + "[0]++;"), copy.source());
        assertCompiles(copy);
    }

    @Test
    void switchExpressionArrowBecomesBlockWithYield() {
        InstrumentedSource copy = instrument(15);
        assertTrue(copy.source().contains("case 1 -> { " + COUNTER + "[0]++; yield \"one\"; }"), copy.source());
        assertCompiles(copy);
    }

    @Test
    void labeledLoopKeepsLabelNextToLoop() {
        InstrumentedSource copy = instrument(19);
        assertEquals(List.of(19), copy.statementLines());
        assertTrue(copy.source().contains(COUNTER + "[0]++; outer:"), copy.source());
        assertCompiles(copy);
    }

    @Test
    void fieldsAndSignaturesAreNotMeasurable() {
        assertTrue(CoverageInstrumenter.instrument(PROBE, lines(1, 3, 7, 10)).isEmpty(),
                "импорт, поле, сигнатуры конструктора и метода — не операторы");
    }

    @Test
    void everyStatementInstrumentedStillCompilesAndBehavesTheSame() {
        SortedSet<Integer> all = new TreeSet<>(IntStream.rangeClosed(1, 36).boxed().toList());
        InstrumentedSource copy = CoverageInstrumenter.instrument(PROBE, all).orElseThrow();
        assertTrue(copy.counters() > 15, "счётчики почти на каждом операторе: " + copy.counters());
        assertEquals(PROBE.lines().count() + 4, copy.source().lines().count(),
                "вставки на тех же строках; в конце добавлены пустая строка и класс счётчиков (3 строки)");

        ProgramRunner runner = new ProgramRunner();
        String input = "-1 0 1 1 5";
        RunResult original = runner.runOne(program(PROBE), input, Duration.ofSeconds(5));
        RunResult measured = runner.runOne(program(copy.source()), input, Duration.ofSeconds(5));
        assertEquals(original.stdout(), measured.stdout());
        assertEquals(original.exitCode(), measured.exitCode());
        assertEquals(original.exception(), measured.exception(), "то же исключение с тем же текстом");
        assertEquals("java.lang.NullPointerException", measured.exception().type());
        assertNull(original.coverageHits(), "у обычной программы счётчиков нет");
        assertNotNull(measured.coverageHits());
        assertEquals(copy.counters(), measured.coverageHits().length);
    }

    @Test
    void countersCountExecutions() {
        InstrumentedSource copy = CoverageInstrumenter.instrument(PROBE, lines(11, 13, 15)).orElseThrow();
        assertEquals(List.of(11, 11, 13, 15), copy.statementLines());
        RunResult measured = new ProgramRunner().runOne(program(copy.source()), "-1 0 1 1 5", Duration.ofSeconds(5));
        // if (n < 0) — на всех 5 числах; return "neg" — на -1; return "zero" — на 0; "one" — на двух единицах.
        assertArrayEquals(new int[]{5, 1, 1, 2}, measured.coverageHits());
    }

    private static InstrumentedSource instrument(int line) {
        return CoverageInstrumenter.instrument(PROBE, lines(line)).orElseThrow();
    }

    static SortedSet<Integer> lines(Integer... numbers) {
        return new TreeSet<>(List.of(numbers));
    }

    private void assertCompiles(InstrumentedSource copy) {
        CompileResult compiled = compiler.compile("Probe", copy.source());
        assertTrue(compiled.ok(), compiled.messages() + "\n" + copy.source());
    }

    private WorkerProtocol.Program program(String source) {
        CompileResult compiled = compiler.compile("Probe", source);
        assertTrue(compiled.ok(), compiled.messages() + "\n" + source);
        return new WorkerProtocol.Program("p", "Probe", compiled.classes());
    }
}
