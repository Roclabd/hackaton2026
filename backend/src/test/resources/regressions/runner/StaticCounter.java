public class StaticCounter {
    static int runs = 0;
    public static void main(String[] args) {
        runs++;
        System.out.println("runs=" + runs);
    }
}
