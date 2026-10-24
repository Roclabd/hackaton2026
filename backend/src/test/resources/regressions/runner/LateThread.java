public class LateThread {
    public static void main(String[] args) throws Exception {
        Thread worker = new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException e) { }
            System.out.println("late result: 42");
        });
        worker.start();
        System.out.println("main done");
    }
}
