package blueprints.instrument;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

/** Результат замера: отсутствие правок отличается от сбоя или неизмеримого изменения. */
public record CoverageMeasurement(List<CoverageRuns> runs, String issue) {

    public CoverageMeasurement {
        runs = List.copyOf(runs);
    }

    public static CoverageMeasurement unchanged() {
        return new CoverageMeasurement(List.of(), null);
    }

    public static CoverageMeasurement failed(String reason) {
        return new CoverageMeasurement(List.of(), reason);
    }

    public ChangedCoverage summary() {
        return summary(input -> true);
    }

    /** Обе стороны считаются на одних и тех же сравнённых входах с исправными счётчиками. */
    public ChangedCoverage summary(IntPredicate counted) {
        if (runs.isEmpty()) return null;
        IntPredicate valid = i -> counted.test(i) && runs.stream().allMatch(run -> run.valid(i));
        List<ChangedCoverage> sides = runs.stream().map(run -> run.summary(valid)).toList();
        List<ChangedCoverage.Statement> statements = new ArrayList<>();
        sides.forEach(side -> statements.addAll(side.statements()));
        int inputs = (int) IntStream.range(0, runs.getFirst().inputs()).filter(valid).count();
        int reached = (int) IntStream.range(0, runs.getFirst().inputs()).filter(valid)
                .filter(i -> runs.stream().anyMatch(run -> run.reached(i))).count();
        return new ChangedCoverage(statements.size(), sides.stream().mapToInt(ChangedCoverage::covered).sum(),
                sides.stream().mapToInt(ChangedCoverage::minHits).min().orElse(0), reached, inputs,
                sides.stream().allMatch(ChangedCoverage::onOriginal), statements);
    }

    public String incompleteReason(IntPredicate counted) {
        if (issue != null) return issue;
        if (runs.isEmpty()) return null;
        long missing = IntStream.range(0, runs.getFirst().inputs()).filter(counted)
                .filter(i -> runs.stream().anyMatch(run -> !run.valid(i))).count();
        return missing == 0 ? null : "Не удалось измерить покрытие изменённого кода на " + missing
                + " сравнённых входах: счётчики не получены";
    }
}
