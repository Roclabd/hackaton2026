public class ShutdownHookExit {
    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("hook ran")));
        System.out.println("exiting");
        System.exit(4);
    }
}
