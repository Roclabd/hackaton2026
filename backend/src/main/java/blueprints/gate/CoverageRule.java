package blueprints.gate;

import blueprints.instrument.ChangedCoverage;

import java.util.ArrayList;
import java.util.List;

/**
 * Правило плана 3.6 (п. 4): изменённый код выполнился меньше чем на {@link #MIN_INPUTS} входах → не «совпало»,
 * а «проверка неполна». Совпадение на входах, которые не дошли до правки, ничего о правке не говорит.
 */
final class CoverageRule {

    static final int MIN_INPUTS = 3;

    private CoverageRule() {}

    /**
     * @param coverage покрытие по сравнённым входам; отсутствие правок и сбой измерения различает сборщик вердикта
     * @return null — покрытия хватает или сводки нет; иначе — причина по-русски для incompleteReason
     */
    static String incompleteReason(ChangedCoverage coverage) {
        if (coverage == null || coverage.lines() == 0 || coverage.minHits() >= MIN_INPUTS) {
            return null;
        }
        // В смешанной правке номера строк двух версий различаются — сначала называем слабые строки кандидата.
        boolean original = coverage.onOriginal() || coverage.statements().stream()
                .noneMatch(s -> !s.onOriginal() && s.inputs() < MIN_INPUTS);
        List<Integer> weak = coverage.statements().stream()
                .filter(s -> s.inputs() < MIN_INPUTS && (coverage.onOriginal() || s.onOriginal() == original))
                .map(ChangedCoverage.Statement::line).distinct().sorted().toList();
        String what = original ? "Удалённый или заменённый код эталона" : "Изменённый код";
        String target = "Добавьте вход, который доходит до " + toLines(weak) + (original ? " эталона" : "");
        if (coverage.inputsReached() == 0) {
            return what + " выполнился только на 0 из " + coverage.inputs()
                    + " входов: проверка не затронула изменение. " + target;
        }
        if (coverage.inputs() < MIN_INPUTS) {
            // Изменение выполнилось, но разных входов всего меньше трёх — обычно программа не читает ввод.
            return what + " выполнился на " + coverage.inputsReached() + " из " + coverage.inputs()
                    + " входов, но для «совпало» нужно хотя бы " + MIN_INPUTS + " разных входа: программа, похоже, "
                    + "не читает ввод, и генератор не может сделать разные запуски. Вывод сравнён, но одного запуска мало";
        }
        if (coverage.inputsReached() < MIN_INPUTS) {
            return what + " выполнился только на " + coverage.inputsReached() + " из " + coverage.inputs()
                    + " входов — меньше " + MIN_INPUTS + " разных. " + target;
        }
        return what + " на " + atLines(weak) + " выполнился меньше чем на " + MIN_INPUTS + " из " + coverage.inputs()
                + " входов (самое меньшее — на " + coverage.minHits() + "). " + target;
    }

    /** «строки 5» или «строк 12–14, 20» — для «доходит до …». */
    static String toLines(List<Integer> lines) {
        return (lines.size() == 1 ? "строки " : "строк ") + ranges(lines);
    }

    /** «строке 5» или «строках 12–14, 20» — для «на …». */
    private static String atLines(List<Integer> lines) {
        return (lines.size() == 1 ? "строке " : "строках ") + ranges(lines);
    }

    /** Возрастающий список строк → «12–14, 20»: подряд идущие строки сливаются в диапазон. */
    static String ranges(List<Integer> lines) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            int j = i;
            while (j + 1 < lines.size() && lines.get(j + 1) == lines.get(j) + 1) {
                j++;
            }
            parts.add(i == j ? String.valueOf(lines.get(i)) : lines.get(i) + "–" + lines.get(j));
            i = j + 1;
        }
        return String.join(", ", parts);
    }
}
