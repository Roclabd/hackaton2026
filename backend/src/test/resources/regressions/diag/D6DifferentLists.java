import java.util.*;
public class D6DifferentLists {
    static int count(List<String> a, List<String> b) {
        int total = 0;
        for (String x : a) {
            for (String y : b) {
                if (Objects.equals(x, y)) {
                    total++;
                }
            }
        }
        return total;
    }
    static List<String> parse(String line) {
        if (line == null) return null;
        List<String> r = new ArrayList<>();
        for (String t : line.trim().split("\\s+")) if (!t.isEmpty()) r.add(t.equals("null") ? null : t);
        return r;
    }
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        List<String> a = parse(in.hasNextLine() ? in.nextLine() : "");
        List<String> b = parse(in.hasNextLine() ? in.nextLine() : null);
        System.out.println("count " + count(a, b));
    }
}
