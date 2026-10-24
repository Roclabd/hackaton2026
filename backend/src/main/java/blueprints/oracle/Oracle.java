package blueprints.oracle;

import blueprints.runner.RunResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Оракул: совпало ли поведение двух прогонов. Эталон — настоящий запуск оригинала, а не ожидания ИИ.
 *
 * stdout, код выхода, тип и текст исключения сравниваются точно. В stderr нормализуется только стектрейс:
 * номера строк (правка сдвигает строки) и кадры внутри JDK (зависят от версии JDK, а не от программы).
 */
public final class Oracle {

    /** «(MatchDemo.java:16)» → «(MatchDemo.java)». */
    private static final Pattern LINE_NUMBER = Pattern.compile("\\(([^():\\s]+\\.java):\\d+\\)");
    /** Кадры JDK: «at java.base/java.util.Scanner.next(…)», «at jdk.internal…». */
    private static final Pattern JDK_FRAME = Pattern.compile("(?m)^\\s*at (java\\.base/|jdk\\.internal\\.).*(\\R|$)");

    private Oracle() {}

    public static Comparison compare(RunResult original, RunResult candidate) {
        if (original.unknown() || candidate.unknown()) {
            return Comparison.unknown();
        }
        List<Comparison.Difference> differences = new ArrayList<>();
        check(differences, "stdout", original.stdout(), candidate.stdout());
        check(differences, "exitCode", String.valueOf(original.exitCode()), String.valueOf(candidate.exitCode()));
        check(differences, "exception", describe(original.exception()), describe(candidate.exception()));
        check(differences, "stderr", normalizeStderr(original.stderr()), normalizeStderr(candidate.stderr()));
        return differences.isEmpty() ? Comparison.same() : Comparison.different(differences);
    }

    /** Нормализация stderr — единственная, что допускает оракул (правило 2 в CLAUDE.md). */
    public static String normalizeStderr(String stderr) {
        if (stderr == null) {
            return "";
        }
        String unified = stderr.replace("\r\n", "\n");
        String withoutJdk = JDK_FRAME.matcher(unified).replaceAll("");
        return LINE_NUMBER.matcher(withoutJdk).replaceAll("($1)");
    }

    private static String describe(RunResult.ExceptionInfo exception) {
        return exception == null ? "нет" : exception.type() + ": " + exception.message();
    }

    private static void check(List<Comparison.Difference> out, String field, String original, String candidate) {
        if (!Objects.equals(original, candidate)) {
            out.add(new Comparison.Difference(field, original, candidate));
        }
    }
}
