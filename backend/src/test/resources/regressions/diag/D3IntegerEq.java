import java.util.*;
public class D3IntegerEq {
    static long count(List<Integer> a, List<Integer> b) {
        long total = 0;
        for (Integer x : a) {
            for (Integer y : b) {
                if (x == y) {
                    total++;
                }
            }
        }
        return total;
    }
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        List<Integer> a = new ArrayList<>();
        String line = in.hasNextLine() ? in.nextLine() : "";
        for (String t : line.trim().split("\\s+")) { try { a.add(Integer.valueOf(t) * 100); } catch (NumberFormatException e) { } }
        System.out.println(count(a, a));
    }
}
