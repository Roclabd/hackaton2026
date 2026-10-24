import java.util.*;
public class D1MutatingMerge {
    static final class Item { final String id; int qty; Item(String id, int qty) { this.id = id; this.qty = qty; } String id() { return id; } }
    static List<Item> items = new ArrayList<>();
    // merge удаляет дубликат из общего списка — внутренний цикл видит изменение
    static void merge(Item a, Item b) {
        System.out.println("merge " + a.id + " " + b.qty);
        if (a != b) { a.qty += b.qty; items.remove(b); }
    }
    static void dedup() {
        for (Item a : items) {
            for (Item b : items) {
                if (a.id().equals(b.id())) {
                    merge(a, b);
                }
            }
        }
    }
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        String line = in.hasNextLine() ? in.nextLine() : "";
        int q = 1;
        for (String t : line.trim().split("\\s+")) if (!t.isEmpty()) items.add(new Item(t, q++));
        try { dedup(); } catch (ConcurrentModificationException e) { System.out.println("CME"); }
        for (Item it : items) System.out.println(it.id + ":" + it.qty);
    }
}
