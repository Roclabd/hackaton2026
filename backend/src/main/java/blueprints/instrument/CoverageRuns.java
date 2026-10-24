package blueprints.instrument;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Сырые счётчики копии «замер» по каждому входу. Сводку считает {@link #summary(IntPredicate)}:
 * проверка учитывает только те входы, которые действительно сравнила (без «случайных» и неизвестных).
 */
public final class CoverageRuns {

    private final InstrumentedSource instrumented;
    private final boolean onOriginal;
    /** hits[i][k] — сколько раз оператор k выполнился на входе i; null — замер на этом входе не удался. */
    private final int[][] hits;

    CoverageRuns(InstrumentedSource instrumented, boolean onOriginal, int[][] hits) {
        this.instrumented = instrumented;
        this.onOriginal = onOriginal;
        this.hits = hits.clone();
    }

    int inputs() {
        return hits.length;
    }

    boolean valid(int input) {
        return hits[input] != null && hits[input].length == instrumented.counters();
    }

    boolean reached(int input) {
        return valid(input) && java.util.Arrays.stream(hits[input]).anyMatch(hit -> hit > 0);
    }

    /** Сводка по всем входам. */
    public ChangedCoverage summary() {
        return summary(input -> true);
    }

    /** @param counted учитывать ли вход с этим номером (номера — в порядке списка входов) */
    public ChangedCoverage summary(IntPredicate counted) {
        int statements = instrumented.counters();
        int[] inputsPerStatement = new int[statements];
        int inputs = 0;
        int reached = 0;
        for (int i = 0; i < hits.length; i++) {
            int[] run = hits[i];
            if (run == null || run.length != statements || !counted.test(i)) {
                continue;
            }
            inputs++;
            boolean any = false;
            for (int k = 0; k < statements; k++) {
                if (run[k] > 0) {
                    inputsPerStatement[k]++;
                    any = true;
                }
            }
            if (any) {
                reached++;
            }
        }
        List<ChangedCoverage.Statement> details = new ArrayList<>();
        int covered = 0;
        int minHits = Integer.MAX_VALUE;
        for (int k = 0; k < statements; k++) {
            details.add(new ChangedCoverage.Statement(instrumented.statementLines().get(k), inputsPerStatement[k], onOriginal));
            covered += inputsPerStatement[k] > 0 ? 1 : 0;
            minHits = Math.min(minHits, inputsPerStatement[k]);
        }
        return new ChangedCoverage(statements, covered, minHits, reached, inputs, onOriginal, details);
    }
}
