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

    /** «(MatchDemo.java:16)» → «(MatchDemo.java)» — только внутри кадра стека. */
    private static final Pattern LINE_NUMBER = Pattern.compile("\\(([^():\\s]+\\.java):\\d+\\)");
    /** Кадр стека: «\tat пакет.Класс.метод(Файл.java:16)». */
    private static final Pattern FRAME = Pattern.compile("^\\s+at \\S+\\(.*\\)$");
    /** Кадр внутри JDK: зависит от версии JDK, а не от программы. */
    private static final Pattern JDK_FRAME = Pattern.compile("^\\s+at (java\\.base/|jdk\\.internal\\.).*");
    /**
     * Строка, после которой начинается стек: заголовок JVM («Exception in thread …»), «Caused by: …»,
     * «Suppressed: …», «… N more» или имя исключения с сообщением («java.lang.IllegalStateException: …»).
     */
    private static final Pattern TRACE_HEAD = Pattern.compile(
            "^(Exception in thread .*|\\s*Caused by: .*|\\s*Suppressed: .*|\\s+\\.\\.\\. \\d+ (more|common frames omitted)"
                    + "|[\\w$.]+(Exception|Error|Throwable)(: .*)?)$");

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
        // Номер строки убираем только в кадрах стека: строка «at …(Файл.java:N)» сразу после заголовка
        // исключения или другого кадра. Тот же текст, напечатанный программой как обычная строка, сравнивается точно.
        StringBuilder out = new StringBuilder();
        boolean inTrace = false;
        for (String line : stderr.replace("\r\n", "\n").split("\n", -1)) {
            boolean frame = inTrace && FRAME.matcher(line).matches();
            if (frame && JDK_FRAME.matcher(line).matches()) continue;
            String normalized = frame ? LINE_NUMBER.matcher(line).replaceAll("($1)") : line;
            out.append(normalized).append('\n');
            inTrace = frame || TRACE_HEAD.matcher(line).matches();
        }
        return out.substring(0, out.length() - 1);
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
