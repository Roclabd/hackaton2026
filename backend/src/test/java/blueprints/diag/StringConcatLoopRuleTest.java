package blueprints.diag;

import blueprints.parse.SourceParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** R9: склейка строки в цикле → StringBuilder. Заплатка проверяется запуском: вывод до и после один и тот же. */
class StringConcatLoopRuleTest {

    private static final String PLUS_ASSIGN = """
            import java.util.Scanner;
            public class Sample {
                public static void main(String[] args) {
                    Scanner in = new Scanner(System.in);
                    int n = in.hasNextInt() ? in.nextInt() : 0;
                    String s = "start:";
                    for (int i = 0; i < n; i++) {
                        if (i % 2 == 0) {
                            s += i + 1;
                        } else {
                            s += ',';
                        }
                        s += null;
                    }
                    System.out.println(s);
                }
            }
            """;

    private static final String CHAIN = """
            public class Sample {
                public static void main(String[] args) {
                    String report = "";
                    outer:
                    for (String word : new String[]{"a", "bb", "ccc", "stop", "dd"}) {
                        for (int k = 0; k < word.length(); k++) {
                            if (word.equals("stop")) break outer;
                            report = report + word.charAt(k) + k + 1;
                        }
                    }
                    System.out.println(report);
                }
            }
            """;

    @Test
    void plusAssignInLoopGetsEquivalentBuilderPatch() throws Exception {
        Finding finding = one(PLUS_ASSIGN);
        assertEquals(Finding.INEFFICIENCY, finding.category());
        assertNull(finding.growthBefore(), "n² — символы, а не шаги цикла: замер роста их не увидит");
        assertTrue(finding.why().contains("n²"), finding.why());
        Patch patch = finding.patches().getFirst();
        assertEquals(Patch.REFACTOR, patch.intent());
        assertTrue(patch.source().contains("sBuilder.append(i + 1);"), patch.source());
        assertTrue(patch.source().contains("s = sBuilder.toString();"), patch.source());
        assertSameOutput(PLUS_ASSIGN, patch.source(), "0", "1", "7", "", "abc");
    }

    /** s = s + a + b → append(a).append(b); выход по метке не мешает: s пишется после цикла. */
    @Test
    void concatenationChainWithLabeledBreakIsEquivalent() throws Exception {
        Patch patch = one(CHAIN).patches().getFirst();
        assertTrue(patch.source().contains(".append(word.charAt(k)).append(k).append(1);"), patch.source());
        assertSameOutput(CHAIN, patch.source(), "");
    }

    /** s может быть null: склейка даёт «nullx», а new StringBuilder(null) бросает NPE — находка есть, заплатки нет. */
    @ParameterizedTest
    @ValueSource(strings = {
            "void f(int n) { String s = null; for (int i = 0; i < n; i++) s += \"x\"; System.out.println(s); }",
            "void f(String start, int n) { String s = start; for (int i = 0; i < n; i++) s += \"x\"; System.out.println(s); }",
            "void f(int n) { String s = \"\"; s = null; for (int i = 0; i < n; i++) s += \"x\"; System.out.println(s); }"
    })
    void noBuilderPatchWhenStartMayBeNull(String body) {
        assertEquals(List.of(), one("class Sample { " + body + " }").patches());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // s читается внутри цикла
            "void f(int n) { String s = \"\"; for (int i = 0; i < n; i++) { if (s.length() > 3) break; s += i; } System.out.println(s); }",
            // дописывание в начало — не append
            "void f(int n) { String s = \"\"; for (int i = 0; i < n; i++) s = i + s; System.out.println(s); }",
            // s = a + s + b — тоже не в конец
            "void f(int n) { String s = \"\"; for (int i = 0; i < n; i++) s = \"<\" + s + \">\"; System.out.println(s); }",
            // поле, а не локальная переменная
            "String s = \"\"; void f(int n) { for (int i = 0; i < n; i++) s += i; }",
            // char[] склейкой печатается как объект, append — как символы
            "void f(char[] cs, int n) { String s = \"\"; for (int i = 0; i < n; i++) s += cs; System.out.println(s); }",
            // склейка в условии цикла, а не отдельным оператором
            "void f(int n) { String s = \"\"; for (int i = 0; i < n; s += i, i++) { } System.out.println(s); }",
            // цикл в другом блоке, чем объявление
            "void f(int n) { String s = \"\"; if (n > 0) { for (int i = 0; i < n; i++) s += i; } System.out.println(s); }",
            // не String
            "void f(int n) { int s = 0; for (int i = 0; i < n; i++) s += i; System.out.println(s); }"
    })
    void staysSilentWhenEquivalenceIsNotVisible(String body) {
        assertEquals(List.of(), r9("class Sample { " + body + " }"));
    }

    private static Finding one(String source) {
        List<Finding> findings = r9(source);
        assertEquals(1, findings.size(), findings.toString());
        return findings.getFirst();
    }

    private static List<Finding> r9(String source) {
        return new FindingsService().find(SourceParser.parseOrThrow(source), source).stream()
                .filter(f -> f.rule().equals(StringConcatLoopRule.RULE)).toList();
    }

    private static void assertSameOutput(String original, String patched, String... inputs) throws Exception {
        var before = ProgramRun.compile("Sample", original);
        var after = ProgramRun.compile("Sample", patched);
        for (String input : inputs) {
            assertEquals(ProgramRun.run(before, "Sample", input), ProgramRun.run(after, "Sample", input), "вход: " + input);
        }
    }
}
