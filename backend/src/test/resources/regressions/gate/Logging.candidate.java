import java.util.Scanner;
import java.util.logging.Logger;
public class Logging {
    private static final Logger LOG = Logger.getLogger("Logging");
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int sum = 0;
        while (in.hasNextInt()) {
            int v = in.nextInt();
            LOG.warning("VALUE " + (v * 1000));
            sum += v;
        }
        System.out.println("sum " + sum);
    }
}
