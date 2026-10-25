import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaitR13 {

    enum Day { MON, TUE, WED }

    private final Map<String, Integer> load = new HashMap<>();

    BaitR13() {
        for (Day d : Day.values()) load.put(d.name(), 0);
    }

    // ПРИМАНКА: перед get стоит containsKey у той же карты.
    static int countOf(Map<String, Integer> counts, String key) {
        if (!counts.containsKey(key)) return 0;
        int c = counts.get(key);
        return c;
    }

    // ПРИМАНКА: getOrDefault никогда не вернёт null.
    static int safeCount(Map<String, Integer> counts, String key) {
        int c = counts.getOrDefault(key, 0);
        return c + 1;
    }

    // ПРИМАНКА: merge вместо get + 1 — распаковки null нет.
    static Map<String, Integer> tally(List<String> words) {
        Map<String, Integer> counts = new HashMap<>();
        for (String w : words) counts.merge(w, 1, Integer::sum);
        return counts;
    }

    // ПРИМАНКА: карта заполнена нулями выше по методу — get гарантированно не null.
    static int busiest(List<String> visits) {
        Map<String, Integer> perDay = new HashMap<>();
        for (Day d : Day.values()) perDay.put(d.name(), 0);
        for (String v : visits) perDay.put(v, perDay.get(v) + 1);
        return perDay.get("MON");
    }

    // ПРИМАНКА: результат идёт в Integer и проверяется на null до использования.
    static String describe(Map<String, Integer> prices, String item) {
        Integer p = prices.get(item);
        if (p == null) return item + ": нет цены";
        return item + ": " + (p * 2);
    }

    // ПРИМАНКА: get участвует только в склейке строки — печатается «null», распаковки нет.
    static String show(Map<String, Integer> prices, String item) {
        return "price=" + prices.get(item);
    }

    // ПРИМАНКА: карта load заполнена в конструкторе для всех Day — ключ вида Day.name() всегда есть.
    int loadOf(Day day) {
        int value = load.get(day.name());
        return value;
    }

    // ПРИМАНКА: EnumMap не входит в проверяемые карты, а Integer-результат проверяется на null.
    static int shifts(EnumMap<Day, Integer> plan, Day d) {
        Integer s = plan.get(d);
        return s != null ? s : 0;
    }

    public static void main(String[] args) {
        Map<String, Integer> m = new HashMap<>(Map.of("a", 2));
        System.out.println(countOf(m, "a") + safeCount(m, "z"));
        System.out.println(tally(List.of("x", "x")));
        System.out.println(busiest(List.of("MON", "TUE")));
        System.out.println(describe(m, "b") + show(m, "c"));
        System.out.println(new BaitR13().loadOf(Day.WED));
        System.out.println(shifts(new EnumMap<>(Day.class), Day.MON));
    }
}
