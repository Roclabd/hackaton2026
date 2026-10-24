import java.util.Scanner;
public class EndlessMenu {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int balance = 0;
        while (true) {
            System.out.println("1) add 2) show");
            String cmd = in.hasNextLine() ? in.nextLine().trim() : "";
            switch (cmd) {
                case "1" -> balance += 10;
                case "2" -> System.out.println("balance " + balance);
                default -> { }
            }
        }
    }
}
