public class HugeOutput {
    public static void main(String[] args) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < 99; i++) line.append('a');
        for (int i = 0; i < 20000; i++) System.out.println(line); // 2 000 000 bytes
        System.out.println("END-ORIGINAL");
    }
}
