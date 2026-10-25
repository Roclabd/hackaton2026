import java.util.List;

public class BaitR7 {

    enum Level { LOW, HIGH }

    // ПРИМАНКА: «==» для enum — сравнение констант по ссылке правильно.
    static boolean isHigh(Level level) {
        return level == Level.HIGH;
    }

    // ПРИМАНКА: обе строки прошли intern(), их ссылки равны тогда и только тогда, когда равен текст.
    static boolean sameInterned(String a, String b) {
        return a.intern() == b.intern();
    }

    // ПРИМАНКА: проверка на null — сравнение ссылки с null, а не текста.
    static int safeLength(String s) {
        if (s == null) return 0;
        return s.length();
    }

    // ПРИМАНКА: классический быстрый путь equals — сначала тождество ссылок, затем содержимое.
    static boolean sameText(String a, String b) {
        if (a == b) return true;
        return a != null && a.equals(b);
    }

    // ПРИМАНКА: сравнение символов (char) через «==» корректно.
    static boolean startsWithHash(String s) {
        return !s.isEmpty() && s.charAt(0) == '#';
    }

    // ПРИМАНКА: compareTo == 0 и equals — сравнение по содержимому.
    static int countWord(List<String> words, String w) {
        int n = 0;
        for (String x : words) {
            if (x.compareTo(w) == 0 || x.equalsIgnoreCase(w)) n++;
        }
        return n;
    }

    // ПРИМАНКА: две строковые константы-литерала интернированы компилятором.
    static boolean literalCheck() {
        return "ok" == "ok";
    }

    public static void main(String[] args) {
        System.out.println(isHigh(Level.HIGH));
        System.out.println(sameInterned(new String("a"), "a"));
        System.out.println(safeLength(null));
        System.out.println(sameText("x", new String("x")));
        System.out.println(startsWithHash("#tag"));
        System.out.println(countWord(List.of("a", "A", "b"), "a"));
        System.out.println(literalCheck());
    }
}
