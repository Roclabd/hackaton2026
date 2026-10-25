package blueprints.heat;

import blueprints.graph.Blueprint;
import blueprints.graph.GraphNode;
import blueprints.inputs.InputGenerator;
import blueprints.project.Blueprints;
import blueprints.project.Project;
import blueprints.project.ProjectStore;
import blueprints.runner.RunResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Реальные прогоны: частоты привязаны к AST-узлам чертежа, а не к строкам исходника. */
class HeatServiceTest {
    private final ProjectStore store = new ProjectStore();
    private final HeatService service = new HeatService();

    @Test
    void matchDemoCountsOperatorEntriesExactly() throws Exception {
        String source = Files.readString(Path.of("../corpus/ours/MatchDemo/MatchDemo.java"), StandardCharsets.UTF_8);
        Project project = store.create("MatchDemo.java", source);
        String method = "M:MatchDemo#countMatches(List<Integer>,List<Integer>)";
        HeatResult result = service.measure(project, method, "1 2 3\n2 3 4");
        assertEquals(method, result.methodId());
        assertEquals(1, result.runs());
        assertEquals(Map.of("long total = 0;", 1L, "for (Integer x : a)", 1L,
                "for (Integer y : b)", 3L, "if (Objects.equals(x, y))", 9L,
                "total++;", 2L, "return total;", 1L), byLabel(project, method, result));
        assertEquals(9, result.maxCount());
        assertTrue(result.note().contains("не число итераций"));
        assertEquals(source, store.find(project.id()).orElseThrow().source());
    }

    @Test
    void distinctStatementsOnSameLineKeepTheirOwnCountersAndStableIds() {
        String source = "class Probe { static int f() { int x=0; for(int i=0;i<3;i++) if(i>0) x++; return x; } "
                + "public static void main(String[] a) { f(); } }";
        Project first = store.create("Probe.java", source);
        Project shifted = store.create("Probe.java", "\n\n" + source);
        String method = "M:Probe#f()";
        HeatResult result = service.measure(first, method, "");
        assertEquals(Map.of("int x=0;", 1L, "for (int i=0; i<3; i++)", 1L,
                "if (i>0)", 3L, "x++;", 2L, "return x;", 1L), byLabel(first, method, result));
        assertEquals(result.counts(), service.measure(shifted, method, "").counts());
    }

    @Test
    void methodNotCalledHasRealZeroCounts() {
        Project project = store.create("Probe.java", "class Probe { static int unused() { int x=1; return x; } "
                + "public static void main(String[] a) {} }");
        HeatResult result = service.measure(project, "M:Probe#unused()", "");
        assertEquals(2, result.counts().size());
        assertTrue(result.counts().values().stream().allMatch(value -> value == 0));
        assertEquals(0, result.maxCount());
    }

    @Test
    void generatedInputsAreAllCountedAndExplicitEmptyInputIsOneRun() {
        String source = "import java.util.Scanner; class Probe { static void f() { "
                + "Scanner s=new Scanner(System.in); int x=s.nextInt(); if(x>0) x++; } "
                + "public static void main(String[] a) { f(); } }";
        Project project = store.create("Probe.java", source);
        HeatResult generated = service.measure(project, "M:Probe#f()", null);
        assertEquals(new InputGenerator().generate(project.fileName(), source).size(), generated.runs());
        assertEquals(generated.runs(), byLabel(project, "M:Probe#f()", generated).get("int x=s.nextInt();"));
        assertTrue(generated.note().contains("Прогонов с исключением"), generated.note());
        HeatResult explicit = service.measure(project, "M:Probe#f()", "");
        assertEquals(1, explicit.runs());
        assertTrue(explicit.note().contains("Прогонов с исключением"));
    }

    @Test
    void exceptionRetainsVisitsBeforeThrow() {
        Project project = store.create("Probe.java", "class Probe { static void f() { int x=1; "
                + "throw new IllegalArgumentException(\"bad\"); } public static void main(String[] a) { f(); } }");
        HeatResult result = service.measure(project, "M:Probe#f()", "");
        assertEquals(List.of(1L, 1L), result.counts().values().stream().toList());
        assertTrue(result.note().contains("Прогонов с исключением или ненулевым кодом выхода: 1"));
        assertTrue(result.note().contains("до завершения"));
    }

    @Test
    void nonzeroExitRetainsVisitsBeforeExit() {
        Project project = store.create("Probe.java", "class Probe { public static void main(String[] a) { "
                + "System.exit(7); int x=1; } }");
        HeatResult result = service.measure(project, "M:Probe#main(String[])", "");
        assertEquals(Map.of("System.exit(7);", 1L, "int x=1;", 0L),
                byLabel(project, "M:Probe#main(String[])", result));
        assertTrue(result.note().contains("Прогонов с исключением или ненулевым кодом выхода: 1"));
    }

    @Test
    void rawNodesAreOmittedRatherThanReportedAsZero() {
        Project project = store.create("Probe.java", "class Probe { static void f() { "
                + "class Local { int x; } int x=1; } public static void main(String[] a) { f(); } }");
        String method = "M:Probe#f()";
        HeatResult result = service.measure(project, method, "");
        assertEquals(1, result.counts().size());
        assertEquals(List.of(1L), result.counts().values().stream().toList());
        assertTrue(result.note().contains("Не измерено узлов: 1"));
    }

    @Test
    void labelsAndContinueKeepTheirMeaning() {
        Project project = store.create("Probe.java", "class Probe { static int f() { int n=0; outer: "
                + "for(int i=0;i<3;i++) { if(i==1) continue outer; n++; } return n; } "
                + "public static void main(String[] a) { if(f()!=2) throw new Error(); } }");
        HeatResult result = service.measure(project, "M:Probe#f()", "");
        Map<String, Long> labels = byLabel(project, "M:Probe#f()", result);
        assertEquals(1, labels.get("for (int i=0; i<3; i++)"));
        assertEquals(3, labels.get("if (i==1)"));
        assertEquals(1, labels.get("continue outer;"));
        assertEquals(2, labels.get("n++;"));
        assertFalse(result.note().contains("Прогонов с исключением"));
    }

    @Test
    void runnerLocaleAndEncodingAreFixed() {
        Project project = store.create("Probe.java", "class Probe { static int f() { "
                + "if(java.util.Locale.getDefault().toString().equals(\"en_US\") "
                + "&& java.nio.charset.Charset.defaultCharset().name().equals(\"UTF-8\")) return 1; return 0; } "
                + "public static void main(String[] a) { f(); } }");
        HeatResult result = service.measure(project, "M:Probe#f()", "");
        Map<String, Long> labels = byLabel(project, "M:Probe#f()", result);
        assertEquals(1, labels.get("return 1;"));
        assertEquals(0, labels.get("return 0;"));
    }

    @Test
    void rejectsNoMainAndStackSensitivePrograms() {
        assertError("class Probe { static int f(){return 1;} }", "M:Probe#f()", "main");
        assertError("class Probe { public static void main(String[] a){ Thread.dumpStack(); } }",
                "M:Probe#main(String[])", "стек");
        assertError("class Probe { static class __BlueprintsCoverage {} public static void main(String[] a) {} }",
                "M:Probe#main(String[])", "служебного класса");
    }

    @Test
    void timeoutAndTruncationDoNotBecomeSuccessfulPartialMaps() {
        Project timeout = store.create("Probe.java", "class Probe { public static void main(String[] a) { while(true) {} } }");
        HeatService.RequestException timedOut = assertThrows(HeatService.RequestException.class,
                () -> new HeatService(Duration.ofMillis(100)).measure(timeout, "M:Probe#main(String[])", ""));
        assertEquals(422, timedOut.status());
        assertTrue(timedOut.getMessage().contains("лимит времени"), timedOut.getMessage());
        assertError("class Probe { public static void main(String[] a) { System.out.print(\"x\".repeat(1100000)); } }",
                "M:Probe#main(String[])", "обрезан");
    }

    @Test
    void missingMalformedAndOverflowedCountersAreUnreliable() {
        assertNotNull(HeatService.invalid(result(null), 1));
        assertNotNull(HeatService.invalid(result(new int[0]), 1));
        assertNotNull(HeatService.invalid(result(new int[]{-1}), 1));
        assertNull(HeatService.invalid(result(new int[]{0}), 1));
        assertNotNull(HeatService.invalid(new RunResult("", "", null, null, false, 0, true, false), 1));
    }

    @Test
    void deadWorkerDoesNotBecomeAnUnvisitedMethod() {
        assertError("class Probe { static int unused(){ return 1; } public static void main(String[] a) { "
                + "Runtime.getRuntime().halt(0); } }", "M:Probe#unused()", "полный результат");
    }

    @Test
    void constructorDelegationIsOmittedAndFollowingStatementIsCounted() {
        Project project = store.create("Probe.java", "class Probe { Probe(){ this(1); int y=2; } Probe(int x){} "
                + "public static void main(String[] a){ new Probe(); } }");
        HeatResult result = service.measure(project, "M:Probe#<init>()", "");
        assertEquals(Map.of("int y=2;", 1L), byLabel(project, "M:Probe#<init>()", result));
        assertTrue(result.note().contains("Не измерено узлов: 1"));
    }

    private static RunResult result(int[] hits) {
        return new RunResult("", "", 0, null, false, 0, false, false, hits);
    }

    private void assertError(String source, String method, String contains) {
        HeatService.RequestException error = assertThrows(HeatService.RequestException.class,
                () -> service.measure(store.create("Probe.java", source), method, ""));
        assertEquals(422, error.status());
        assertTrue(error.getMessage().contains(contains), error.getMessage());
    }

    private static Map<String, Long> byLabel(Project project, String method, HeatResult result) {
        Blueprint blueprint = new Blueprints().forMethod(project, method).orElseThrow();
        return blueprint.graph().nodes().stream().filter(node -> result.counts().containsKey(node.id()))
                .collect(Collectors.toMap(GraphNode::label, node -> result.counts().get(node.id())));
    }
}
