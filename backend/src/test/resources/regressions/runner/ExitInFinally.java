public class ExitInFinally {
    public static void main(String[] args) {
        try {
            System.out.println("in try");
            System.exit(2);
        } catch (Throwable t) {
            System.out.println("caught " + t);
        } finally {
            System.out.println("finally ran");
        }
        System.out.println("after");
    }
}
