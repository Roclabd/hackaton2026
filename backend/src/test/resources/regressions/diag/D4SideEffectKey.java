import java.util.*;
public class D4SideEffectKey {
    static int keyCalls = 0;
    static final class Item { final String id; Item(String id) { this.id = id; } String key() { keyCalls++; return id; } }
    static long count(List<Item> a, List<Item> b) {
        long total = 0;
        for (Item x : a) {
            for (Item y : b) {
                if (Objects.equals(x.key(), y.key())) {
                    total++;
                }
            }
        }
        return total;
    }
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        List<Item> a = new ArrayList<>();
        String line = in.hasNextLine() ? in.nextLine() : "";
        for (String t : line.trim().split("\\s+")) if (!t.isEmpty()) a.add(new Item(t));
        System.out.println("matches " + count(a, a) + " keyCalls " + keyCalls);
    }
}
