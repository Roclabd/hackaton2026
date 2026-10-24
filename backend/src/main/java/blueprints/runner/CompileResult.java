package blueprints.runner;

import java.util.List;
import java.util.Map;

/**
 * Итог компиляции.
 *
 * @param classes  байткод по полному имени класса; пусто, если не скомпилировалось
 * @param messages ошибки и предупреждения javac с номерами строк
 */
public record CompileResult(boolean ok, Map<String, byte[]> classes, List<Message> messages) {

    public record Message(String kind, int line, int column, String text) {}

    static CompileResult success(Map<String, byte[]> classes, List<Message> messages) {
        return new CompileResult(true, Map.copyOf(classes), List.copyOf(messages));
    }

    static CompileResult failure(List<Message> messages) {
        return new CompileResult(false, Map.of(), List.copyOf(messages));
    }
}
