public class ExitThree {
    public static void main(String[] args) {
        System.out.println("before");
        System.err.print("err-no-newline");
        System.out.print("out-no-newline");
        System.exit(3);
    }
}
