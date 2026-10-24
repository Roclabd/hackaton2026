package blueprints.instrument;

import java.util.List;

/**
 * Покрытие изменённого кода — поле {@code changedCovered} вердикта (docs/API.md). Считается в операторах,
 * а не в строках текста: строку, где нет оператора (поле, сигнатура, импорт), выполнить нельзя.
 *
 * @param lines         сколько изменённых операторов измерено (имя — как в контракте)
 * @param covered       сколько из них выполнилось хотя бы на одном входе
 * @param minHits       на скольких входах выполнился самый «редкий» изменённый оператор; 0 — какой-то не выполнился
 *                      ни разу. Это число <b>входов</b>, а не выполнений: цикл, прошедший 100 раз на одном входе, даёт 1
 * @param inputsReached на скольких входах выполнился хотя бы один изменённый оператор
 * @param inputs        сколько входов учтено
 * @param onOriginal    вся сводка относится к эталону (при смешанной правке источник указан у каждого оператора)
 * @param statements    по каждому измеренному оператору: строка, число входов и принадлежность эталону
 */
public record ChangedCoverage(int lines, int covered, int minHits, int inputsReached, int inputs,
                              boolean onOriginal, List<Statement> statements) {

    /** Один оператор: строка (с 1), число входов и источник; false — кандидат, true — эталон. */
    public record Statement(int line, int inputs, boolean onOriginal) {
        public Statement(int line, int inputs) {
            this(line, inputs, false);
        }
    }

    public ChangedCoverage {
        statements = List.copyOf(statements);
    }

    /** Строки операторов, которые выполнились меньше чем на {@code count} входах, — без повторов, по возрастанию. */
    public List<Integer> linesBelow(int count) {
        return statements.stream().filter(s -> s.inputs() < count).map(Statement::line).distinct().sorted().toList();
    }
}
