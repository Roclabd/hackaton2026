import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
public class ForkJoinExit {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        String line = in.hasNextLine() ? in.nextLine() : "";
        ForkJoinPool.commonPool().submit(() -> {
            try { java.util.concurrent.TimeUnit.MILLISECONDS.sleep(15); } catch (InterruptedException e) { }
            System.out.println("background " + line);
        });
        System.out.println("exit now " + line.length());
        System.exit(0);
    }
}
