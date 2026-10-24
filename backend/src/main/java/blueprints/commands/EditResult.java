package blueprints.commands;

import blueprints.graph.Blueprint;
import blueprints.project.Project;

import java.util.List;
import java.util.Map;

/**
 * Итог команды правки узла.
 *
 * @param project       новая версия (при ok) или текущая (при отказе)
 * @param idMap         старый id → новый id для узлов, чей id поменялся (сам узел и его потомки)
 * @param blueprint     чертёж метода после правки
 * @param compileErrors почему правка отклонена: не разобралось или не скомпилировалось
 */
public record EditResult(boolean ok, Project project, String methodId, Blueprint blueprint,
                         Map<String, String> idMap, List<Problem> compileErrors) {

    /** Проблема с местом: строка и колонка в новом тексте файла (для ошибок поля ввода — в самом поле). */
    public record Problem(int line, int column, String text) {}

    static EditResult rejected(Project project, String methodId, Blueprint blueprint, List<Problem> problems) {
        return new EditResult(false, project, methodId, blueprint, Map.of(), List.copyOf(problems));
    }
}
