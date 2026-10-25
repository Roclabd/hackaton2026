import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class BaitR10 {

    private static final List<Character> VOWELS = List.of('a', 'e', 'i', 'o', 'u');

    // ПРИМАНКА: contains у Set — одно обращение к хешу.
    static List<Integer> unique(List<Integer> xs) {
        Set<Integer> seen = new HashSet<>();
        List<Integer> out = new ArrayList<>();
        for (int x : xs) {
            if (!seen.contains(x)) {
                seen.add(x);
                out.add(x);
            }
        }
        return out;
    }

    // ПРИМАНКА: contains у String — это поиск подстроки, а не в списке.
    static int countWithDot(List<String> names) {
        int n = 0;
        for (String name : names) {
            if (name.contains(".")) n++;
        }
        return n;
    }

    // ПРИМАНКА: список создаётся заново на каждой итерации — общий рост не накапливается.
    static int linesWithX(List<String> lines) {
        int total = 0;
        for (String line : lines) {
            List<String> tokens = List.of(line.split(","));
            if (tokens.contains("x")) total++;
        }
        return total;
    }

    // ПРИМАНКА: цикл меню — итерации это команды пользователя, а не элементы данных.
    static List<String> menu(Scanner in) {
        List<String> history = new ArrayList<>();
        while (in.hasNextLine()) {
            String cmd = in.nextLine().trim();
            if (cmd.equals("exit")) break;
            if (!history.contains(cmd)) history.add(cmd);
        }
        return history;
    }

    // ПРИМАНКА: список гласных — константа из 5 элементов: поиск в нём O(1), не O(n).
    static int vowels(String word) {
        int n = 0;
        for (char c : word.toCharArray()) {
            if (VOWELS.contains(c)) n++;
        }
        return n;
    }

    // ПРИМАНКА: поиск в списке один раз, после цикла.
    static int positionOfMax(List<Integer> xs) {
        int max = Integer.MIN_VALUE;
        for (int x : xs) {
            max = Math.max(max, x);
        }
        return xs.indexOf(max);
    }

    // ПРИМАНКА: порядок вставки сохраняется через LinkedHashSet, поиск по множеству.
    static List<String> orderedUnique(List<String> xs) {
        Set<String> kept = new LinkedHashSet<>();
        for (String x : xs) {
            if (!kept.contains(x)) kept.add(x);
        }
        return new ArrayList<>(kept);
    }

    public static void main(String[] args) {
        System.out.println(unique(List.of(1, 1, 2)));
        System.out.println(countWithDot(List.of("a.b", "c")));
        System.out.println(linesWithX(List.of("a,x", "b,c", "x")));
        System.out.println(menu(new Scanner("a\nb\na\nexit\n")));
        System.out.println(vowels("education"));
        System.out.println(positionOfMax(List.of(3, 9, 1)));
        System.out.println(orderedUnique(List.of("b", "a", "b")));
    }
}
