package blueprints.commands;

import blueprints.graph.Blueprint;
import blueprints.graph.GraphNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Сопоставляет узлы чертежа до и после правки, чтобы фронт не потерял выделение.
 *
 * id узла зависит от его текста, поэтому у изменённого узла (и у потомков изменённого условия) id новый.
 * Сопоставляем по месту в структуре: путь «ветка#номер среди соседей» от корня метода одинаков,
 * если правка не меняла вид оператора.
 */
final class IdMatcher {

    private IdMatcher() {}

    static Map<String, String> match(Blueprint before, Blueprint after) {
        if (before == null || after == null) {
            return Map.of();
        }
        Map<String, String> beforeByPath = byPath(before.graph().nodes());
        Map<String, String> afterByPath = byPath(after.graph().nodes());
        Set<String> beforeIds = new HashSet<>(beforeByPath.values());
        Set<String> afterIds = new HashSet<>(afterByPath.values());
        Map<String, String> idMap = new LinkedHashMap<>();
        beforeByPath.forEach((path, oldId) -> {
            String newId = afterByPath.get(path);
            // Узел, который остался в чертеже под тем же id, не «переехал»: исчезнувший узел нельзя отображать
            // на чужой узел, занявший его место (иначе после удаления соседа выделение прыгнет на return).
            boolean oldVanished = !afterIds.contains(oldId);
            boolean newAppeared = newId != null && !beforeIds.contains(newId);
            if (oldVanished && newAppeared) {
                idMap.put(oldId, newId);
            }
        });
        return idMap;
    }

    /** Путь узла → id. Узлы в списке идут в порядке обхода: родитель раньше детей, соседи — по порядку. */
    private static Map<String, String> byPath(List<GraphNode> nodes) {
        Map<String, String> pathById = new HashMap<>();
        Map<String, Integer> siblingCounter = new HashMap<>();
        Map<String, String> result = new LinkedHashMap<>();
        for (GraphNode node : nodes) {
            String parentPath = pathById.getOrDefault(node.parent(), "");
            String slot = parentPath + "/" + node.branch();
            int index = siblingCounter.merge(slot, 1, Integer::sum);
            String path = slot + "#" + index;
            pathById.put(node.id(), path);
            result.put(path, node.id());
        }
        return result;
    }
}
