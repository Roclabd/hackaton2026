package blueprints.graph;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Узел схемы — форма из docs/API.md.
 *
 * @param id       стабильный id (см. {@link NodeIds}); не зависит от позиции в файле
 * @param label    что показать на узле, например «static int run(String, Interpreter)»
 * @param code     исходный текст фрагмента. На чертеже метода — ровно то, что подставится в поле правки
 *                 (оператор целиком или условие if/while)
 * @param lines    [первая, последняя] строка в исходнике, с 1
 * @param parent   id родительского узла или null
 * @param branch   в какой ветке родителя лежит узел (then/else/body/…); на уровне 0 — null
 * @param badges   номера правил находок, которые сработали на узле (R1…)
 * @param editable можно ли править узел командой editStatement
 * @param branches ветки контейнера (if, loop, switch, try) в порядке показа; у остальных узлов — null
 * @param expr     дерево выражения узла («подробная схема»); на карте и там, где выражения нет, — null,
 *                 и в JSON поля тогда нет вовсе
 */
public record GraphNode(String id, NodeKind kind, String label, String code, int[] lines,
                        String parent, String branch, List<String> badges, boolean editable,
                        List<GraphBranch> branches,
                        @JsonInclude(JsonInclude.Include.NON_NULL) ExprNode expr) {

    /** Узел без дерева выражения — карта (уровень 0). */
    public GraphNode(String id, NodeKind kind, String label, String code, int[] lines,
                     String parent, String branch, List<String> badges, boolean editable,
                     List<GraphBranch> branches) {
        this(id, kind, label, code, lines, parent, branch, badges, editable, branches, null);
    }
}
