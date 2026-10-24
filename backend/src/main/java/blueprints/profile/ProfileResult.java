package blueprints.profile;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Ответ POST /api/projects/{id}/profile — замер роста по счётчикам выполнений, а не по секундомеру.
 *
 * @param supported  удалось ли измерить; false — в {@link #note()} сказано почему, чисел нет
 * @param sizes      размеры входа n, на которых измерены обе версии
 * @param before     оригинал: выполнения тел циклов в строках находки
 * @param after      заплатка: выполнения тел циклов в строках замены; null — заплатку не просили или её не измерить
 * @param factor     во сколько раз меньше выполнений у заплатки на наибольшем n; null — сравнивать нечего
 * @param note       что и как измерено, что пропущено — по-русски
 * @param durationMs сколько длился замер на сервере
 */
public record ProfileResult(boolean supported,
                            @JsonInclude(JsonInclude.Include.NON_NULL) List<Integer> sizes,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Series before,
                            Series after, Double factor, String note,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Long durationMs) {

    /**
     * Ряд счётчиков одной версии.
     *
     * @param counts сколько раз выполнились тела измеряемых циклов на каждом размере из {@code sizes}
     * @param model  лучшая модель роста: 1, log n, n, n log n, n², n³
     * @param slope  наклон на лог-лог шкале (n² — около 2, n — около 1), округлён до сотых
     * @param lines  [первая, последняя] строка, циклы которой считались
     * @param loops  сколько циклов в этих строках
     */
    public record Series(List<Long> counts, String model, double slope, int[] lines, int loops) {}

    static ProfileResult unsupported(String note, long durationMs) {
        return new ProfileResult(false, null, null, null, null, note, durationMs);
    }
}
