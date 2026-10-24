package blueprints.gate;

import java.util.List;

/**
 * Состояние проверки кандидата — ответ GET /api/jobs/{jobId}. Форма — docs/ПРОТОТИП_КОНТРАКТ.md, «Проверка».
 * Пока идёт проверка, status = RUNNING и progress < 1; итоговые поля заполняются по мере прихода результатов.
 *
 * @param checked        сколько входов прогнано до конца (все три прогона)
 * @param matched        на скольких входах эталон и кандидат реально сравнены и совпали — это число и пишется
 *                       в «Совпало на N входах»; случайные и непроверенные входы в него не входят
 * @param changedCovered покрытие изменённого кода ({@link blueprints.instrument.ChangedCoverage}: lines, covered,
 *                       minHits и подробности) по сравнённым входам; null — пока идёт проверка или не измерено
 *                       (изменений нет, изменены только не-операторы, копию со счётчиками не собрать)
 * @param speedup        ускорение — появится с режимом «замер», пока null
 */
public record Verdict(VerdictStatus status, Phase phase, double progress,
                      int inputs, int checked, int matched, int mismatches, int nondeterministic,
                      String incompleteReason, List<BuildError> buildErrors,
                      RunPair smallestMismatch, RunPair example, List<GroupSummary> groups,
                      Object changedCovered, Object speedup, long durationMs) {

    static Verdict starting() {
        return new Verdict(VerdictStatus.RUNNING, Phase.COMPILE, 0.0, 0, 0, 0, 0, 0, null, List.of(),
                null, null, List.of(), null, null, 0);
    }

    static Verdict buildError(List<BuildError> errors, long durationMs) {
        return new Verdict(VerdictStatus.BUILD_ERROR, Phase.DONE, 1.0, 0, 0, 0, 0, 0, null, List.copyOf(errors),
                null, null, List.of(), null, null, durationMs);
    }

    /** Проверить не удалось по причине, не связанной с кандидатом (эталон сломан, нет main, сбой инструмента). */
    static Verdict incomplete(String reason, long durationMs) {
        return new Verdict(VerdictStatus.INCOMPLETE, Phase.DONE, 1.0, 0, 0, 0, 0, 0, reason, List.of(),
                null, null, List.of(), null, null, durationMs);
    }
}
