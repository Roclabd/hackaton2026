import java.util.concurrent.ForkJoinPool;
public class ForkJoinExit {
    public static void main(String[] args) {
        ForkJoinPool.commonPool().submit(() -> {
            try { java.util.concurrent.TimeUnit.MILLISECONDS.sleep(300); } catch (InterruptedException e) { }
            System.out.println("background leaked");
        });
        System.out.println("exit now");
        System.exit(0);
    }
}
