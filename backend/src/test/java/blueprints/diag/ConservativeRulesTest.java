package blueprints.diag;

import blueprints.parse.SourceParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** R2–R8: основания находок, безопасные отказы и намерение каждой предлагаемой правки. */
class ConservativeRulesTest {
    @Test
    void r2RecognizesIndexedBoundaryAndProducesOnlyBugfix() {
        String source = "class Sample { void run(int[] a) { for (int i=0; i<=a.length; i++) { System.out.println(a[i]); } } }";
        Finding finding = one("R2", source);
        assertEquals(Finding.DEFECT, finding.category());
        assertNull(finding.growthBefore());
        assertNull(finding.growthAfter());
        assertTrue(finding.why().contains("Если"));
        assertBugfixParses(finding);
        assertTrue(finding.patches().getFirst().source().contains("i < a.length"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "for(int i=0; i<a.length; i++) System.out.println(a[i]);",
            "for(int i=0; i<=a.length; i++) System.out.println(i);",
            "for(int i=0; i<=a.length; i++) { if(i==a.length) break; System.out.println(a[i]); }",
            "for(int i=0; i<=a.length; i++) { if(i<a.length) System.out.println(a[i]); }",
            "for(int i=0; i<=a.length; i++) { i++; System.out.println(a[i]); }",
            "for(int i=0; i<=a.length; i++) { a=new int[100]; System.out.println(a[i]); }"
    })
    void r2DoesNotAccuseSafeOrUnrelatedLoops(String body) {
        assertNone("R2", "class Sample { void run(int[] a) { " + body + " } }");
    }

    @Test
    void r2DoesNotTreatUserLengthFieldAsArray() {
        assertNone("R2", "class Sample { int length; void run(Sample a) { for(int i=0;i<=a.length;i++) System.out.println(i); } }");
    }

    @Test
    void r3DeletesWholeUnreachableCycleAndKeepsLiveHelper() throws Exception {
        String source = """
                public class Sample {
                    public static void main(String[] args) { System.out.println(live()); }
                    private static int live() { return 42; }
                    private static void unused() { recursive(); }
                    private static void recursive() { unused(); }
                }
                """;
        List<Finding> findings = rule("R3", source);
        assertEquals(2, findings.size());
        Finding finding = findings.getFirst();
        assertEquals(Finding.DEAD_CODE, finding.category());
        Patch patch = finding.patches().getFirst();
        assertEquals(Patch.REFACTOR, patch.intent());
        assertFalse(patch.source().contains("unused()"));
        assertFalse(patch.source().contains("recursive()"));
        assertTrue(patch.source().contains("live()"));
        assertEquals(ProgramRun.run(ProgramRun.compile("Sample", source), "Sample", ""),
                ProgramRun.run(ProgramRun.compile("Sample", patch.source()), "Sample", ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "class Sample { public void run(){ used(); } private void used(){} }",
            "class Sample { Sample(){ used(); } private void used(){} }",
            "class Sample { { used(); } private void used(){} }",
            "class Sample { int x=used(); private int used(){ return 1; } }",
            "class Sample { public Runnable run(){ return this::used; } private void used(){} }",
            "class Sample { private void used(){} private void used(int x){} }",
            "class Sample { public void run(Object x){ x.used(); } private void used(){} }",
            "class Sample { public void run(){ getClass().getDeclaredMethods(); } private void used(){} }",
            "class Sample extends Parent { private void used(){} }",
            "class Sample { static class Nested {} private void used(){} }",
            "class Sample { @Deprecated private void used(){} }",
            "class Sample { private Object readResolve(){ return this; } }",
            "class Sample { public Class<?> type(){ return Sample.class; } private void used(){} }"
    })
    void r3PreservesCallsReferencesCallbacksAndUnknownCases(String source) {
        assertNone("R3", source);
    }

    @Test
    void r3SameLineGroupPatchRetainsCompilableSource() {
        Finding finding = rule("R3", "class Sample { private void a(){ b(); } private void b(){} public void run(){} }").getFirst();
        assertTrue(new SourceParser().parse(finding.patches().getFirst().source()).errors().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "int highest=0; for(int value:a) { if(value<highest) highest=value; } return highest;",
            "int max=0; for(int value:a) { if(max>value) max=value; } return max;",
            "int lowest=0; for(int value:a) { if(value>lowest) lowest=value; } return lowest;"
    })
    void r4ReportsOnlyAsPotentialIntentMismatch(String body) {
        Finding finding = one("R4", "class Sample { int run(int[] a){ " + body + " } }");
        assertTrue(finding.title().contains("Возможно"));
        assertTrue(finding.why().contains("не доказательство"));
        assertBugfixParses(finding);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "int highest=0; for(int value:a) { if(value>highest) highest=value; } return highest;",
            "int lowest=0; for(int value:a) { if(value<lowest) lowest=value; } return lowest;",
            "int result=0; for(int value:a) { if(value<result) result=value; } return result;",
            "int highest=0; for(int value:a) { if(value<highest) highest=highest+1; } return highest;"
    })
    void r4SkipsCorrectDirectionAndUnknownIntent(String body) {
        assertNone("R4", "class Sample { int run(int[] a){ " + body + " } }");
    }

    @Test
    void r5ExplainsMeanAssumptionAndZeroDivisor() {
        Finding finding = one("R5", "class Sample { double calculateAverage(int[] a,int sum){ return (double)sum/(a.length-1); } }");
        assertTrue(finding.why().contains("выборочной"));
        assertTrue(finding.why().contains("length == 1"));
        assertBugfixParses(finding);
        assertTrue(finding.patches().getFirst().source().contains("/(a.length)"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "class Sample { double average(int[] a,int sum){ return (double)sum/a.length; } }",
            "class Sample { double variance(int[] a,int sum){ return (double)sum/(a.length-1); } }",
            "class Sample { double average(int n,int sum){ return (double)sum/(n-1); } }"
    })
    void r5DoesNotLabelSampleVarianceOrUnknownLength(String source) { assertNone("R5", source); }

    @Test
    void r6FindsNonCompilingNumericAssignment() {
        Finding finding = one("R6", "class Sample { void run(int percent){ if(percent=100) System.out.println(percent); } }");
        assertTrue(finding.why().contains("не компилируется"));
        assertBugfixParses(finding);
        assertTrue(finding.patches().getFirst().source().contains("=="));
    }

    @Test
    void r6DoesNotPretendBooleanAssignmentIsInvalid() {
        Finding finding = one("R6", "class Sample { void run(boolean ready){ while(ready=false) {} } }");
        assertTrue(finding.why().contains("допустимо"));
        assertTrue(finding.patches().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "class Sample { void run(int percent){ if(percent==100) {} } }",
            "class Sample { void run(java.io.BufferedReader reader) throws Exception { String line; while((line=reader.readLine())!=null) {} } }",
            "class Sample { void run(){ int percent=100; System.out.println(percent); } }"
    })
    void r6SkipsComparisonsAndReadAssignmentIdiom(String source) { assertNone("R6", source); }

    @ParameterizedTest
    @ValueSource(strings = {"a==b", "a!=b"})
    void r7KnownStringsGetConditionalBugfix(String expression) {
        Finding finding = one("R7", "class Sample { boolean run(String a,String b){ return " + expression + "; } }");
        assertTrue(finding.why().contains("может быть намеренным"));
        assertBugfixParses(finding);
        assertTrue(finding.patches().getFirst().source().contains("java.util.Objects.equals(a, b)"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "class Sample { boolean run(Object a,Object b){ return a==b; } }",
            "class Sample { boolean run(String a){ return a==null; } }",
            "class Sample { boolean run(String a){ return a==a; } }",
            "class Sample { boolean run(){ return \"a\"==\"b\"; } }",
            "class Sample { boolean run(String a,String b){ return a.equals(b); } }",
            "class String {} class Sample { boolean run(String a,String b){ return a==b; } }",
            "class Sample { boolean run(String a, String b){ { int a=1; } return a==b; } }"
    })
    void r7SkipsUnknownTypesNullInternedLiteralsAndShadowing(String source) { assertNone("R7", source); }

    @Test
    void r7PatchUsesNullSafeEquality() throws Exception {
        Finding finding = one("R7", "public class Sample { public static void main(String[] args){ String a=new String(\"x\"), b=new String(\"x\"); System.out.println(a==b); } }");
        assertEquals("true" + System.lineSeparator(), ProgramRun.run(ProgramRun.compile("Sample", finding.patches().getFirst().source()), "Sample", ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "double d=a/b; return d;",
            "double d; d=a/b; return d;",
            "return a/b;",
            "double d=(double)(a/b); return d;"
    })
    void r8FindsIntegralDivisionBeforeWidening(String body) {
        Finding finding = one("R8", "class Sample { double run(int a,int b){ " + body + " } }");
        assertTrue(finding.conditions().stream().anyMatch(c -> c.contains("ненулевой")));
        assertBugfixParses(finding);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "class Sample { double run(int a,int b){ return (double)a/b; } }",
            "class Sample { double run(double a,int b){ return a/b; } }",
            "class Sample { int run(int a,int b){ return a/b; } }",
            "class Sample { double run(int a,int b){ double d=a/2.0; return d; } }",
            "class Sample { double run(Integer a,Integer b){ return a/b; } }",
            "class Sample { double run(int a,int b){ return unknown()/b; } }"
    })
    void r8SkipsFloatingOperandsIntegralDestinationAndUnknownTypes(String source) { assertNone("R8", source); }

    @Test
    void r8PatchProducesFractionalResult() throws Exception {
        Finding finding = one("R8", "public class Sample { public static void main(String[] args){ int a=3,b=2; double d=a/b; System.out.println(d); } }");
        assertEquals("1.5" + System.lineSeparator(), ProgramRun.run(ProgramRun.compile("Sample", finding.patches().getFirst().source()), "Sample", ""));
    }

    @Test
    void actualGradesExampleHasExpectedRulesDespiteCompileError() throws Exception {
        String source = Files.readString(Path.of("../corpus/traps/grades/Main.java"), StandardCharsets.UTF_8);
        Set<String> rules = new FindingsService().find(SourceParser.parseOrThrow(source), source).stream()
                .map(Finding::rule).collect(Collectors.toSet());
        assertEquals(Set.of("R2", "R3", "R4", "R5", "R6"), rules);
        assertTrue(source.contains("if (percent = 100)"));
    }

    @Test
    void findingsNeverMutateSharedAstAndIdsAreStable() {
        String source = "class Sample { void run(int[] a){ for(int i=0;i<=a.length;i++) System.out.println(a[i]); } private void unused(){} }";
        var unit = SourceParser.parseOrThrow(source);
        String before = unit.toString();
        var service = new FindingsService();
        List<String> ids = service.find(unit, source).stream().map(Finding::id).toList();
        assertEquals(before, unit.toString());
        assertEquals(ids, service.find(unit, source).stream().map(Finding::id).toList());
    }

    @Test
    void r7RejectsGenericStringTypeParameter() {
        assertNone("R7", "class Sample { <String> boolean same(String a,String b){ return a==b; } }");
    }

    @Test
    void r7DoesNotBorrowTypesFromLocalClassFields() {
        assertNone("R7", "class Sample { int a,b; boolean run(){ class Local { String a,b; } return a==b; } }");
    }

    @Test
    void r8DoesNotBorrowDoubleReturnTypeFromEnclosingMethodForLambda() {
        assertNone("R8", "class Sample { double run(int a,int b){ java.util.function.IntSupplier f=()->{ return a/b; }; return 0; } }");
    }

    @Test
    void r3RemovalPreservesObservableStackTraceLineNumbers() throws Exception {
        String source = """
                public class Sample {
                    private static void unused() {
                        System.out.println("unreachable");
                    }
                    public static void main(String[] args) {
                        System.out.println(new Throwable().getStackTrace()[0].getLineNumber());
                    }
                }
                """;
        Patch patch = one("R3", source).patches().getFirst();
        assertEquals(source.lines().count(), patch.source().lines().count());
        assertEquals(ProgramRun.run(ProgramRun.compile("Sample", source), "Sample", ""),
                ProgramRun.run(ProgramRun.compile("Sample", patch.source()), "Sample", ""));
    }

    private List<Finding> rule(String rule, String source) {
        return new FindingsService().find(SourceParser.parseOrThrow(source), source).stream().filter(f -> f.rule().equals(rule)).toList();
    }

    private Finding one(String rule, String source) {
        List<Finding> findings = rule(rule, source);
        assertEquals(1, findings.size(), findings.toString());
        return findings.getFirst();
    }

    private void assertNone(String rule, String source) { assertEquals(List.of(), rule(rule, source)); }

    private void assertBugfixParses(Finding finding) {
        assertFalse(finding.patches().isEmpty());
        for (Patch patch : finding.patches()) {
            assertEquals(Patch.BUGFIX, patch.intent());
            assertTrue(new SourceParser().parse(patch.source()).errors().isEmpty(), patch.source());
        }
    }
}



