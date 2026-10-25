import java.util.List;
import java.util.function.IntUnaryOperator;

public class BaitR3 {

    private static final int BASE = initBase();
    private final IntUnaryOperator twice = v -> doubleIt(v);
    private int counter;

    // ПРИМАНКА: вызывается из инициализатора статического поля — достижим без main.
    private static int initBase() {
        return 10;
    }

    // ПРИМАНКА: вызывается из лямбды в инициализаторе поля экземпляра.
    private static int doubleIt(int v) {
        return v * 2;
    }

    BaitR3() {
        reset();
    }

    // ПРИМАНКА: вызывается из конструктора.
    private void reset() {
        counter = 0;
    }

    // ПРИМАНКА: используется только через ссылку на метод BaitR3::square.
    private static int square(int v) {
        return v * v;
    }

    // ПРИМАНКА: вызывается через другой объект того же класса (other.bump()).
    private void bump() {
        counter++;
    }

    // ПРИМАНКА: рекурсия плюс вызов из main — достижим.
    private static int depth(int n) {
        return n <= 0 ? 0 : 1 + depth(n - 1);
    }

    // ПРИМАНКА: две перегрузки — вызывается одна, но правило консервативно и не должно удалять ни одну.
    private static String label(int v) {
        return "#" + v;
    }

    private static String label(String v) {
        return "#" + v;
    }

    public static void main(String[] args) {
        BaitR3 self = new BaitR3();
        BaitR3 other = new BaitR3();
        other.bump();
        System.out.println(BASE + self.twice.applyAsInt(3) + other.counter);
        System.out.println(List.of(1, 2, 3).stream().map(BaitR3::square).toList());
        System.out.println(depth(4) + label(7));
    }
}
