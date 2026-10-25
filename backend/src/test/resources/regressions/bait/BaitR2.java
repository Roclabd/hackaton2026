import java.util.List;

public class BaitR2 {

    // ПРИМАНКА: граница length - 1 включена правильно — последний индекс и есть length - 1.
    static int sumInclusive(int[] arr) {
        int sum = 0;
        for (int i = 0; i <= arr.length - 1; i++) {
            sum += arr[i];
        }
        return sum;
    }

    // ПРИМАНКА: n — число шагов, а не длина массива; массив выделен на n + 1 элемент.
    static long[] factorials(int n) {
        long[] f = new long[n + 1];
        f[0] = 1;
        for (int i = 1; i <= n; i++) {
            f[i] = f[i - 1] * i;
        }
        return f;
    }

    // ПРИМАНКА: i доходит до length, но обращение — a[i - 1], и начинается цикл с 1.
    static int[] prefixSums(int[] a) {
        int[] prefix = new int[a.length + 1];
        for (int i = 1; i <= a.length; i++) {
            prefix[i] = prefix[i - 1] + a[i - 1];
        }
        return prefix;
    }

    // ПРИМАНКА: включённая граница по массиву-префиксу длины n + 1 — индекс i допустим до a.length.
    static int lastPrefix(int[] a) {
        int[] prefix = prefixSums(a);
        int last = 0;
        for (int i = 0; i <= a.length; i++) {
            last = prefix[i];
        }
        return last;
    }

    // ПРИМАНКА: внутри стоит проверка границы — обращения к arr[arr.length] не происходит.
    static int guardedSum(int[] arr) {
        int sum = 0;
        for (int i = 0; i <= arr.length; i++) {
            if (i < arr.length) {
                sum += arr[i];
            }
        }
        return sum;
    }

    // ПРИМАНКА: list.size() — это не массив, а обход по индексу идёт с «<».
    static int listSum(List<Integer> list) {
        int sum = 0;
        for (int i = 0; i < list.size(); i++) {
            sum += list.get(i);
        }
        return sum;
    }

    public static void main(String[] args) {
        int[] a = {1, 2, 3};
        System.out.println(sumInclusive(a));
        System.out.println(factorials(5)[5]);
        System.out.println(prefixSums(a)[3]);
        System.out.println(lastPrefix(a));
        System.out.println(guardedSum(a));
        System.out.println(listSum(List.of(4, 5)));
    }
}
