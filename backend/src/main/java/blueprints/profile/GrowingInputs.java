package blueprints.profile;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Входы растущего размера для замера роста.
 *
 * Поддержан один формат — «строки чисел через Scanner» (как в MatchDemo): программа читает System.in
 * через Scanner построчно (nextLine) и разбирает целые числа. Тогда вход размера n — столько строк,
 * сколько в коде чтений строки (от 1 до 5), и в каждой n случайных целых от 1 до n:
 * при таком диапазоне совпадения есть (в среднем около n на пару строк).
 *
 * Для других форматов ответ честный — «не поддержан»: подбирать вход наугад значило бы выдумывать цифры.
 */
final class GrowingInputs {

    /** Размеры входа: каждый следующий вдвое больше — так рост виден на лог-лог шкале. */
    static final List<Integer> SIZES = List.of(100, 200, 400, 800, 1600);
    static final long SEED = 20261024L;
    private static final int MAX_LINES = 5;

    private static final Pattern LINE_READ = Pattern.compile("\\.nextLine\\s*\\(");
    private static final Pattern INTEGER_PARSE =
            Pattern.compile("\\b(Integer|Long)\\s*\\.\\s*(valueOf|parseInt|parseLong)\\s*\\(");

    private final int lines;

    private GrowingInputs(int lines) {
        this.lines = lines;
    }

    /** Формат входа по тексту программы или причина, почему он не поддержан. */
    static Detection detect(String source) {
        if (!source.contains("System.in")) {
            return Detection.unsupported("программа не читает ввод (System.in) — размер входа не на что менять");
        }
        int lineReads = count(LINE_READ, source);
        if (!source.contains("Scanner") || lineReads == 0) {
            return Detection.unsupported("программа читает ввод не построчно через Scanner (nextLine)");
        }
        if (!INTEGER_PARSE.matcher(source).find()) {
            return Detection.unsupported("в строках ввода не разбираются целые числа (Integer.valueOf / parseInt)");
        }
        return new Detection(Optional.of(new GrowingInputs(Math.min(MAX_LINES, lineReads))), null);
    }

    int lines() {
        return lines;
    }

    /** Вход размера n: {@link #lines()} строк по n случайных целых 1…n. Зерно зависит только от n — повторяемо. */
    String of(int n) {
        Random random = new Random(SEED + n);
        StringBuilder text = new StringBuilder();
        for (int line = 0; line < lines; line++) {
            StringJoiner numbers = new StringJoiner(" ");
            for (int i = 0; i < n; i++) {
                numbers.add(String.valueOf(1 + random.nextInt(n)));
            }
            text.append(numbers).append('\n');
        }
        return text.toString();
    }

    /** Как описать вход человеку — для пояснения к замеру. */
    String describe() {
        String rows = lines == 1 ? "1 строка" : lines + (lines < 5 ? " строки" : " строк");
        return rows + " по n случайных целых от 1 до n (зерно " + SEED + ")";
    }

    private static int count(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** Либо генератор, либо причина отказа — что-то одно. */
    record Detection(Optional<GrowingInputs> inputs, String reason) {
        static Detection unsupported(String reason) {
            return new Detection(Optional.empty(), reason);
        }
    }
}
