package blueprints.graph;

import java.util.List;

/** Схема: узлы и рёбра. Неизменяемая — новая версия кода даёт новую схему. */
public record Graph(List<GraphNode> nodes, List<GraphEdge> edges) {

    public static Graph empty() {
        return new Graph(List.of(), List.of());
    }
}
