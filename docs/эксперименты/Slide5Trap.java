import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Проверка ловушки из примера брифа (слайд 5):
 *
 *   for (Item a : items) for (Item b : items) if (a.id().equals(b.id())) merge(a, b);
 *
 * Сравниваем три версии на одних и тех же входах:
 *   ORIGINAL   — код из брифа, O(n²);
 *   NAIVE      — «индекс по id», как его обычно пишет ИИ: Map<id, Item>;
 *   EQUIVALENT — индекс Map<id, List<Item>> с сохранением порядка, O(n) при уникальных id.
 *
 * merge складывает количество — поэтому самослияние merge(a, a) удваивает qty.
 * Запуск: javac Slide5Trap.java && java Slide5Trap
 */
public class Slide5Trap {

    static final class Item {
        final String id;
        int qty;
        Item(String id, int qty) { this.id = id; this.qty = qty; }
        String id() { return id; }
        @Override public String toString() { return id + ":" + qty; }
    }

    static List<String> log;

    static void merge(Item a, Item b) {
        log.add(a.id + "<-" + b.id);
        a.qty += b.qty;
    }

    static void original(List<Item> items) {
        for (Item a : items) {
            for (Item b : items) {
                if (a.id().equals(b.id())) {
                    merge(a, b);
                }
            }
        }
    }

    static void naive(List<Item> items) {
        Map<String, Item> byId = new HashMap<>();
        for (Item it : items) {
            Item seen = byId.get(it.id());
            if (seen != null) merge(seen, it); else byId.put(it.id(), it);
        }
    }

    static void equivalent(List<Item> items) {
        Map<String, List<Item>> byId = new LinkedHashMap<>();
        for (Item b : items) byId.computeIfAbsent(b.id(), k -> new ArrayList<>()).add(b);
        for (Item a : items) {
            a.id().equals(a.id()); // NPE оригинала для id == null с тем же текстом (hashCode() дал бы другой текст)
            for (Item b : byId.get(a.id())) merge(a, b);
        }
    }

    interface Version { void run(List<Item> items); }

    static String runOnce(String name, Version v, String[][] input) {
        List<Item> items = new ArrayList<>();
        for (String[] row : input) items.add(new Item(row[0], Integer.parseInt(row[1])));
        log = new ArrayList<>();
        String outcome;
        try {
            v.run(items);
            outcome = "OK";
        } catch (RuntimeException e) {
            outcome = e.getClass().getSimpleName() + "(" + e.getMessage() + ")";
        }
        return outcome + " items=" + items + " merges=" + log.size() + " " + log;
    }

    public static void main(String[] args) {
        String[][][] inputs = {
            {{"A", "1"}, {"B", "2"}, {"C", "3"}},                // все id разные
            {{"A", "1"}, {"B", "2"}, {"A", "5"}},                // дубликат id
            {{"A", "1"}, {null, "2"}, {"B", "3"}},               // null-id в середине
            {{"A", "1"}, {"A", "1"}, {"A", "1"}, {"A", "1"}},    // все id одинаковые: вызовов merge всё равно n²
        };
        String[] names = {"ORIGINAL", "NAIVE", "EQUIVALENT"};
        Version[] versions = {Slide5Trap::original, Slide5Trap::naive, Slide5Trap::equivalent};
        for (int i = 0; i < inputs.length; i++) {
            System.out.println("=== вход " + (i + 1));
            String reference = null;
            for (int v = 0; v < versions.length; v++) {
                String result = runOnce(names[v], versions[v], inputs[i]);
                if (reference == null) reference = result;
                String verdict = v == 0 ? "эталон" : (result.equals(reference) ? "СОВПАДАЕТ" : "РАСХОЖДЕНИЕ");
                System.out.println(String.format("%-10s %-12s %s", names[v], verdict, result));
            }
        }
    }
}
