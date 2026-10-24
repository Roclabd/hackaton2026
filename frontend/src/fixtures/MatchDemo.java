import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

/**
 * Демо-монолит для сценария питча: вложенный цикл подсчёта совпадений.
 * Вход: две строки чисел через пробел; «null» — пустое значение.
 */
public class MatchDemo {

    public static long countMatches(List<Integer> a, List<Integer> b) {
        long total = 0;
        for (Integer x : a) {
            for (Integer y : b) {
                if (Objects.equals(x, y)) {
                    total++;
                }
            }
        }
        return total;
    }

    static List<Integer> parse(String line) {
        List<Integer> values = new ArrayList<>();
        for (String token : line.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            values.add(token.equals("null") ? null : Integer.valueOf(token));
        }
        return values;
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        List<Integer> a = in.hasNextLine() ? parse(in.nextLine()) : new ArrayList<>();
        List<Integer> b = in.hasNextLine() ? parse(in.nextLine()) : new ArrayList<>();
        System.out.println("Совпадений: " + countMatches(a, b));
    }
}
