package blueprints.profile;

import blueprints.diag.Finding;
import blueprints.project.Project;
import blueprints.project.ProjectStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Замер роста на демо-файле MatchDemo и честные отказы для неподдержанных программ. */
class GrowthProfilerTest {

    private static GrowthProfiler profiler;
    private static String matchDemo;
    private final ProjectStore store = new ProjectStore();

    @BeforeAll
    static void setUp() throws Exception {
        profiler = new GrowthProfiler();
        matchDemo = Files.readString(Path.of("../corpus/ours/MatchDemo/MatchDemo.java"), StandardCharsets.UTF_8);
    }

    @Test
    void matchDemoIsQuadraticAndFrequencyIndexIsLinear() {
        Project project = store.create("MatchDemo.java", matchDemo);

        ProfileResult result = profiler.profile(project, "R1-1", "R1-1-index");

        assertTrue(result.supported(), result.note());
        assertEquals(GrowingInputs.SIZES, result.sizes(), result.note());
        assertEquals("n²", result.before().model());
        assertEquals("n", result.after().model());
        // Вход — две строки по n чисел: внешний цикл n раз, внутренний n² раз; у заплатки — n + n.
        List<Long> expectedBefore = GrowingInputs.SIZES.stream().map(n -> (long) n * n + n).toList();
        List<Long> expectedAfter = GrowingInputs.SIZES.stream().map(n -> 2L * n).toList();
        assertEquals(expectedBefore, result.before().counts());
        assertEquals(expectedAfter, result.after().counts());
        assertEquals(800.5, result.factor());
        assertEquals(2, result.before().loops());
        assertTrue(result.durationMs() < 15_000, "бюджет запроса: " + result.durationMs() + " мс");
        System.out.println("MatchDemo: " + result);
    }

    @Test
    void withoutPatchOnlyOriginalIsMeasured() {
        Project project = store.create("MatchDemo.java", matchDemo);
        ProfileResult result = profiler.profile(project, "R1-1", null);
        assertTrue(result.supported(), result.note());
        assertEquals("n²", result.before().model());
        assertNull(result.after());
        assertNull(result.factor());
    }

    @Test
    void programWithoutInputIsNotSupported() {
        String source = """
                public class NoInput {
                    public static void main(String[] args) {
                        int[] a = {3, 1, 2};
                        int same = 0;
                        for (int x : a) {
                            for (int y : a) {
                                if (java.util.Objects.equals(x, y)) {
                                    same++;
                                }
                            }
                        }
                        System.out.println(same);
                    }
                }
                """;
        Project project = store.create("NoInput.java", source);
        String findingId = new blueprints.diag.FindingsService().find(project).get(0).id();

        ProfileResult result = profiler.profile(project, findingId, null);

        assertFalse(result.supported());
        assertTrue(result.note().contains("не поддержан"), result.note());
        assertNull(result.sizes());
        assertNull(result.before());
    }

    /** n³: после малых размеров большие не запускаются (ожидалось > 5·10⁸ операций) — и об этом сказано. */
    @Test
    void cubicGrowthCutsLargeSizesHonestly() {
        String source = """
                import java.util.Scanner;

                public class Cubic {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        String[] tokens = in.nextLine().trim().split("\\\\s+");
                        int[] a = new int[tokens.length];
                        for (int i = 0; i < tokens.length; i++) a[i] = Integer.parseInt(tokens[i]);
                        long triples = 0;
                        for (int i = 0; i < a.length; i++) {
                            for (int j = 0; j < a.length; j++) {
                                for (int k = 0; k < a.length; k++) {
                                    if (a[i] + a[j] == a[k]) triples++;
                                }
                            }
                        }
                        System.out.println(triples);
                    }
                }
                """;
        Finding triple = new Finding("T-1", "T", "M:Cubic#main(String[])", new int[]{10, 16}, "Тройной цикл", "",
                "n³", null, List.of(), List.of());

        ProfileResult result = profiler.measure(source, triple, null, System.currentTimeMillis());

        assertTrue(result.supported(), result.note());
        assertEquals(List.of(100, 200, 400), result.sizes());
        assertEquals("n³", result.before().model());
        assertEquals(3, result.before().loops(), "цикл разбора в строке 8 не входит в строки находки");
        assertTrue(result.note().contains("не запускались"), result.note());
    }

    /** Программа ждёт одно число в строке и падает на «n чисел через пробел» — честный отказ, без цифр. */
    @Test
    void inputRejectedByProgramIsNotSupported() {
        String source = """
                import java.util.Scanner;

                public class OneNumber {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        int n = Integer.parseInt(in.nextLine().trim());
                        long s = 0;
                        for (int i = 0; i < n; i++) {
                            s += i;
                        }
                        System.out.println(s);
                    }
                }
                """;
        Finding loop = new Finding("L-1", "L", "M:OneNumber#main(String[])", new int[]{8, 10}, "Цикл", "",
                null, null, List.of(), List.of());

        ProfileResult result = profiler.measure(source, loop, null, System.currentTimeMillis());

        assertFalse(result.supported());
        assertTrue(result.note().contains("формата ввода не поддержан"), result.note());
        assertTrue(result.note().contains("NumberFormatException"), result.note());
        assertNull(result.before());
    }

    @Test
    void unknownFindingIs404() {
        Project project = store.create("MatchDemo.java", matchDemo);
        GrowthProfiler.RequestException error = assertThrows(GrowthProfiler.RequestException.class,
                () -> profiler.profile(project, "R9-9", null));
        assertEquals(404, error.status());
    }
}
