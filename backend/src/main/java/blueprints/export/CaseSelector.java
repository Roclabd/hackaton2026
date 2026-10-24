package blueprints.export;

import blueprints.gate.GateService.ReferenceKind;
import blueprints.gate.GateService.ReferenceRun;
import blueprints.inputs.InputGenerator;
import blueprints.runner.RunResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Какие входы проверки становятся тестами. Только те, где вывод эталона известен и стабилен:
 * случайный вывод или таймаут дали бы тест, который падает сам по себе.
 */
final class CaseSelector {

    /**
     * Строковая константа в class-файле — не больше 65 535 байт. С запасом: больший вход или вывод
     * в тест не попадает (пометка в README), а не ломает компиляцию теста.
     */
    static final int MAX_LITERAL_BYTES = 60_000;

    /** Имена методов по группам входов — латиницей; русское имя группы остаётся в @DisplayName. */
    private static final Map<String, String> METHOD_PREFIX = Map.of(
            InputGenerator.NO_INPUT, "noInput",
            InputGenerator.NORMAL, "normalValues",
            InputGenerator.WIDE, "largeNumbers",
            InputGenerator.REPEATED, "repeatedValues",
            InputGenerator.EMPTY, "emptyLists",
            InputGenerator.NULLS, "nullValues",
            InputGenerator.LARGE, "largeInputs",
            InputGenerator.EDGE, "edgeValues",
            InputGenerator.COMMANDS, "codeCommands",
            InputGenerator.CORPUS, "corpusInputs");

    /**
     * @param cases         тесты в порядке входов проверки
     * @param random        пропущено: оригинал при повторном запуске печатал другое
     * @param unknown       пропущено: таймаут, потерянный или обрезанный вывод
     * @param tooLarge      пропущено: вход или вывод больше {@link #MAX_LITERAL_BYTES}
     */
    record Selection(List<CharacterizationCase> cases, int random, int unknown, int tooLarge) {}

    private CaseSelector() {}

    static Selection select(List<ReferenceRun> runs) {
        List<CharacterizationCase> cases = new ArrayList<>();
        Map<String, Integer> numberInGroup = new HashMap<>();
        int random = 0;
        int unknown = 0;
        int tooLarge = 0;
        for (ReferenceRun run : runs) {
            if (run.kind() == ReferenceKind.NONDETERMINISTIC) {
                random++;
                continue;
            }
            RunResult result = run.original();
            if (run.kind() != ReferenceKind.COMPARED || result == null || result.unknown() || result.exitCode() == null) {
                unknown++;
                continue;
            }
            String stdout = normalizeLineEnds(result.stdout());
            if (tooLarge(run.input().text()) || tooLarge(stdout)) {
                tooLarge++;
                continue;
            }
            String group = run.input().group();
            String prefix = METHOD_PREFIX.getOrDefault(group, "input");
            // Счёт по префиксу, а не по группе: две незнакомые группы с префиксом input не дадут одно имя метода.
            int number = numberInGroup.merge(prefix, 1, Integer::sum);
            cases.add(new CharacterizationCase(
                    prefix + String.format("%02d", number),
                    group + " №" + number,
                    run.input().text(), stdout, result.exitCode(),
                    result.exception() == null ? null : result.exception().type()));
        }
        return new Selection(List.copyOf(cases), random, unknown, tooLarge);
    }

    /**
     * Единственная нормализация экспорта: CRLF → LF. Без неё тест, снятый на Windows, падал бы на Linux
     * только из-за System.lineSeparator(). В README это сказано.
     */
    static String normalizeLineEnds(String text) {
        return text.replace("\r\n", "\n");
    }

    private static boolean tooLarge(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length > MAX_LITERAL_BYTES;
    }
}
