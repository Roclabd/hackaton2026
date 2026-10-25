import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BaitR15 {

    // ПРИМАНКА: одна сторона — int, поэтому Integer распаковывается и сравниваются значения.
    static boolean matchesInt(Integer boxed, int plain) {
        return boxed == plain;
    }

    // ПРИМАНКА: сравнение Integer с null — проверка ссылки по назначению.
    static boolean missing(Map<String, Integer> m, String k) {
        Integer v = m.get(k);
        return v == null;
    }

    // ПРИМАНКА: equals и Objects.equals сравнивают значения.
    static boolean same(Integer a, Integer b) {
        return a.equals(b) || Objects.equals(a, b);
    }

    // ПРИМАНКА: явная распаковка intValue()/longValue() с обеих сторон.
    static boolean sameExplicit(Integer a, Long b) {
        return a.intValue() == b.longValue();
    }

    // ПРИМАНКА: Integer.compare(...) == 0 — сравнение примитивов.
    static boolean sameCompare(Integer a, Integer b) {
        return Integer.compare(a, b) == 0;
    }

    // ПРИМАНКА: Long против литерала long — литерал примитивный, Long распаковывается.
    static boolean isZero(Long total) {
        return total != null && total == 0L;
    }

    // ПРИМАНКА: обе стороны — примитивы int после явного присваивания из Integer.
    static int countEqual(List<Integer> a, List<Integer> b) {
        int n = 0;
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            int x = a.get(i);
            int y = b.get(i);
            if (x == y) n++;
        }
        return n;
    }

    // ПРИМАНКА: «==» между Integer и выражением с арифметикой — арифметика уже дала int.
    static boolean nextOf(Integer a, Integer b) {
        return a + 1 == b;
    }

    public static void main(String[] args) {
        System.out.println(matchesInt(1000, 1000));
        System.out.println(missing(Map.of("a", 1), "b"));
        System.out.println(same(1000, 1000));
        System.out.println(sameExplicit(5, 5L));
        System.out.println(sameCompare(1000, 1000));
        System.out.println(isZero(0L));
        System.out.println(countEqual(List.of(1000, 2), List.of(1000, 3)));
        System.out.println(nextOf(999, 1000));
    }
}
