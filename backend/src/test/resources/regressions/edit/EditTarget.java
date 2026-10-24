import java.util.*;
import java.util.function.*;

public class EditTarget {
    record Point(int x, int y) {
        Point {
            if (x < 0) throw new IllegalArgumentException("x");
        }
        int sum() { int s = x + y; return s; }
    }

    enum Op {
        ADD { int apply(int a, int b) { int r = a + b; return r; } },
        MUL { int apply(int a, int b) { int r = a * b; return r; } };
        abstract int apply(int a, int b);
    }

    static int twoOnOneLine() {
        int a = 0, b = 0;
        a = 1; b = 2;
        return a * 10 + b;
    }

    static String emoji() {
        String s = "😀😀"; int n = s.length();
        return s + n;
    }

    static int withLambdaAndSwitch(int k) {
        Function<Integer, Integer> f = v -> {
            int t = v * 2;
            return t + 1;
        };
        int r = switch (k) {
            case 1 -> f.apply(1);
            case 2 -> { int q = f.apply(2); yield q * 3; }
            default -> 0;
        };
        switch (k) {
            case 1 -> r += 100;
            default -> r += 1000;
        }
        return r;
    }

    static String textBlock() {
        String t = """
            hello
            """;
        return t.strip();
    }

    public static void main(String[] args) {
        System.out.println(twoOnOneLine());
        System.out.println(emoji());
        System.out.println(withLambdaAndSwitch(1) + " " + withLambdaAndSwitch(2));
        System.out.println(new Point(1, 2).sum() + " " + Op.MUL.apply(3, 4));
        System.out.println(textBlock());
    }
}
