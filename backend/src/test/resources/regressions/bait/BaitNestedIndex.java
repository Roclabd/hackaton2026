import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/** Приманки R15–R17: похоже на перебор n², но линейно или без поиска. Ни одной находки. */
public class BaitNestedIndex {

    static final int ALPHABET = 26;

    /** Внутренний цикл по константе: 26 × n — линейно. */
    static int[] letterCounts(String text) {
        int[] counts = new int[ALPHABET];
        for (int i = 0; i < text.length(); i++) {
            for (int c = 0; c < ALPHABET; c++) {
                if (text.charAt(i) == (char) ('a' + c)) {
                    counts[c]++;
                }
            }
        }
        return counts;
    }

    /** Симметричность матрицы: обход n² по самой задаче, а не поиск. */
    static boolean symmetric(int[][] m) {
        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m.length; j++) {
                if (m[i][j] != m[j][i]) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Попарные произведения: сравниваются индексы, а не элементы. */
    static long pairProducts(int[] a) {
        long sum = 0;
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a.length; j++) {
                if (i == j) {
                    continue;
                }
                sum += (long) a[i] * a[j];
            }
        }
        return sum;
    }

    /** Сортировка вставками сравнивает порядок, а не равенство. */
    static void insertionSort(int[] a) {
        for (int i = 1; i < a.length; i++) {
            int key = a[i];
            int j = i - 1;
            while (j >= 0 && a[j] > key) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    /** Подсчёт уже через HashMap — один проход. */
    static int duplicates(int[] a) {
        Map<Integer, Integer> seen = new HashMap<>();
        int duplicates = 0;
        for (int i = 0; i < a.length; i++) {
            int before = seen.getOrDefault(a[i], 0);
            if (before > 0) {
                duplicates++;
            }
            seen.put(a[i], before + 1);
        }
        return duplicates;
    }

    /** ArrayList по индексу — одно обращение на шаг. */
    static int sumArrayList(ArrayList<Integer> list) {
        int sum = 0;
        for (int i = 0; i < list.size(); i++) {
            sum += list.get(i);
        }
        return sum;
    }

    /** LinkedList обходится for-each, по индексу — только первый элемент. */
    static int sumLinked(LinkedList<Integer> list) {
        int sum = 0;
        for (int value : list) {
            sum += value;
        }
        return sum + list.get(0);
    }

    /** Свой метод в цикле, но это сумма, а не поиск. */
    static int total(int[] row) {
        int sum = 0;
        for (int i = 0; i < row.length; i++) {
            sum += row[i];
        }
        return sum;
    }

    static int grandTotal(int[][] rows) {
        int sum = 0;
        for (int r = 0; r < rows.length; r++) {
            sum += total(rows[r]);
        }
        return sum;
    }

    /** Поиск перебором, но вызывается один раз, вне цикла. */
    static int indexOf(int[] a, int target) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] == target) {
                return i;
            }
        }
        return -1;
    }

    /** Поиск в цикле по константе: три запроса — не рост. */
    static int firstThree(int[] a) {
        int found = 0;
        for (int q = 0; q < 3; q++) {
            if (indexOf(a, q) >= 0) {
                found++;
            }
        }
        return found;
    }

    public static void main(String[] args) {
        int[] data = {3, 1, 4, 1, 5, 9, 2, 6, 5, 3};
        int[][] grid = {{1, 2}, {2, 1}};
        ArrayList<Integer> list = new ArrayList<>(List.of(1, 2, 3));
        LinkedList<Integer> linked = new LinkedList<>(List.of(4, 5, 6));
        insertionSort(data.clone());
        System.out.println(letterCounts("hello")[7] + " " + symmetric(grid) + " " + pairProducts(data));
        System.out.println(duplicates(data) + " " + sumArrayList(list) + " " + sumLinked(linked));
        System.out.println(grandTotal(grid) + " " + indexOf(data, 9) + " " + firstThree(data));
    }
}
