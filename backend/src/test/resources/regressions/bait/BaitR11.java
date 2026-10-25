import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

public class BaitR11 {

    record Score(String name, int points) { }

    // ПРИМАНКА: одна сортировка после цикла наполнения.
    static List<Integer> collectSorted(int[] a) {
        List<Integer> out = new ArrayList<>();
        for (int v : a) {
            out.add(v);
        }
        Collections.sort(out);
        return out;
    }

    // ПРИМАНКА: каждая группа сортируется один раз — суммарно n log n, а не n² log n.
    static void sortEachGroup(List<List<Integer>> groups) {
        for (List<Integer> g : groups) {
            g.sort(null);
        }
    }

    // ПРИМАНКА: сортировка по команде пользователя в цикле меню — одна на команду.
    static List<Score> menu(Scanner in) {
        List<Score> table = new ArrayList<>();
        while (true) {
            if (!in.hasNext()) break;
            String cmd = in.next();
            if (cmd.equals("add")) table.add(new Score(in.next(), in.nextInt()));
            else if (cmd.equals("top")) table.sort(Comparator.comparingInt(Score::points).reversed());
            else break;
        }
        return table;
    }

    // ПРИМАНКА: TreeMap держит порядок сам — явной сортировки в цикле нет.
    static Map<String, Integer> ordered(List<Score> scores) {
        Map<String, Integer> byName = new TreeMap<>();
        for (Score s : scores) {
            byName.merge(s.name(), s.points(), Integer::sum);
        }
        return byName;
    }

    // ПРИМАНКА: Arrays.sort один раз до цикла бинарного поиска.
    static int countPresent(int[] haystack, int[] needles) {
        int[] copy = haystack.clone();
        Arrays.sort(copy);
        int n = 0;
        for (int x : needles) {
            if (Arrays.binarySearch(copy, x) >= 0) n++;
        }
        return n;
    }

    // ПРИМАНКА: stream().sorted() на копии строки матрицы — каждая строка сортируется один раз.
    static int sumOfRowMedians(int[][] rows) {
        int sum = 0;
        for (int[] row : rows) {
            int[] sorted = Arrays.stream(row).sorted().toArray();
            sum += sorted[sorted.length / 2];
        }
        return sum;
    }

    public static void main(String[] args) {
        System.out.println(collectSorted(new int[]{3, 1, 2}));
        List<List<Integer>> groups = List.of(new ArrayList<>(List.of(3, 1)), new ArrayList<>(List.of(9, 2)));
        sortEachGroup(groups);
        System.out.println(groups);
        System.out.println(menu(new Scanner("add a 5 add b 9 top")));
        System.out.println(ordered(List.of(new Score("b", 1), new Score("a", 2))));
        System.out.println(countPresent(new int[]{5, 1, 3}, new int[]{3, 4}));
        System.out.println(sumOfRowMedians(new int[][]{{3, 1, 2}, {9, 7, 8}}));
    }
}
