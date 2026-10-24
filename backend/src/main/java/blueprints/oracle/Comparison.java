package blueprints.oracle;

import java.util.List;

/**
 * Итог сравнения двух прогонов.
 *
 * @param outcome     совпало / различается / неизвестно (таймаут или потерянный вывод — это не «совпало»)
 * @param differences что именно различается; пусто, если совпало или неизвестно
 */
public record Comparison(Outcome outcome, List<Difference> differences) {

    public enum Outcome { SAME, DIFFERENT, UNKNOWN }

    /** @param field stdout, stderr, exitCode или exception */
    public record Difference(String field, String original, String candidate) {}

    public boolean matches() {
        return outcome == Outcome.SAME;
    }

    static Comparison same() {
        return new Comparison(Outcome.SAME, List.of());
    }

    static Comparison unknown() {
        return new Comparison(Outcome.UNKNOWN, List.of());
    }

    static Comparison different(List<Difference> differences) {
        return new Comparison(Outcome.DIFFERENT, List.copyOf(differences));
    }
}
