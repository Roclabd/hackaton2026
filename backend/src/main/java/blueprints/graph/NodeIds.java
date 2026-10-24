package blueprints.graph;

import java.util.List;

/**
 * Правила построения id узлов. Главное требование — id не зависит от позиции в файле:
 * вставка кода выше не меняет id остальных узлов, и фронт сохраняет выделение и раскладку.
 *
 * Уровень 0:
 *   класс  — C:Outer.Inner
 *   поле   — F:Outer.Inner.name
 *   метод  — M:Outer.Inner#name(Type1,Type2); конструктор — #&lt;init&gt;(…), блок инициализации — #&lt;clinit&gt;
 * Повтор одинакового id (например, два блока static {}) получает суффикс ~2, ~3…
 */
public final class NodeIds {

    private NodeIds() {}

    public static String type(String typeName) {
        return "C:" + typeName;
    }

    public static String field(String typeName, String fieldName) {
        return "F:" + typeName + "." + fieldName;
    }

    public static String method(String typeName, String methodName, List<String> parameterTypes) {
        return "M:" + typeName + "#" + methodName + "(" + String.join(",", parameterTypes) + ")";
    }

    /** Убирает пробелы из записи типа, чтобы «Map<String, Integer>» и «Map<String,Integer>» давали один id. */
    public static String normalizeType(String typeText) {
        return typeText.replaceAll("\\s+", "");
    }
}
