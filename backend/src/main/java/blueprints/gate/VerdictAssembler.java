package blueprints.gate;

import blueprints.inputs.InputGenerator;
import blueprints.instrument.ChangedCoverage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Собирает вердикт из результатов по входам. Вызывается и по ходу проверки (status = RUNNING), и в конце.
 *
 * Порядок решений (план, раздел 3.9): расхождение → MISMATCH (или BEHAVIOR_CHANGE для исправления дефекта);
 * таймаут / потерянный или обрезанный вывод → INCOMPLETE; входы почти ничего не проверяют → INCOMPLETE;
 * изменённый код выполнился меньше чем на 3 входах ({@link CoverageRule}) → INCOMPLETE; иначе MATCHED.
 */
final class VerdictAssembler {

    /** Меньше сравнённых входов — не «совпало», а «проверка неполна» (план, раздел 3.6). */
    static final int MIN_COMPARED = 3;

    private VerdictAssembler() {}

    /** Промежуточный вердикт по ходу проверки: покрытие изменённого кода ещё не известно. */
    static Verdict assemble(List<InputCheck> checks, Intent intent, boolean finished, long timeoutMs, long durationMs) {
        return assemble(checks, intent, finished, timeoutMs, durationMs, null);
    }

    /**
     * @param coverage покрытие по сравнённым входам; null допустим для отсутствия изменений.
     *                 Причину сбоя замера реальной правки вызывающий код передаёт отдельным параметром.
     */
    static Verdict assemble(List<InputCheck> checks, Intent intent, boolean finished, long timeoutMs, long durationMs,
                            ChangedCoverage coverage) {
        return assemble(checks, intent, finished, timeoutMs, durationMs, coverage, null);
    }

    static Verdict assemble(List<InputCheck> checks, Intent intent, boolean finished, long timeoutMs, long durationMs,
                            ChangedCoverage coverage, String coverageIssue) {
        int mismatches = count(checks, InputCheck.Outcome.MISMATCH);
        int unknown = count(checks, InputCheck.Outcome.UNKNOWN);
        int nondeterministic = count(checks, InputCheck.Outcome.NONDETERMINISTIC);
        int matched = count(checks, InputCheck.Outcome.MATCH);
        int checked = (int) checks.stream().filter(InputCheck::finished).count();

        VerdictStatus status = VerdictStatus.RUNNING;
        String reason = null;
        if (finished) {
            if (mismatches > 0) {
                status = intent == Intent.BUGFIX ? VerdictStatus.BEHAVIOR_CHANGE : VerdictStatus.MISMATCH;
            } else {
                reason = incompleteReason(checks, intent, unknown, matched, nondeterministic, timeoutMs);
                if (reason == null && intent == Intent.REFACTOR) {
                    reason = coverageIssue != null ? coverageIssue : CoverageRule.incompleteReason(coverage);
                }
                status = reason == null ? VerdictStatus.MATCHED : VerdictStatus.INCOMPLETE;
            }
        }

        RunPair smallestMismatch = shortest(checks, InputCheck.Outcome.MISMATCH).map(InputCheck::pair).orElse(null);
        RunPair example = shortest(checks, InputCheck.Outcome.MATCH).map(InputCheck::pair).orElse(null);
        double progress = finished ? 1.0 : 0.99 * runsDone(checks) / Math.max(1, checks.size() * 3);
        return new Verdict(status, finished ? Phase.DONE : Phase.RUN, progress,
                checks.size(), checked, matched, mismatches, nondeterministic, reason, List.of(),
                smallestMismatch, example, groups(checks, intent), coverage, null, durationMs);
    }

    /** null — проверка полная. Иначе — что именно не проверено, по-русски. */
    private static String incompleteReason(List<InputCheck> checks, Intent intent, int unknown, int matched,
                                           int nondeterministic, long timeoutMs) {
        if (unknown > 0) {
            return unknownReason(checks, unknown, timeoutMs);
        }
        if (matched == 0) {
            return nondeterministic > 0
                    ? "Оригинал на всех " + nondeterministic + " входах печатает разное при повторном запуске — сравнивать не с чем"
                    : "Нет ни одного входа для проверки";
        }
        if (intent == Intent.BUGFIX) {
            return "Исправление не изменило поведение ни на одном из " + matched
                    + " входов: входы не затрагивают исправленный случай. Нужен вход, на котором проявляется дефект";
        }
        if (readsInput(checks)) {
            return weakInputsReason(checks, matched, nondeterministic);
        }
        return null;
    }

    private static String unknownReason(List<InputCheck> checks, int unknown, long timeoutMs) {
        List<InputCheck> unknownChecks = checks.stream().filter(c -> c.outcome() == InputCheck.Outcome.UNKNOWN).toList();
        long timeouts = unknownChecks.stream().filter(InputCheck::timedOut).count();
        long truncated = unknownChecks.stream().filter(c -> !c.timedOut() && c.truncated()).count();
        long lost = unknown - timeouts - truncated;
        String example = unknownChecks.stream().map(c -> c.input().text())
                .min(Comparator.comparingInt(String::length)).orElse("");
        List<String> causes = new ArrayList<>();
        if (timeouts > 0) {
            causes.add("превышен лимит времени " + timeoutMs / 1000 + " с — " + timeouts);
        }
        if (truncated > 0) {
            causes.add("вывод больше 1 МБ, хвост не сравнить — " + truncated);
        }
        if (lost > 0) {
            causes.add("вывод программы потерян — " + lost);
        }
        return "Не удалось проверить " + unknown + " из " + checks.size() + " входов: " + String.join(", ", causes)
                + ". Пример входа: «" + preview(example) + "»";
    }

    /**
     * Входы есть, но они почти ничего не проверяют. Два случая:
     * сравнено меньше {@link #MIN_COMPARED} входов (остальные случайные), или на всех непустых входах программа
     * повела себя одинаково — значит, входы не подходят к формату и до основной логики не доходят.
     * Падение само по себе не признак: программа-меню на конце ввода падает всегда, но вывод до падения разный.
     */
    private static String weakInputsReason(List<InputCheck> checks, int matched, int nondeterministic) {
        if (matched < MIN_COMPARED) {
            return "Сравнено только " + matched + " из " + checks.size() + " входов: на остальных "
                    + nondeterministic + " оригинал печатает разное при повторном запуске. Этого мало, чтобы судить о поведении";
        }
        List<InputCheck> nonEmpty = checks.stream()
                .filter(c -> c.outcome() == InputCheck.Outcome.MATCH && !c.input().text().isBlank()).toList();
        if (nonEmpty.size() < MIN_COMPARED || nonEmpty.stream().map(VerdictAssembler::behaviour).distinct().count() >= 2) {
            return null;
        }
        var exception = nonEmpty.get(0).original().exception();
        String same = exception == null ? "выдаёт один и тот же результат"
                : "одинаково падает (" + exception.type() + ")";
        return "Сгенерированные входы не подходят к программе: на всех " + nonEmpty.size() + " непустых входах она "
                + same + ". Изменённый код, скорее всего, не выполнялся — нужны входы в формате, который ждёт программа";
    }

    /** Что программа сделала на входе: вывод, код выхода и исключение — для подсчёта разных поведений. */
    private static String behaviour(InputCheck check) {
        var run = check.original();
        String exception = run.exception() == null ? "" : run.exception().type() + ": " + run.exception().message();
        return run.stdout() + "\u0000" + run.exitCode() + "\u0000" + exception;
    }

    private static boolean readsInput(List<InputCheck> checks) {
        return checks.stream().anyMatch(c -> !InputGenerator.NO_INPUT.equals(c.input().group()));
    }

    private static List<GroupSummary> groups(List<InputCheck> checks, Intent intent) {
        Map<String, List<InputCheck>> byGroup = new LinkedHashMap<>();
        checks.forEach(c -> byGroup.computeIfAbsent(c.input().group(), g -> new ArrayList<>()).add(c));
        List<GroupSummary> result = new ArrayList<>();
        byGroup.forEach((name, group) -> result.add(new GroupSummary(name, group.size(),
                (int) group.stream().filter(InputCheck::finished).count(),
                count(group, InputCheck.Outcome.MATCH), groupStatus(group, intent))));
        return result;
    }

    private static VerdictStatus groupStatus(List<InputCheck> group, Intent intent) {
        if (count(group, InputCheck.Outcome.MISMATCH) > 0) {
            return intent == Intent.BUGFIX ? VerdictStatus.BEHAVIOR_CHANGE : VerdictStatus.MISMATCH;
        }
        if (count(group, InputCheck.Outcome.PENDING) > 0) {
            return VerdictStatus.RUNNING;
        }
        if (count(group, InputCheck.Outcome.UNKNOWN) > 0 || count(group, InputCheck.Outcome.MATCH) == 0) {
            return VerdictStatus.INCOMPLETE;
        }
        return VerdictStatus.MATCHED;
    }

    /** Самый короткий вход с таким исходом: его проще всего прочитать человеку. */
    private static Optional<InputCheck> shortest(List<InputCheck> checks, InputCheck.Outcome outcome) {
        return checks.stream().filter(c -> c.outcome() == outcome)
                .min(Comparator.comparingInt(c -> c.input().text().length()));
    }

    private static int count(List<InputCheck> checks, InputCheck.Outcome outcome) {
        return (int) checks.stream().filter(c -> c.outcome() == outcome).count();
    }

    private static long runsDone(List<InputCheck> checks) {
        return checks.stream().mapToLong(c -> (c.original() != null ? 1 : 0)
                + (c.originalAgain() != null ? 1 : 0) + (c.candidate() != null ? 1 : 0)).sum();
    }

    private static String preview(String text) {
        String flat = text.replace("\n", "⏎");
        return flat.length() <= 40 ? flat : flat.substring(0, 40) + "…";
    }
}
