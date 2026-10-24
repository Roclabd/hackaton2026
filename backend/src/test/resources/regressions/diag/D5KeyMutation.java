import java.util.*;
public class D5KeyMutation {
    static final class Item { String id; int qty; Item(String id, int qty) { this.id = id; this.qty = qty; } String id() { return id; } }
    // merge меняет ключ второго элемента: оригинал потом уже не считает его равным
    static void merge(Item a, Item b) { if (a != b) { a.qty += b.qty; b.id = b.id + "*"; } }
    static void dedup(List<Item> items) {
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
        List<Item> items = new ArrayList<>();
        int q = 1;
        for (String t : line.trim().split("\\s+")) if (!t.isEmpty()) items.add(new Item(t, q++));
        dedup(items);
        for (Item it : items) System.out.println(it.id + ":" + it.qty);
    }
}
