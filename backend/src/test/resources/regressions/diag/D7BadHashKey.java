import java.util.*;
public class D7BadHashKey {
    // equals по значению, hashCode не переопределён: HashMap не найдёт «равный» ключ
    static final class Key {
        final int v; Key(int v) { this.v = v; }
        @Override public boolean equals(Object o) { return o instanceof Key k && k.v == v; }
    }
    static final class Item { final Key key; Item(int v) { key = new Key(v); } Key key() { return key; } }
    static int count(List<Item> a) {
        int total = 0;
        for (Item x : a) {
            for (Item y : a) {
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
        for (String t : line.trim().split("\\s+")) { try { a.add(new Item(Integer.parseInt(t))); } catch (NumberFormatException e) { } }
        System.out.println("pairs " + count(a));
    }
}
