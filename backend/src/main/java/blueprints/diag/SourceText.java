package blueprints.diag;

import com.github.javaparser.JavaToken;
import com.github.javaparser.Position;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Исходный текст файла с переводом «строка:столбец» дерева разбора в смещения в строке.
 *
 * Заплатка меняет текст ровно в диапазоне одного узла, а остальной файл не трогает —
 * так в ней не меняются ни форматирование, ни комментарии, ни номера строк выше.
 */
final class SourceText {

    private final String text;
    private final List<Integer> lineStarts;

    SourceText(String text) {
        this.text = text;
        this.lineStarts = lineStarts(text);
    }

    /**
     * Текст, из которого разобрано дерево, — склейкой его лексем (в них есть и пробелы, и комментарии).
     * Нужен, когда текущий текст проекта не разбирается и дерево — от прошлой версии.
     */
    static Optional<String> fromTokens(CompilationUnit unit) {
        Optional<JavaToken> any = unit.getTokenRange().map(TokenRange::getBegin);
        if (any.isEmpty()) {
            return Optional.empty();
        }
        JavaToken first = any.get();
        while (first.getPreviousToken().isPresent()) {
            first = first.getPreviousToken().get();
        }
        StringBuilder result = new StringBuilder();
        for (Optional<JavaToken> t = Optional.of(first); t.isPresent(); t = t.get().getNextToken()) {
            result.append(t.get().getText());
        }
        return Optional.of(result.toString());
    }

    String text() {
        return text;
    }

    /** Перевод строки, принятый в файле: заплатка пишется тем же. */
    String lineSeparator() {
        return text.contains("\r\n") ? "\r\n" : "\n";
    }

    /** Пробелы в начале строки line (с 1). */
    String indentOf(int line) {
        int start = lineStarts.get(line - 1);
        int end = start;
        while (end < text.length() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
            end++;
        }
        return text.substring(start, end);
    }

    /**
     * Текст узла по его диапазону. Пусто, если диапазона нет или он не совпал с лексемами узла
     * (например, из-за табуляции в другой ширине) — тогда заплатку лучше не делать, чем сделать кривую.
     */
    Optional<String> textOf(Node node) {
        Optional<int[]> span = span(node);
        if (span.isEmpty()) {
            return Optional.empty();
        }
        String piece = text.substring(span.get()[0], span.get()[1]);
        boolean matches = node.getTokenRange().map(TokenRange::toString).filter(piece::equals).isPresent();
        return matches ? Optional.of(piece) : Optional.empty();
    }

    /**
     * Текст узла {@code container}, в котором диапазоны вложенных узлов заменены. Пусто — если текст контейнера
     * не совпал с лексемами (см. {@link #textOf}) или правка выходит за его границы.
     */
    Optional<String> textWithEdits(Node container, Map<Node, String> edits) {
        Optional<int[]> outer = span(container);
        if (textOf(container).isEmpty() || outer.isEmpty()) {
            return Optional.empty();
        }
        List<Map.Entry<int[], String>> spans = new ArrayList<>();
        for (Map.Entry<Node, String> edit : edits.entrySet()) {
            Optional<int[]> inner = span(edit.getKey());
            if (inner.isEmpty() || inner.get()[0] < outer.get()[0] || inner.get()[1] > outer.get()[1]) {
                return Optional.empty();
            }
            spans.add(Map.entry(inner.get(), edit.getValue()));
        }
        // С конца к началу: тогда смещения ещё не тронутых правок остаются верными.
        spans.sort((a, b) -> Integer.compare(b.getKey()[0], a.getKey()[0]));
        StringBuilder piece = new StringBuilder(text.substring(outer.get()[0], outer.get()[1]));
        for (Map.Entry<int[], String> s : spans) {
            piece.replace(s.getKey()[0] - outer.get()[0], s.getKey()[1] - outer.get()[0], s.getValue());
        }
        return Optional.of(piece.toString());
    }

    /** Новый полный текст: диапазон узла заменён на replacement. Вызывать после успешного textOf. */
    String replace(Node node, String replacement) {
        int[] s = span(node).orElseThrow();
        return text.substring(0, s[0]) + replacement + text.substring(s[1]);
    }

    /** [начало, конец) узла в строке; конец диапазона в JavaParser включительный. */
    private Optional<int[]> span(Node node) {
        return node.getRange().flatMap(r -> {
            int begin = offset(r.begin);
            int end = offset(r.end) + 1;
            return begin >= 0 && end <= text.length() && begin < end ? Optional.of(new int[]{begin, end}) : Optional.empty();
        });
    }

    private int offset(Position position) {
        if (position.line < 1 || position.line > lineStarts.size()) {
            return -1;
        }
        return lineStarts.get(position.line - 1) + position.column - 1;
    }

    /** Начала строк: перевод строки — \r\n, \n или \r, как и у JavaParser. */
    private static List<Integer> lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                i++;
                starts.add(i + 1);
            } else if (c == '\n' || c == '\r') {
                starts.add(i + 1);
            }
        }
        return starts;
    }
}
