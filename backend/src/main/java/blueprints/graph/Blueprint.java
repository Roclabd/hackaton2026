package blueprints.graph;

import com.github.javaparser.ast.Node;

import java.util.List;
import java.util.Map;

/**
 * Чертёж метода (уровень 1): схема для экрана + связь «id узла → узел дерева разбора».
 * Связь нужна командам правки: по id с экрана найти, какой кусок исходника менять.
 *
 * @param graph   узлы и рёбра exec — то, что уходит во фронт
 * @param astById id узла → оператор (или его условие) в дереве разбора
 */
public record Blueprint(String methodId, String label, Graph graph, Map<String, Node> astById) {

    /** Форма ответа API: без внутренних ссылок на дерево разбора. */
    public View view() {
        return new View(methodId, label, graph.nodes(), graph.edges());
    }

    public record View(String methodId, String label, List<GraphNode> nodes, List<GraphEdge> edges) {}
}
