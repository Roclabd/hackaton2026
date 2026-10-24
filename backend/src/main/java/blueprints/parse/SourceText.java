package blueprints.parse;

import com.github.javaparser.Position;
import com.github.javaparser.Range;

import java.util.ArrayList;
import java.util.List;

/**
 * Исходный текст с переводом позиций JavaParser (строка и колонка с 1, конец включительно) в смещения строки.
 *
 * Нужен, чтобы менять код точечно: правка узла заменяет ровно его диапазон, а весь остальной файл —
 * форматирование, комментарии, пустые строки — остаётся байт в байт. Табуляция считается за одну колонку,
 * как в JavaParser по умолчанию.
 */
public final class SourceText {

    private final String text;
    private final List<Integer> lineStarts = new ArrayList<>();

    public SourceText(String text) {
        this.text = text;
        lineStarts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lineStarts.add(i + 1);
            }
        }
    }

    /** Смещение символа в позиции JavaParser. */
    public int offset(Position position) {
        if (position.line < 1 || position.line > lineStarts.size()) {
            throw new IllegalArgumentException("Нет строки " + position.line);
        }
        return lineStarts.get(position.line - 1) + position.column - 1;
    }

    /** Текст диапазона как в файле, без нормализации. */
    public String slice(Range range) {
        return text.substring(offset(range.begin), offset(range.end) + 1);
    }

    /** Новый текст файла, где диапазон заменён на replacement. */
    public String replace(Range range, String replacement) {
        return text.substring(0, offset(range.begin)) + replacement + text.substring(offset(range.end) + 1);
    }

    /** Отступ строки, с которой начинается диапазон (пробелы и табы в начале строки). */
    public String indentOf(Range range) {
        int start = lineStarts.get(range.begin.line - 1);
        int end = start;
        while (end < text.length() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
            end++;
        }
        return text.substring(start, end);
    }

    public String text() {
        return text;
    }
}
