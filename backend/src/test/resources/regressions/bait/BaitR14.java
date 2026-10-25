import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class BaitR14 {

    private static final Logger LOG = Logger.getLogger("bait");

    // ПРИМАНКА: в пустом catch есть объясняющий комментарий — проглатывание намеренное.
    static List<Integer> parseAll(String[] tokens) {
        List<Integer> out = new ArrayList<>();
        for (String t : tokens) {
            try {
                out.add(Integer.parseInt(t));
            } catch (NumberFormatException ignored) {
                // ожидаемо: нечисловые токены пропускаем
            }
        }
        return out;
    }

    // ПРИМАНКА: блочный комментарий внутри catch на той же строке.
    static int parseOrMinus(String s) {
        int v = -1;
        try {
            v = Integer.parseInt(s.trim());
        } catch (NumberFormatException e) { /* ожидаемо: -1 означает «не задано» */ }
        return v;
    }

    // ПРИМАНКА: catch логирует исключение — оно не теряется.
    static int safeDivide(int a, int b) {
        try {
            return a / b;
        } catch (ArithmeticException e) {
            LOG.warning("деление на ноль: " + e.getMessage());
            return 0;
        }
    }

    // ПРИМАНКА: catch восстанавливает флаг прерывания — канонический ответ на InterruptedException.
    static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ПРИМАНКА: исключение оборачивается и пробрасывается дальше.
    static int firstChar(String text) {
        try (StringReader r = new StringReader(text)) {
            return r.read();
        } catch (IOException e) {
            throw new IllegalStateException("не удалось прочитать", e);
        }
    }

    // ПРИМАНКА: catch возвращает значение по умолчанию — единственный оператор, а не пустота.
    static double parseRate(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // ПРИМАНКА: пустой finally-блока нет, а catch печатает подсказку пользователю.
    static void tryOpen(String name) {
        try {
            if (name.isEmpty()) throw new IllegalArgumentException("пустое имя");
            System.out.println("открыт " + name);
        } catch (IllegalArgumentException e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        System.out.println(parseAll(new String[]{"1", "x", "3"}));
        System.out.println(parseOrMinus("z"));
        System.out.println(safeDivide(4, 0));
        pause(1);
        System.out.println(firstChar("A"));
        System.out.println(parseRate("bad"));
        tryOpen("");
    }
}
