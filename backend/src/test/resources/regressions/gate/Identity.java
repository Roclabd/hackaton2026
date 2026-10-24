import java.util.Scanner;
public class Identity {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int s = 0;
        while (in.hasNextInt()) s += in.nextInt();
        System.out.println("s=" + s);
    }
}
