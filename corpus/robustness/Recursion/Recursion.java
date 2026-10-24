import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Рекурсия: числа Фибоначчи (наивно и с памятью), факториал, перестановки, подмножества,
 * Ханойские башни, НОД, быстрое возведение в степень, разбиения числа.
 * Ввод: одно число n (по умолчанию 5) и, необязательно, строка для перестановок.
 */
public class Recursion {

    private static long calls = 0;
    private static final Map<Integer, Long> memo = new HashMap<>();

    // Наивный Фибоначчи — экспоненциальное число вызовов
    static long fib(int n) {
        calls++;
        if (n <= 1) {
            return n;
        }
        return fib(n - 1) + fib(n - 2);
    }

    static long fibMemo(int n) {
        if (n <= 1) {
            return n;
        }
        Long cached = memo.get(n);
        if (cached != null) {
            return cached;
        }
        long value = fibMemo(n - 1) + fibMemo(n - 2);
        memo.put(n, value);
        return value;
    }

    static BigInteger factorial(int n) {
        return n <= 1 ? BigInteger.ONE : BigInteger.valueOf(n).multiply(factorial(n - 1));
    }

    static void permutations(String prefix, String rest, List<String> out) {
        if (rest.isEmpty()) {
            out.add(prefix);
            return;
        }
        for (int i = 0; i < rest.length(); i++) {
            permutations(prefix + rest.charAt(i), rest.substring(0, i) + rest.substring(i + 1), out);
        }
    }

    static void subsets(int[] items, int index, List<Integer> current, List<List<Integer>> out) {
        if (index == items.length) {
            out.add(new ArrayList<>(current));
            return;
        }
        subsets(items, index + 1, current, out);
        current.add(items[index]);
        subsets(items, index + 1, current, out);
        current.remove(current.size() - 1);
    }

    static int hanoi(int n, char from, char to, char via, StringBuilder log) {
        if (n == 0) {
            return 0;
        }
        int moves = hanoi(n - 1, from, via, to, log);
        if (log.length() < 200) {
            log.append(from).append("→").append(to).append(' ');
        }
        moves++;
        return moves + hanoi(n - 1, via, to, from, log);
    }

    static int gcd(int a, int b) {
        return b == 0 ? Math.abs(a) : gcd(b, a % b);
    }

    static long power(long base, int exp) {
        if (exp == 0) {
            return 1;
        }
        long half = power(base, exp / 2);
        return exp % 2 == 0 ? half * half : half * half * base;
    }

    // Число разбиений n на слагаемые не больше max
    static int partitions(int n, int max) {
        if (n == 0) {
            return 1;
        }
        if (n < 0 || max == 0) {
            return 0;
        }
        return partitions(n - max, max) + partitions(n, max - 1);
    }

    static int sumDigits(int n) {
        n = Math.abs(n);
        return n < 10 ? n : n % 10 + sumDigits(n / 10);
    }

    static boolean isPalindrome(String s) {
        if (s.length() < 2) {
            return true;
        }
        return s.charAt(0) == s.charAt(s.length() - 1) && isPalindrome(s.substring(1, s.length() - 1));
    }

    static int sum(int... values) {
        return sumFrom(values, 0);
    }

    private static int sumFrom(int[] values, int i) {
        return i == values.length ? 0 : values[i] + sumFrom(values, i + 1);
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = 5;
        if (sc.hasNextInt()) {
            n = sc.nextInt();
        }
        String word = sc.hasNext() ? sc.next() : "абв";
        if (n < 0 || n > 25) {
            System.out.println("n должно быть от 0 до 25, взято 5 вместо " + n);
            n = 5;
        }

        calls = 0;
        long f = fib(n);
        System.out.println("fib(" + n + ") = " + f + ", вызовов: " + calls);
        System.out.println("fibMemo(" + (n * 3) + ") = " + fibMemo(n * 3));
        System.out.println(n + "! = " + factorial(n));

        if (word.length() > 6) {
            word = word.substring(0, 6);
        }
        List<String> perms = new ArrayList<>();
        permutations("", word, perms);
        System.out.println("Перестановок «" + word + "»: " + perms.size()
                + (perms.size() <= 6 ? " " + perms : ", первые: " + perms.subList(0, 6)));
        System.out.println("«" + word + "» палиндром: " + isPalindrome(word));

        int size = Math.min(n, 4);
        int[] items = new int[size];
        for (int i = 0; i < size; i++) {
            items[i] = i + 1;
        }
        List<List<Integer>> subs = new ArrayList<>();
        subsets(items, 0, new ArrayList<>(), subs);
        System.out.println("Подмножеств {1.." + size + "}: " + subs.size() + " " + subs);

        StringBuilder log = new StringBuilder();
        int disks = Math.min(n, 10);
        System.out.println("Ханой(" + disks + "): " + hanoi(disks, 'A', 'C', 'B', log) + " ходов: " + log.toString().trim());

        System.out.println("НОД(" + (n * 12) + ", 18) = " + gcd(n * 12, 18));
        System.out.println("3^" + n + " = " + power(3, n));
        System.out.println("Разбиений " + n + ": " + partitions(n, n));
        System.out.println("Сумма цифр " + f + ": " + sumDigits((int) (f % Integer.MAX_VALUE)));
        System.out.println("sum(1..5) = " + sum(1, 2, 3, 4, 5) + ", sum() = " + sum());
    }
}
