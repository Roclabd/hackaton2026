public class StderrAndException {
    public static void main(String[] args) {
        System.out.println("out line");
        System.err.println("err line");
        helper(5);
    }
    static void helper(int n) {
        if (n == 0) throw new IllegalStateException("boom at zero");
        helper(n - 1);
    }
}
