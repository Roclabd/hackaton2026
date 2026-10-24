package blueprints.diag;

import java.util.ArrayList;
import java.util.List;

/**
 * Сборка нового фрагмента кода построчно с уровнями отступа.
 * Первая строка встаёт на место старого оператора (отступ у неё уже есть в файле), остальные получают базовый отступ.
 */
final class CodeLines {

    private record Line(int depth, String code) {}

    private final List<Line> lines = new ArrayList<>();

    CodeLines add(int depth, String code) {
        lines.add(new Line(depth, code));
        return this;
    }

    /** Многострочный текст (например, тело if из исходника) — каждую строку на нужный уровень. */
    CodeLines addBlock(int depth, String code) {
        for (String line : code.split("\\R")) {
            lines.add(new Line(depth, line));
        }
        return this;
    }

    int size() {
        return lines.size();
    }

    String render(String baseIndent, String lineSeparator) {
        String unit = baseIndent.contains("\t") ? "\t" : "    ";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (i > 0) {
                out.append(lineSeparator).append(baseIndent);
            }
            out.append(unit.repeat(line.depth())).append(line.code());
        }
        return out.toString();
    }
}
