package blueprints.diag;

import blueprints.parse.SourceParser;
import blueprints.runner.CompileResult;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Правило R1 и заплатки: находка на демо, эквивалентность заплатки прогоном, отсутствие заплатки там, где её быть не может. */
class FindingsServiceTest {

    /** Входы для сравнения оригинала и заплатки: обычные, повторы, пустые списки, null. */
    private static final List<String> INPUTS = List.of(
            "1 2 3\n2 3 4", "1 1 1\n1 1", "\n1 2", "1 2\n", "null\nnull", "null 1\n1 null null", "");

    private final FindingsService service = new FindingsService();

    @Test
    void matchDemoHasOneFindingWithFrequencyIndex() throws Exception {
        String source = read("../corpus/ours/MatchDemo/MatchDemo.java");
        List<Finding> findings = find(source);

        assertEquals(1, findings.size(), "одна находка");
        Finding f = findings.getFirst();
        assertEquals("R1-1", f.id());
        assertEquals("R1", f.rule());
        assertEquals("M:MatchDemo#countMatches(List<Integer>,List<Integer>)", f.methodId());
        assertArrayEquals(new int[]{14, 20}, f.lines(), "строки внешнего цикла");
        assertEquals("Вложенный цикл", f.title());
        assertEquals("n²", f.growthBefore());
        assertEquals("n", f.growthAfter());
        assertTrue(f.conditions().contains("Списки a и b не меняются внутри цикла"), f.conditions().toString());

        assertEquals(1, f.patches().size());
        Patch patch = f.patches().getFirst();
        assertEquals("R1-1-index", patch.id());
        assertEquals("REFACTOR", patch.intent());
        assertArrayEquals(new int[]{14, 23}, patch.changedLines(), "10 строк заплатки с 14-й");
        assertTrue(patch.source().contains("freq.merge(y, 1L, Long::sum);"), patch.source());
        assertUnchangedOutside(source, patch.source(), 14, 20, 23);
    }

    @Test
    void matchDemoPatchBehavesLikeOriginal() throws Exception {
        String source = read("../corpus/ours/MatchDemo/MatchDemo.java");
        assertEquivalentOnInputs("MatchDemo", source, patchOf(source), INPUTS);
    }

    @Test
    void patchedSourceParses() throws Exception {
        String patched = patchOf(read("../corpus/ours/MatchDemo/MatchDemo.java"));
        assertTrue(new SourceParser().parse(patched).errors().isEmpty(), "заплатка разбирается JavaParser");
    }

    /** Для подсчёта с другим оформлением, одним списком, int-счётчиком и занятым именем freq — та же заплатка. */
    @ParameterizedTest
    @ValueSource(strings = {
            "long total = 0; for (Integer x : a) { for (Integer y : b) { if (Objects.equals(y, x)) ++total; } } return total;",
            "int total = 0; for (Integer x : a) for (Integer y : b) if (Objects.equals(x, y)) total += 1; return total;",
            "long total = 0; for (Integer x : a) { for (Integer y : a) { if (Objects.equals(x, y)) { total++; } } } return total;",
            "long total = 0; long freq = 7; for (Integer x : a) { for (Integer y : b) { if (Objects.equals(x, y)) { total++; } } } return total + freq;",
    })
    void countingVariantsGetEquivalentPatch(String body) throws Exception {
        String source = withCountMatchesBody(body);
        String patched = patchOf(source);
        if (body.contains("long freq")) {
            assertTrue(patched.contains("freq2"), "занятое имя freq не перекрыто: " + patched);
        }
        assertEquivalentOnInputs("MatchDemo", source, patched, INPUTS);
    }

    /** Ленивый индекс: при пустом a список b не читается, при b == null исключение с тем же текстом. */
    @Test
    void indexIsBuiltLazilyLikeOriginalReadsB() throws Exception {
        String demo = read("../corpus/ours/MatchDemo/MatchDemo.java");
        String main = demo.substring(demo.indexOf("    public static void main"));
        String source = demo.replace(main, """
                    public static void main(String[] args) {
                        System.out.println(countMatches(new ArrayList<>(), null));
                        try {
                            countMatches(List.of(1), null);
                        } catch (NullPointerException e) {
                            System.out.println("NPE: " + e.getMessage());
                        }
                    }
                }
                """);
        assertEquivalentOnInputs("MatchDemo", source, patchOf(source), List.of(""));
        assertTrue(run("MatchDemo", source, "").contains("NPE: "), "оригинал действительно падает на b == null");
    }

    @Test
    void equalsMethodHasFindingButNoPatch() throws Exception {
        String source = read("../corpus/ours/MatchDemo/MatchDemo.java").replace("Objects.equals(x, y)", "x.equals(y)");
        Finding f = find(source).getFirst();
        assertTrue(f.patches().isEmpty(), "x.equals(y) падает на null — эквивалентного индекса нет");
        assertNull(f.growthAfter());
        assertTrue(f.why().contains("NullPointerException"), f.why());
    }

    @Test
    void sameReferenceHasFindingButNoPatch() throws Exception {
        String source = read("../corpus/ours/MatchDemo/MatchDemo.java").replace("Objects.equals(x, y)", "x == y");
        Finding f = find(source).getFirst();
        assertTrue(f.patches().isEmpty(), "== сравнивает ссылки");
        assertTrue(f.why().contains("ссылки"), f.why());
    }

    @Test
    void fieldCounterGetsNoFrequencyPatch() throws Exception {
        String source = withCountMatchesBody(
                "for (Integer x : a) { for (Integer y : b) { if (Objects.equals(x, y)) { hits++; } } } return hits;")
                .replace("public class MatchDemo {", "public class MatchDemo {\n    static long hits;");
        Finding f = find(source).getFirst();
        assertTrue(f.patches().stream().noneMatch(p -> p.title().contains("частот")), "поле — не локальный счётчик");
    }

    /** Действие над парой при разных списках — индекс групп; печать показывает и порядок пар. */
    @Test
    void actionOnPairGetsEquivalentGroupIndex() throws Exception {
        String source = withCountMatchesBody(
                "long total = 0; for (Integer x : a) { for (Integer y : b) { if (Objects.equals(x, y)) { System.out.println(x + \"=\" + y); } } } return total;");
        Finding f = find(source).getFirst();
        assertEquals("R1-1-index", f.patches().getFirst().id());
        assertEquals("n + m", f.growthAfter());
        assertEquivalentOnInputs("MatchDemo", source, f.patches().getFirst().source(), INPUTS);
    }

    /** Главный сценарий: пример со слайда 5. Находка объясняет самослияние, заплатка совпадает с оригиналом на всех входах. */
    @Test
    void slide5TrapFindingExplainsSelfMergeAndPatchIsEquivalent() throws Exception {
        String source = read("../corpus/traps/slide5/Slide5Trap.java");
        List<Finding> findings = find(source);

        assertEquals(1, findings.size(), "только метод original: " + findings);
        Finding f = findings.getFirst();
        assertEquals("M:Slide5Trap#original(List<Item>)", f.methodId());
        assertTrue(f.why().contains("merge(a, a)") && f.why().contains("самослияние"), f.why());
        assertEquals(1, f.patches().size());

        String patched = f.patches().getFirst().source();
        assertTrue(patched.contains("a.id().equals(a.id());"), "NPE оригинала сохранён: " + patched);
        String expected = run("Slide5Trap", source, "");
        assertTrue(expected.contains("NullPointerException"), "оригинал падает на null-id");
        assertEquals(expected, run("Slide5Trap", patched, ""), "вывод (с текстом NPE) совпадает байт в байт");
    }

    @Test
    void miniLangHasNoFalseFindings() throws Exception {
        assertEquals(List.of(), find(read("../corpus/ours/MiniLang/MiniLang.java")));
    }

    @Test
    void crlfFileGetsCrlfPatch() throws Exception {
        String source = read("../corpus/ours/MatchDemo/MatchDemo.java").replace("\r\n", "\n").replace("\n", "\r\n");
        String patched = patchOf(source);
        assertFalse(patched.replace("\r\n", "").contains("\n"), "в заплатке только \\r\\n");
        assertEquivalentOnInputs("MatchDemo", source, patched, List.of("1 1 1\n1 1"));
    }

    @Test
    void textFromTokensEqualsSource() throws Exception {
        String source = read("../corpus/ours/MatchDemo/MatchDemo.java");
        assertEquals(source, SourceText.fromTokens(SourceParser.parseOrThrow(source)).orElseThrow());
    }

    // --- помощники ---

    private List<Finding> find(String source) {
        CompilationUnit unit = SourceParser.parseOrThrow(source);
        return service.find(unit, source);
    }

    private String patchOf(String source) {
        List<Finding> findings = find(source);
        assertEquals(1, findings.size(), "одна находка");
        assertEquals(1, findings.getFirst().patches().size(), "есть заплатка: " + findings.getFirst().why());
        return findings.getFirst().patches().getFirst().source();
    }

    private static void assertEquivalentOnInputs(String mainClass, String original, String patched, List<String> inputs) throws Exception {
        CompileResult before = ProgramRun.compile(mainClass, original);
        CompileResult after = ProgramRun.compile(mainClass, patched);
        for (String input : inputs) {
            assertEquals(ProgramRun.run(before, mainClass, input), ProgramRun.run(after, mainClass, input),
                    "вход " + input.replace("\n", "⏎") + "\n" + patched);
        }
    }

    private static String run(String mainClass, String source, String input) throws Exception {
        return ProgramRun.run(ProgramRun.compile(mainClass, source), mainClass, input);
    }

    /** MatchDemo с другим телом countMatches. */
    private static String withCountMatchesBody(String body) throws Exception {
        String demo = read("../corpus/ours/MatchDemo/MatchDemo.java").replace("\r\n", "\n");
        int start = demo.indexOf("    public static long countMatches");
        int end = demo.indexOf("\n    }\n", start) + "\n    }\n".length();
        return demo.substring(0, start)
                + "    public static long countMatches(List<Integer> a, List<Integer> b) {\n        " + body + "\n    }\n"
                + demo.substring(end);
    }

    /** Строки до заплатки и после неё не изменились. */
    private static void assertUnchangedOutside(String original, String patched, int firstLine, int oldLast, int newLast) {
        List<String> before = original.lines().toList();
        List<String> after = patched.lines().toList();
        assertEquals(before.subList(0, firstLine - 1), after.subList(0, firstLine - 1), "строки выше заплатки");
        assertEquals(before.subList(oldLast, before.size()), after.subList(newLast, after.size()), "строки ниже заплатки");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
