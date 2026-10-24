public class StaticInitLoop {
    static int x;
    static { while (x >= 0) { x = 1; } }
    public static void main(String[] args) { System.out.println("never"); }
}
