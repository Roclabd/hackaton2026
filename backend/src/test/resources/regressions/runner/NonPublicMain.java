class Helper { static String hi() { return "hi"; } }
class NonPublicMainX {
    public static void main(String[] args) { System.out.println(Helper.hi() + " from non-public"); }
}
