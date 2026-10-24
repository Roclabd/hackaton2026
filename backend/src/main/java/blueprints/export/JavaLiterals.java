package blueprints.export;

/**
 * Строка → литерал Java для сгенерированного теста.
 *
 * Многострочный вывод читается лучше текстовым блоком ({@code """ … """}), но текстовый блок молча
 * убирает пробелы в конце строк и по-своему трактует обратную косую черту. Поэтому он выбирается, только
 * когда текст переживёт его без изменений; иначе — обычная строка с экранированием. Кириллица остаётся
 * как есть: файл пишется в UTF-8.
 */
final class JavaLiterals {

    private JavaLiterals() {}

    /**
     * @param indent отступ строк текстового блока (совпадает с отступом закрывающих кавычек — он и снимается)
     */
    static String literal(String text, String indent) {
        return fitsTextBlock(text) ? textBlock(text, indent) : quoted(text);
    }

    /** Обычная строка в кавычках: управляющие символы и не-ASCII-пробелы — через экранирование. */
    static String quoted(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20 || c == 0x7f) {
                        // Восьмеричная запись: юникод-экранирование javac раскрыл бы ещё до разбора строк.
                        out.append(String.format("\\%03o", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /**
     * Текстовый блок безопасен, если текст кончается переводом строки (тогда закрывающие кавычки стоят
     * на своей строке и задают снимаемый отступ), а внутри нет того, что блок изменил бы:
     * пробелов в конце строк, обратной косой черты, тройных кавычек, табуляции, \r и прочих управляющих символов.
     */
    static boolean fitsTextBlock(String text) {
        if (text.isEmpty() || !text.endsWith("\n") || text.contains("\\") || text.contains("\"\"\"")) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\n' && (c < 0x20 || c == 0x7f)) {
                return false;
            }
            boolean lineEnd = i + 1 == text.length() || text.charAt(i + 1) == '\n';
            if (lineEnd && c != '\n' && Character.isWhitespace(c)) {
                return false;
            }
        }
        // Текст из одних переводов строк блок тоже передаёт, но читается он хуже, чем "\n\n".
        return !text.isBlank();
    }

    private static String textBlock(String text, String indent) {
        StringBuilder out = new StringBuilder("\"\"\"\n");
        for (String line : text.substring(0, text.length() - 1).split("\n", -1)) {
            // Пустые строки без отступа: в текстовом блоке они и так пустые.
            out.append(line.isEmpty() ? "" : indent + line).append('\n');
        }
        return out.append(indent).append("\"\"\"").toString();
    }
}
