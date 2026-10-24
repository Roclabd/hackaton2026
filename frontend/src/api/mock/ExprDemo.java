import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Демо для режима моков: выражения с тернарным оператором, instanceof, приведением типов и лямбдами.
 */
public class ExprDemo {

    static String describe(Object value, List<Integer> items) {
        String kind = value instanceof String s ? "строка из " + s.length() : "другое";
        int limit = value instanceof Integer ? (Integer) value : items.size();
        items.removeIf(x -> x == null || x > limit);
        items.sort((a, b) -> Integer.compare(b, a));
        String text = kind + ": " + items.size();
        text += "!";
        return items.isEmpty() ? text : text + ", максимум " + Collections.max(items);
    }

    public static void main(String[] args) {
        List<Integer> items = new ArrayList<>(List.of(5, 1, 9, 3));
        System.out.println(describe(args.length > 0 ? (Object) args[0] : Integer.valueOf(4), items));
    }
}
