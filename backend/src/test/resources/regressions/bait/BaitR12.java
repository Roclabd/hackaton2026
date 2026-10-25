import java.util.List;
import java.util.Map;

public class BaitR12 {

    // ПРИМАНКА: длина строки не бывает отрицательной — старт с 0 правильный.
    static int longestWord(List<String> words) {
        int maxLen = 0;
        for (String w : words) {
            if (w.length() > maxLen) maxLen = w.length();
        }
        return maxLen;
    }

    // ПРИМАНКА: best — количество, а имя метода не задаёт направления; 0 — честный ответ для пустых данных.
    static int mostPopular(Map<String, Integer> votes) {
        int best = 0;
        for (int c : votes.values()) {
            if (c > best) best = c;
        }
        return best;
    }

    // ПРИМАНКА: частоты неотрицательны по построению (счётчики ++) — максимум с 0 корректен.
    static int maxCount(int[] freq) {
        int max = 0;
        for (int c : freq) {
            if (c > max) max = c;
        }
        return max;
    }

    // ПРИМАНКА: максимум стартует с первого элемента, а не с 0.
    static int maxValue(int[] a) {
        int max = a[0];
        for (int v : a) {
            if (v > max) max = v;
        }
        return max;
    }

    // ПРИМАНКА: минимум стартует с Integer.MAX_VALUE.
    static int minValue(int[] a) {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < a.length; i++) {
            if (a[i] < min) min = a[i];
        }
        return min;
    }

    // ПРИМАНКА: «max» здесь — счётчик подряд идущих, накапливается без сравнения с элементом.
    static int longestRun(int[] a) {
        int max = 0;
        int run = 0;
        for (int i = 0; i < a.length; i++) {
            run = (i > 0 && a[i] == a[i - 1]) ? run + 1 : 1;
            if (run > max) max = run;
        }
        return max;
    }

    public static void main(String[] args) {
        System.out.println(longestWord(List.of("ab", "abc")));
        System.out.println(mostPopular(Map.of("a", 3, "b", 5)));
        System.out.println(maxCount(new int[]{0, 2, 1}));
        System.out.println(maxValue(new int[]{-3, -1}));
        System.out.println(minValue(new int[]{4, 2}));
        System.out.println(longestRun(new int[]{1, 1, 2, 2, 2}));
    }
}
