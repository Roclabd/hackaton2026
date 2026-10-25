import java.util.function.IntUnaryOperator;

/** Небольшой настоящий исходник для проверки вложенных блоков на фронтенде. */
public class BonusBlocks {
    static int run(int value) {
        IntUnaryOperator twice = x -> {
            int doubled = x * 2;
            return doubled;
        };
        return switch (value) {
            case 0 -> {
                int base = twice.applyAsInt(3);
                yield base + 1;
            }
            default -> {
                yield twice.applyAsInt(value);
            }
        };
    }

    public static void main(String[] args) {
        System.out.println(run(0));
    }
}
