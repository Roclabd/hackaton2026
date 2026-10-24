import java.util.Scanner;
public class Flaky {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        String line = in.hasNextLine() ? in.nextLine() : "";
        if (line.isBlank()) {
            System.out.println("empty");
        } else {
            System.out.println(line.length() + " " + System.nanoTime());
        }
    }
}
