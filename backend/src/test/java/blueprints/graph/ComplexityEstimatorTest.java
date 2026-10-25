package blueprints.graph;

import blueprints.parse.SourceParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** Оценка сложности по структуре кода: каждое правило — на маленьком примере. */
class ComplexityEstimatorTest {

    private static Complexity of(String classBody, String method) {
        CompilationUnit unit = SourceParser.parseOrThrow("import java.util.*;\nclass C {\n" + classBody + "\n}");
        MethodDeclaration declaration = unit.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals(method)).findFirst().orElseThrow();
        return new ComplexityEstimator(unit).ofDeclaration(declaration);
    }

    @Test
    void noLoopsIsConstant() {
        Complexity c = of("int f(int a) { return a * 2; }", "f");
        assertEquals("O(1)", c.big());
        assertEquals("low", c.level());
    }

    @Test
    void nestedLoopsOverDataAreQuadratic() {
        Complexity c = of("int f(List<Integer> a, List<Integer> b) { int t = 0; for (int x : a) for (int y : b) if (x == y) t++; return t; }", "f");
        assertEquals("O(n²)", c.big());
        assertEquals("high", c.level());
        assertTrue(c.why().contains("стр."), c.why());
    }

    @Test
    void loopWithConstantBoundDoesNotCount() {
        assertEquals("O(n)", of("void f(int[] a) { for (int i = 0; i < 10; i++) for (int x : a) System.out.println(x); }", "f").big());
    }

    @Test
    void binarySearchIsLogarithmic() {
        String search = """
                int find(int[] a, int key) {
                    int lo = 0, hi = a.length - 1;
                    while (lo <= hi) {
                        int mid = (lo + hi) >>> 1;
                        if (a[mid] < key) lo = mid + 1; else if (a[mid] > key) hi = mid - 1; else return mid;
                    }
                    return -1;
                }""";
        assertEquals("O(log n)", of(search, "find").big());
    }

    @Test
    void librarySortIsNLogN() {
        assertEquals("O(n log n)", of("void f(List<Integer> a) { Collections.sort(a); }", "f").big());
        assertEquals("O(n² log n)", of("void f(List<List<Integer>> all) { for (List<Integer> a : all) Collections.sort(a); }", "f").big());
    }

    /** Любая степень — верхним индексом, без «^»: n⁴, n¹², log² n. */
    @Test
    void notationUsesSuperscriptsForAnyPower() {
        assertEquals("O(1)", ComplexityEstimator.notation(0, 0));
        assertEquals("O(n)", ComplexityEstimator.notation(1, 0));
        assertEquals("O(n³)", ComplexityEstimator.notation(3, 0));
        assertEquals("O(n⁴)", ComplexityEstimator.notation(4, 0));
        assertEquals("O(n¹⁰)", ComplexityEstimator.notation(10, 0));
        assertEquals("O(n⁴ log n)", ComplexityEstimator.notation(4, 1));
        assertEquals("O(n log² n)", ComplexityEstimator.notation(1, 2));
        assertEquals("O(log³ n)", ComplexityEstimator.notation(0, 3));
        String fourLoops = "int f(List<Integer> a) { int t = 0; for (int w : a) for (int x : a) for (int y : a) for (int z : a) t++; return t; }";
        assertEquals("O(n⁴)", of(fourLoops, "f").big());
    }

    /** Свой sort — не библиотечный: стоимость берётся из его тела (пузырёк — n²). */
    @Test
    void ownSortMethodIsCountedByItsBody() {
        String body = """
                void sort(int[] a) { for (int i = 0; i < a.length; i++) for (int j = 0; j + 1 < a.length - i; j++) if (a[j] > a[j+1]) { int t = a[j]; a[j] = a[j+1]; a[j+1] = t; } }
                void run(int[] a) { sort(a); }""";
        assertEquals("O(n²)", of(body, "run").big());
    }

    @Test
    void callInsideLoopMultiplies() {
        String body = """
                boolean has(List<String> all, String s) { for (String x : all) if (x.equals(s)) return true; return false; }
                int count(List<String> all, List<String> wanted) { int n = 0; for (String w : wanted) if (has(all, w)) n++; return n; }""";
        Complexity c = of(body, "count");
        assertEquals("O(n²)", c.big());
        assertTrue(c.why().contains("has()"), c.why());
    }

    @Test
    void listContainsInsideLoopIsQuadratic() {
        assertEquals("O(n²)", of("int f(List<Integer> a, int[] b) { int n = 0; for (int x : b) if (a.contains(x)) n++; return n; }", "f").big());
    }

    @Test
    void streamWithLambdaIsLinear() {
        assertEquals("O(n)", of("long f(List<Integer> a) { return a.stream().filter(x -> x > 0).count(); }", "f").big());
    }

    @Test
    void recursionIsNotGuessed() {
        Complexity c = of("long fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }", "fib");
        assertEquals("рекурсия", c.big());
        assertEquals("unknown", c.level());
    }

    @Test
    void mapAndLoopsCarryEstimates() throws Exception {
        String source = Files.readString(Path.of("../corpus/ours/MatchDemo/MatchDemo.java"), StandardCharsets.UTF_8);
        Graph map = new ProjectMapBuilder().build(SourceParser.parseOrThrow(source));
        Map<String, String> byMethod = new TreeMap<>();
        map.nodes().stream().filter(n -> n.kind() == NodeKind.METHOD)
                .forEach(n -> byMethod.put(n.label(), n.complexity().big()));
        assertEquals("O(n²)", byMethod.get("static long countMatches(List<Integer>, List<Integer>)"), byMethod.toString());
        assertEquals("O(n)", byMethod.get("static List<Integer> parse(String)"), byMethod.toString());
    }

    /** Все методы MiniLang получают оценку без исключений; распределение — в вывод теста. */
    @Test
    void everyMiniLangMethodGetsAnEstimate() throws Exception {
        String source = Files.readString(Path.of("../corpus/ours/MiniLang/MiniLang.java"), StandardCharsets.UTF_8);
        Graph map = new ProjectMapBuilder().build(SourceParser.parseOrThrow(source));
        Map<String, Integer> histogram = new TreeMap<>();
        map.nodes().stream().filter(n -> n.kind() == NodeKind.METHOD).forEach(n -> {
            assertNotNull(n.complexity(), n.id());
            histogram.merge(n.complexity().big(), 1, Integer::sum);
        });
        System.out.println("[оценка] MiniLang: " + histogram);
    }
}
