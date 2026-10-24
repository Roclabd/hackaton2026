package blueprints.commands;

import blueprints.graph.Blueprint;
import blueprints.graph.GraphNode;
import blueprints.graph.NodeKind;
import blueprints.parse.JavaFiles;
import blueprints.parse.ParseError;
import blueprints.parse.ParseOutcome;
import blueprints.parse.SourceParser;
import blueprints.parse.SourceText;
import blueprints.project.Blueprints;
import blueprints.project.Project;
import blueprints.project.ProjectNotFoundException;
import blueprints.project.ProjectStore;
import blueprints.project.VersionConflictException;
import blueprints.runner.CompileResult;
import blueprints.runner.InMemoryCompiler;
import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;

import java.util.*;

/**
 * Правка узла чертежа: заменить оператор целиком или условие if/while/do.
 *
 * Как устроено:
 *   1. по id узла находим его в дереве разбора и берём точный диапазон в исходнике;
 *   2. новый текст проверяем парсером: это одно выражение (для условия) или один оператор;
 *   3. подставляем текст ровно в этот диапазон — остальной файл не меняется ни на символ;
 *   4. весь файл разбираем, сверяем лексемы вне узла (см. {@link RestOfFileGuard}) и компилируем javac в памяти;
 *      не вышло — правка отклонена, версия прежняя;
 *   5. сохраняем атомарно с проверкой версии и строим новый чертёж.
 */
public final class EditStatementCommand {

    private final ProjectStore store;
    private final Blueprints blueprints = new Blueprints();
    private final SourceParser parser = new SourceParser();
    private final InMemoryCompiler compiler;

    public EditStatementCommand(ProjectStore store, InMemoryCompiler compiler) {
        this.store = store;
        this.compiler = compiler;
    }

    public EditResult apply(String projectId, String nodeId, String rawText, int baseVersion) {
        Project project = store.find(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId));
        if (project.version() != baseVersion) {
            throw new VersionConflictException(project.version(), baseVersion);
        }
        String methodId = Blueprints.methodIdOf(nodeId);
        Blueprint before = blueprints.forMethod(project, methodId).orElse(null);
        if (before == null) {
            return EditResult.rejected(project, methodId, null, List.of(new EditResult.Problem(1, 1,
                    project.errors().isEmpty() ? "Метод не найден: " + methodId : "Сначала исправьте ошибки разбора в коде")));
        }
        GraphNode node = before.graph().nodes().stream().filter(n -> n.id().equals(nodeId)).findFirst().orElse(null);
        Node target = before.astById().get(nodeId);
        if (node == null || target == null || target.getRange().isEmpty()) {
            return EditResult.rejected(project, methodId, before, List.of(new EditResult.Problem(1, 1, "Узел не найден: " + nodeId)));
        }
        if (!node.editable()) {
            return EditResult.rejected(project, methodId, before, List.of(new EditResult.Problem(1, 1,
                    "Этот узел пока нельзя править на схеме — измените его в коде")));
        }

        String text = rawText == null ? "" : rawText.strip();
        List<ParseError> fieldErrors = editsCondition(node) ? parser.checkExpression(text) : parser.checkStatement(text);
        if (text.isEmpty() || !fieldErrors.isEmpty()) {
            String reason = text.isEmpty() ? "Пустой текст" : fieldErrors.getFirst().message();
            return EditResult.rejected(project, methodId, before, List.of(new EditResult.Problem(1, 1,
                    (editsCondition(node) ? "Ожидалось одно Java-выражение: " : "Ожидался один Java-оператор: ") + reason)));
        }

        Range range = target.getRange().get();
        String newSource = new SourceText(project.source()).replace(range, text);

        List<EditResult.Problem> problems = checkNewSource(target, range, text, editsCondition(node), newSource);
        if (!problems.isEmpty()) {
            return EditResult.rejected(project, methodId, before, problems);
        }

        Project updated = store.updateSource(projectId, baseVersion, newSource);
        Blueprint after = blueprints.forMethod(updated, methodId).orElse(null);
        return new EditResult(true, updated, methodId, after, IdMatcher.match(before, after), List.of());
    }

    /** У if, while и do правится условие; у остальных узлов — оператор целиком. */
    private static boolean editsCondition(GraphNode node) {
        return node.kind() == NodeKind.IF || node.kind() == NodeKind.LOOP;
    }

    private List<EditResult.Problem> checkNewSource(Node target, Range range, String text, boolean asCondition,
                                                    String newSource) {
        ParseOutcome outcome = parser.parse(newSource);
        if (!outcome.ok()) {
            return outcome.errors().stream()
                    .map(e -> new EditResult.Problem(e.line(), e.column(), "Не разбирается: " + e.message()))
                    .toList();
        }
        // Комментарий в тексте правки не должен «съесть» соседний код (аудит v4 №10)
        if (!RestOfFileGuard.restUnchanged(target.findCompilationUnit().orElseThrow(), range, outcome.unit().get(),
                text, asCondition)) {
            return List.of(new EditResult.Problem(1, 1,
                    "Комментарий в тексте правки закомментирует код после узла — уберите комментарий или перенесите его"));
        }
        CompileResult compiled = compiler.compile(JavaFiles.primaryTypeName(outcome.unit().get()), newSource);
        if (compiled.ok()) {
            return List.of();
        }
        return compiled.messages().stream()
                .filter(m -> m.kind().equals("ERROR"))
                .map(m -> new EditResult.Problem(m.line(), m.column(), "Не компилируется: " + m.text()))
                .toList();
    }
}
