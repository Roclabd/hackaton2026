import java.util.Locale;
public class LocaleMutation {
    public static void main(String[] args) {
        System.out.println("default=" + Locale.getDefault() + " prop=" + System.getProperty("audit.flag"));
        System.out.println(String.format("%.2f", 1.5));
        Locale.setDefault(Locale.GERMANY);
        System.setProperty("audit.flag", "set-by-previous-run");
    }
}
