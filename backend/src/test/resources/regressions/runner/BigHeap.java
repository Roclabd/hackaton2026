public class BigHeap {
    public static void main(String[] args) {
        byte[][] blocks = new byte[6][];
        for (int i = 0; i < blocks.length; i++) blocks[i] = new byte[64 * 1024 * 1024];
        System.out.println("allocated 384 MB");
    }
}
