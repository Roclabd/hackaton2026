package blueprints.api;

import blueprints.graph.Graph;
import blueprints.parse.ParseError;
import blueprints.project.CompileError;
import blueprints.project.Project;

import java.util.List;

/**
 * Ответ на загрузку и правку кода — форма из docs/API.md.
 *
 * @param errors        ошибки разбора (синтаксис)
 * @param compileErrors ошибки javac: код разбирается, но не компилируется (например, {@code if (x = 1)})
 * @param map           схема уровня 0 (классы, поля, методы, вызовы). Если код сейчас не разбирается,
 *                      здесь последняя рабочая схема, а в errors — причины.
 */
public record ProjectResponse(String id, int version, String fileName, String source, List<ParseError> errors,
                              List<CompileError> compileErrors, Graph map) {

    static ProjectResponse of(Project project) {
        return new ProjectResponse(project.id(), project.version(), project.fileName(),
                project.source(), project.errors(), project.compileErrors(), project.map());
    }
}
