package blueprints.runner;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Итог одного прогона программы на одном входе. Форма JSON — как в docs/ПРОТОТИП_КОНТРАКТ.md.
 *
 * @param exitCode        код выхода процесса; null — неизвестен
 * @param exception       необработанное исключение из main или null
 * @param timedOut        программа не уложилась в лимит времени — вывод неполный
 * @param outputUnknown   вывод программы потерян (процесс умер, не отдав его) — сравнивать нечего
 * @param outputTruncated вывод больше лимита и обрезан — разница могла остаться в хвосте
 * @param coverageHits    счётчики копии «замер» (см. {@link CoverageCounters}); null у обычных прогонов.
 *                        В JSON для интерфейса не попадает, если null
 */
public record RunResult(String stdout, String stderr, Integer exitCode, ExceptionInfo exception,
                        boolean timedOut, long durationMs, boolean outputUnknown, boolean outputTruncated,
                        @JsonInclude(JsonInclude.Include.NON_NULL) int[] coverageHits) {

    /** Обычный прогон, без счётчиков. */
    public RunResult(String stdout, String stderr, Integer exitCode, ExceptionInfo exception,
                     boolean timedOut, long durationMs, boolean outputUnknown, boolean outputTruncated) {
        this(stdout, stderr, exitCode, exception, timedOut, durationMs, outputUnknown, outputTruncated, null);
    }

    /** Тип и текст необработанного исключения: оракул сравнивает их точно. */
    public record ExceptionInfo(String type, String message) {}

    /** Нельзя сказать, совпал ли прогон: таймаут, потерянный или обрезанный вывод. */
    public boolean unknown() {
        return timedOut || outputUnknown || outputTruncated;
    }

    static RunResult timeout(String stdout, String stderr, long durationMs) {
        return new RunResult(stdout, stderr, null, null, true, durationMs, false, false);
    }

    /** Процесс завершился посреди прогона, не отдав вывод (например, Runtime.halt или падение JVM). */
    static RunResult lost(Integer exitCode, String reason, long durationMs) {
        return new RunResult("", reason, exitCode, null, false, durationMs, true, false);
    }

    RunResult withExitCode(Integer code) {
        return new RunResult(stdout, stderr, code, exception, timedOut, durationMs, outputUnknown, outputTruncated,
                coverageHits);
    }
}
