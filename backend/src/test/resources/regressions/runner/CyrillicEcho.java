import java.util.Scanner;
public class CyrillicEcho {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        String line = in.hasNextLine() ? in.nextLine() : "";
        System.out.println("Привет, " + line + "! длина=" + line.length());
        System.out.printf("%.2f %s %,d%n", 3.14159, 1.5, 1234567);
        System.out.println(String.format("%tA", new java.util.GregorianCalendar(2026, 9, 25)));
    }
}
