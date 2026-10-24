package blueprints.instrument;

import java.util.List;

/**
 * Копия кода со счётчиками — только для замера, вердикт по ней не выносится (план, раздел 3.4).
 *
 * @param source         текст копии: исходник со вставленными {@code __BlueprintsCoverage.HITS[k]++;}
 *                       и вспомогательным классом в конце файла
 * @param statementLines для счётчика k — строка (с 1), с которой начинается оператор в исходном тексте
 */
public record InstrumentedSource(String source, List<Integer> statementLines) {

    public InstrumentedSource {
        statementLines = List.copyOf(statementLines);
    }

    /** Сколько операторов под счётчиками. */
    public int counters() {
        return statementLines.size();
    }
}
