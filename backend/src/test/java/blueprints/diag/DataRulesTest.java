package blueprints.diag;

import blueprints.parse.SourceParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** R4 в цикле с индексом, R10–R14: что находят и где молчат. Источники — типичные места ИИ-кода. */
class DataRulesTest {

    // ---------- R4: цикл с индексом и нейтральное имя ----------

    @Test
    void r4FindsMinWithWrongSignInIndexedLoop() {
        Finding f = one("R4", "class S { static int min(int[] v){ int best=v[0]; for(int i=1;i<v.length;i++){ if(v[i] > best){ best=v[i]; } } return best; } }");
        assertTrue(f.why().contains("минимум"), f.why());
        assertBugfix(f);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "class S { static int min(int[] v){ int best=v[0]; for(int i=1;i<v.length;i++){ if(v[i] < best){ best=v[i]; } } return best; } }",
            "class S { static int total(int[] v){ int best=v[0]; for(int i=1;i<v.length;i++){ if(v[i] > best){ best=v[i]; } } return best; } }",
            "class S { static int min(double[] v){ int best=0; for(int i=1;i<v.length;i++){ if(i > best){ best=i; } } return best; } }"
    })
    void r4SilentWhenDirectionMatchesOrIntentUnknown(String source) { none("R4", source); }

    // ---------- R10: поиск в списке внутри цикла ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "import java.util.*; class S { static int u(int[] v){ List<Integer> seen=new ArrayList<>(); for(int x: v){ if(!seen.contains(x)) seen.add(x); } return seen.size(); } }",
            "import java.util.*; class S { List<String> names=new ArrayList<>(); int f(String[] q){ int n=0; for(String s: q){ if(names.indexOf(s)>=0) n++; } return n; } }"
    })
    void r10FindsListSearchInLoop(String source) {
        Finding f = one("R10", source);
        assertEquals(Finding.INEFFICIENCY, f.category());
        assertEquals("n²", f.growthBefore());
        assertTrue(f.patches().isEmpty(), "заплатки нет — только находка");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Set — уже за одно обращение
            "import java.util.*; class S { static int u(int[] v){ Set<Integer> seen=new HashSet<>(); for(int x: v){ if(!seen.contains(x)) seen.add(x); } return seen.size(); } }",
            // строка, а не список
            "class S { static int f(String[] q, String text){ int n=0; for(String s: q){ if(text.contains(s)) n++; } return n; } }",
            // вне цикла
            "import java.util.*; class S { static boolean f(List<String> l){ return l.contains(\"a\"); } }",
            // свой список на каждой итерации
            "import java.util.*; class S { static int f(int[] v){ int n=0; for(int x: v){ List<Integer> l=new ArrayList<>(); if(l.contains(x)) n++; } return n; } }",
            // цикл меню: одна проверка на команду
            "import java.util.*; class S { static void f(List<String> l, Scanner in){ while(true){ String c=in.nextLine(); if(l.contains(c)) break; } } }",
            // свой класс List
            "class S { static class List { boolean contains(Object o){ return true; } } static int f(List l, int[] v){ int n=0; for(int x: v){ if(l.contains(x)) n++; } return n; } }"
    })
    void r10SilentOnSetsStringsMenusAndOwnTypes(String source) { none("R10", source); }

    // ---------- R11: сортировка в цикле ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "import java.util.*; class S { static void f(List<String> src){ List<String> out=new ArrayList<>(); for(String s: src){ out.add(s); out.sort(Comparator.naturalOrder()); } } }",
            "import java.util.*; class S { static void f(List<Integer> l, int n){ for(int i=0;i<n;i++){ l.add(i); Collections.sort(l); } } }"
    })
    void r11FindsSortInLoop(String source) {
        Finding f = one("R11", source);
        assertEquals(Finding.INEFFICIENCY, f.category());
        assertTrue(f.patches().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "import java.util.*; class S { static void f(List<String> l){ l.sort(Comparator.naturalOrder()); } }",
            "import java.util.*; class S { static void f(List<String> l, Scanner in){ while(in.hasNextLine()){ l.add(in.nextLine()); l.sort(null); } } }",
            "class S { static class Deck { void sort(){} } static void f(Deck d, int n){ for(int i=0;i<n;i++){ d.sort(); } } }",
            // каждая строка сортируется один раз — n log n, а не n² log n
            "import java.util.*; class S { static void f(int[][] m){ for(int[] row: m){ Arrays.sort(row); } } }"
    })
    void r11SilentOutsideLoopsInMenusAndOnOwnTypes(String source) { none("R11", source); }

    // ---------- R12: экстремум с нуля ----------

    @Test
    void r12FindsMaxStartingFromZero() {
        Finding f = one("R12", "class S { static int max(int[] v){ int best=0; for(int x: v){ if(x > best){ best=x; } } return best; } }");
        assertEquals(Finding.DEFECT, f.category());
        assertBugfix(f);
        assertTrue(f.patches().getFirst().source().contains("int best=Integer.MIN_VALUE"), f.patches().getFirst().source());
    }

    @Test
    void r12FindsMinStartingFromZeroByVariableName() {
        Finding f = one("R12", "class S { static double f(double[] v){ double minimum=0; for(int i=0;i<v.length;i++){ if(v[i] < minimum) minimum=v[i]; } return minimum; } }");
        assertTrue(f.patches().getFirst().source().contains("Double.POSITIVE_INFINITY"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // длины не бывают отрицательными — 0 правильный старт
            "class S { static int f(String[] w){ int maxLen=0; for(String s: w){ if(s.length() > maxLen) maxLen=s.length(); } return maxLen; } }",
            // старт с первого элемента
            "class S { static int max(int[] v){ int best=v[0]; for(int x: v){ if(x > best) best=x; } return best; } }",
            // имя без направления и метод без направления
            "class S { static int f(int[] v){ int best=0; for(int x: v){ if(x > best) best=x; } return best; } }",
            // ноль, но значение не накапливается сравнением
            "class S { static int max(int[] v){ int sum=0; for(int x: v){ sum+=x; } return sum; } }"
    })
    void r12SilentOnLengthsFirstElementAndUnknownIntent(String source) { none("R12", source); }

    // ---------- R13: распаковка null из Map.get ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "import java.util.*; class S { Map<String,Integer> c=new HashMap<>(); void f(String w){ c.put(w, c.get(w) + 1); } }",
            "import java.util.*; class S { static int f(Map<String,Integer> m, String k){ int v = m.get(k); return v; } }",
            "import java.util.*; class S { Map<String,Double> vars=new HashMap<>(); double f(String k){ Double v = vars.get(k); return v; } }",
            "import java.util.*; class S { static double f(Map<String,Double> m, String k){ return m.get(k); } }"
    })
    void r13FindsUnboxingOfMissingKey(String source) {
        Finding f = one("R13", source);
        assertEquals(Finding.DEFECT, f.category());
        assertBugfix(f);
        assertTrue(f.patches().getFirst().source().contains("getOrDefault("), f.patches().getFirst().source());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // карта заполнена заранее
            "import java.util.*; class S { static Map<String,Integer> f(String[] all, String[] ws){ Map<String,Integer> c=new HashMap<>(); for(String a: all) c.put(a, 0); for(String w: ws) c.put(w, c.get(w) + 1); return c; } }",
            // containsKey
            "import java.util.*; class S { static int f(Map<String,Integer> m, String k){ if(!m.containsKey(k)) return 0; return m.get(k); } }",
            // проверка на null
            "import java.util.*; class S { static double f(Map<String,Double> m, String k){ Double v = m.get(k); if (v == null) return 0; return v; } }",
            // склейка строки — не распаковка
            "import java.util.*; class S { static String f(Map<String,Integer> m, String k){ return \"Итого: \" + m.get(k); } }",
            // объект, а не число
            "import java.util.*; class S { static int f(Map<String,String> m, String k){ return m.get(k).length(); } }",
            // результат остаётся обёрткой
            "import java.util.*; class S { static Integer f(Map<String,Integer> m, String k){ return m.get(k); } }"
    })
    void r13SilentWhenGuardedPrefilledOrNotUnboxed(String source) { none("R13", source); }

    // ---------- R14: == между Integer ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "class S { static boolean f(Integer a, Integer b){ return a == b; } }",
            "import java.util.*; class S { static boolean f(List<Integer> xs, int i, int j){ return xs.get(i) == xs.get(j); } }",
            "import java.util.*; class S { Map<String,Long> m=new HashMap<>(); boolean f(String a, String b){ return m.get(a) != m.get(b); } }"
    })
    void r14FindsReferenceComparisonOfBoxedNumbers(String source) {
        Finding f = one("R14", source);
        assertBugfix(f);
        assertTrue(f.patches().getFirst().source().contains("java.util.Objects.equals("), f.patches().getFirst().source());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // распаковка: int против Integer сравнивает числа
            "class S { static boolean f(int a, Integer b){ return a == b; } }",
            // сравнение с null — проверка ссылки, так и надо
            "class S { static boolean f(Integer a){ return a == null; } }",
            // уже equals
            "class S { static boolean f(Integer a, Integer b){ return a.equals(b); } }",
            // разные обёртки — не тот случай (ещё и не скомпилируется у javac)
            "class S { static boolean f(int a, int b){ return a == b; } }",
            // свой класс Integer
            "class S { static class Integer { } static boolean f(Integer a, Integer b){ return a == b; } }",
            // List<String> — это R7, не R14
            "import java.util.*; class S { static boolean f(List<String> xs){ return xs.get(0) == xs.get(1); } }"
    })
    void r14SilentOnUnboxingNullEqualsAndOwnTypes(String source) { none("R14", source); }

    // ---------- R6, R7: намеренные идиомы из JDK не обвиняем ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "class S { static boolean f(int x){ boolean neg; if ((neg = (x < 0))) { return neg; } return false; } }",
            "class S { static boolean f(int a, int b){ boolean eq; if (eq = (a == b)) { return eq; } return false; } }"
    })
    void r6SilentOnParenthesizedAssignmentAndStoredComparison(String source) { none("R6", source); }

    @Test
    void r6StillFindsNumericAssignment() {
        assertEquals(1, rule("R6", "class S { static void f(int p){ if (p = 100) { } } }").size());
    }

    @Test
    void r7SilentOnIdentityShortcutBeforeEquals() {
        none("R7", "class S { static boolean f(String s, String t){ if (s == t) return true; return s != null && s.equals(t); } }");
    }

    @Test
    void r7StillFindsPlainStringComparison() {
        assertEquals(1, rule("R7", "class S { static boolean f(String s, String t){ return s == t; } }").size());
    }

    private static List<Finding> rule(String rule, String source) {
        return new FindingsService().find(SourceParser.parseOrThrow(source), source).stream()
                .filter(f -> f.rule().equals(rule)).toList();
    }

    private static Finding one(String rule, String source) {
        List<Finding> findings = rule(rule, source);
        assertEquals(1, findings.size(), findings.toString());
        return findings.getFirst();
    }

    private static void none(String rule, String source) { assertEquals(List.of(), rule(rule, source)); }

    private static void assertBugfix(Finding finding) {
        assertFalse(finding.patches().isEmpty());
        for (Patch patch : finding.patches()) {
            assertEquals(Patch.BUGFIX, patch.intent());
            assertTrue(new SourceParser().parse(patch.source()).errors().isEmpty(), patch.source());
        }
    }
}
