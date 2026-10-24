import java.util.Scanner;
public class Iterative {
    static long sum(int n) { return n == 0 ? 0 : n + sum(n - 1); }
    public static void main(String[] args) {
        System.out.println("sum=" + sum(100000));
    }
}
