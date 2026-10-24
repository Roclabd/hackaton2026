public class HugeOut {
    public static void main(String[] args) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < 99; i++) line.append('a');
        for (int i = 0; i < 1000; i++) System.out.println(line); // 100 КБ: больше лимита 64 КБ
        System.out.println("total=" + 1000);
    }
}
