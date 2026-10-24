import java.util.Scanner;
public class Iterative {
    static long sum(int n) { long s = 0; for (int i = 1; i <= n; i++) s += i; return s; }
    public static void main(String[] args) {
        System.out.println("sum=" + sum(100000));
    }
}
