public class HangThread {
    public static void main(String[] args) {
        new Thread(() -> { while (true) { } }).start(); // не-демон: настоящая java не завершится никогда
        System.out.println("done");
    }
}
