import java.util.*;
public class D2BreakInside {
    static final class Item { final String id; int qty; Item(String id, int qty) { this.id = id; this.qty = qty; } String id() { return id; } }
    static void merge(Item a, Item b) { System.out.println(a.id + "<-" + b.qty); a.qty += b.qty; }
    static void dedup(List<Item> items) {
        for (Item a : items) {
            for (Item b : items) {
                if (a.id().equals(b.id())) {
                    merge(a, b);
                    if (a.qty > 5) break;
                }
            }
        }
    }
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        String line = in.hasNextLine() ? in.nextLine() : "";
        List<Item> items = new ArrayList<>();
        int q = 1;
        for (String t : line.trim().split("\\s+")) if (!t.isEmpty()) items.add(new Item(t.equals("null") ? null : t, q++));
        dedup(items);
        for (Item it : items) System.out.println(it.id + ":" + it.qty);
    }
}
