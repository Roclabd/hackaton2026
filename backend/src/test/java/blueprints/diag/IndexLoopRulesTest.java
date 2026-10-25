package blueprints.diag;

import blueprints.parse.SourceParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** R15–R17: перебор, спрятанный в циклах со счётчиком, в LinkedList.get и в своём методе поиска. */
class IndexLoopRulesTest {

    // ---------- R15: вложенный цикл по индексам со сравнением элементов ----------

    @ParameterizedTest
    @ValueSource(strings = {
            // дубликаты: j < i
            "class S { static int d(int[] a){ int n=0; for(int i=0;i<a.length;i++){ boolean s=false; for(int j=0;j<i;j++){ if(a[j]==a[i]) s=true; } if(s) n++; } return n; } }",
            // мода
            "class S { static int m(int[] a){ int best=0; for(int i=0;i<a.length;i++){ int c=0; for(int j=0;j<a.length;j++){ if(a[j]==a[i]) c++; } if(c>best) best=c; } return best; } }",
            // поиск значения v в массиве
            "import java.util.*; class S { static List<Integer> f(int[] a,int max){ List<Integer> r=new ArrayList<>(); for(int v=0;v<max;v++){ boolean p=false; for(int i=0;i<a.length;i++){ if(a[i]==v) p=true; } if(!p) r.add(v); } return r; } }",
            // списки и equals
            "import java.util.*; class S { static int f(List<String> a){ int n=0; for(int i=0;i<a.size();i++){ for(int j=i+1;j<a.size();j++){ if(a.get(i).equals(a.get(j))) n++; } } return n; } }",
            // внешний for-each, внутренний со счётчиком
            "class S { static int f(int[] a,int[] b){ int n=0; for(int x: a){ for(int j=0;j<b.length;j++){ if(b[j]==x) n++; } } return n; } }",
            // значение внешнего элемента сохранено в переменную
            "class S { static int f(int[] a){ int n=0; for(int i=0;i<a.length;i++){ int x=a[i]; for(int j=0;j<a.length;j++){ if(a[j]==x) n++; } } return n; } }"
    })
    void r15FindsPairwiseComparisonInIndexLoops(String source) {
        Finding f = one("R15", source);
        assertEquals(Finding.INEFFICIENCY, f.category());
        assertEquals("n²", f.growthBefore());
        assertTrue(f.patches().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // константная внутренняя граница — линейно
            "class S { static int f(char[] s){ char[] v={'a','e'}; int n=0; for(int i=0;i<s.length;i++){ for(int j=0;j<26;j++){ if(s[i]==(char)('a'+j)) n++; } } return n; } }",
            "class S { static final int K=5; static int f(int[] a,int[] b){ int n=0; for(int i=0;i<a.length;i++){ for(int j=0;j<K;j++){ if(a[i]==b[j]) n++; } } return n; } }",
            // обход матрицы
            "class S { static boolean f(int[][] m){ for(int i=0;i<m.length;i++){ for(int j=0;j<m.length;j++){ if(m[i][j]==m[j][i]) return false; } } return true; } }",
            // сравнение индексов, а не элементов
            "class S { static int f(int[] a){ int s=0; for(int i=0;i<a.length;i++){ for(int j=0;j<a.length;j++){ if(i==j) continue; s+=a[i]*a[j]; } } return s; } }",
            // сравнение порядка (сортировка), а не равенства
            "class S { static void f(int[] a){ for(int i=0;i<a.length;i++){ for(int j=0;j<a.length-1;j++){ if(a[j]>a[j+1]){ int t=a[j]; a[j]=a[j+1]; a[j+1]=t; } } } } }",
            // пара for-each — это R1
            "class S { static int f(int[] a){ int n=0; for(int x: a){ for(int y: a){ if(x==y) n++; } } return n; } }",
            // сравнение с константой
            "class S { static int f(int[] a){ int n=0; for(int i=0;i<a.length;i++){ for(int j=0;j<a.length;j++){ if(a[j]==0) n++; } } return n; } }"
    })
    void r15SilentOnLinearMatricesIndicesAndOrder(String source) { none("R15", source); }

    // ---------- R16: LinkedList.get(i) в цикле ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "import java.util.*; class S { static int f(LinkedList<Integer> l){ int s=0; for(int i=0;i<l.size();i++){ s+=l.get(i); } return s; } }",
            "import java.util.*; class S { LinkedList<String> l=new LinkedList<>(); int f(){ int n=0; for(int i=0;i<l.size();i++){ n+=l.get(i).length(); } return n; } }"
    })
    void r16FindsLinkedListGetByIndex(String source) {
        Finding f = one("R16", source);
        assertEquals("n²", f.growthBefore());
        assertEquals("n", f.growthAfter());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "import java.util.*; class S { static int f(ArrayList<Integer> l){ int s=0; for(int i=0;i<l.size();i++){ s+=l.get(i); } return s; } }",
            "import java.util.*; class S { static int f(List<Integer> l){ int s=0; for(int i=0;i<l.size();i++){ s+=l.get(i); } return s; } }",
            "import java.util.*; class S { static int f(LinkedList<Integer> l){ int s=0; for(int x: l){ s+=x; } return s+l.get(0); } }",
            "import java.util.*; class S { static int f(LinkedList<Integer> l){ int s=0; for(int i=0;i<3;i++){ s+=l.get(i); } return s; } }"
    })
    void r16SilentOnArrayListsIteratorsAndConstantLoops(String source) { none("R16", source); }

    // ---------- R17: свой метод поиска в цикле ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "class S { static int find(int[] a,int t){ for(int i=0;i<a.length;i++){ if(a[i]==t) return i; } return -1; } static int c(int[] a,int[] q){ int n=0; for(int i=0;i<q.length;i++){ if(find(a,q[i])!=-1) n++; } return n; } }",
            "import java.util.*; class S { static int idx(List<String> l,String w){ for(int i=0;i<l.size();i++){ if(l.get(i).equals(w)) return i; } return -1; } static void c(List<String> ws, List<String> u){ for(String w: ws){ if(idx(u,w)==-1) u.add(w); } } }"
    })
    void r17FindsOwnLinearSearchInLoop(String source) {
        Finding f = one("R17", source);
        assertEquals("n²", f.growthBefore());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // метод — сумма, а не поиск
            "class S { static int sum(int[] a){ int s=0; for(int i=0;i<a.length;i++){ s+=a[i]; } return s; } static int c(int[][] m){ int n=0; for(int i=0;i<m.length;i++){ n+=sum(m[i]); } return n; } }",
            // поиск вне цикла
            "class S { static int find(int[] a,int t){ for(int i=0;i<a.length;i++){ if(a[i]==t) return i; } return -1; } static int c(int[] a){ return find(a,3); } }",
            // перегрузки — не угадываем
            "class S { static int find(int[] a,int t){ for(int i=0;i<a.length;i++){ if(a[i]==t) return i; } return -1; } static int find(long[] a,long t){ return -1; } static int c(int[] a,int[] q){ int n=0; for(int i=0;i<q.length;i++){ if(find(a,q[i])!=-1) n++; } return n; } }",
            // цикл с константной границей
            "class S { static int find(int[] a,int t){ for(int i=0;i<a.length;i++){ if(a[i]==t) return i; } return -1; } static int c(int[] a){ int n=0; for(int i=0;i<3;i++){ if(find(a,i)!=-1) n++; } return n; } }"
    })
    void r17SilentWithoutSearchOrLoop(String source) { none("R17", source); }

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
}
