package blueprints.project;

import blueprints.graph.Blueprint;
import blueprints.graph.GraphNode;
import blueprints.graph.MethodBlueprintBuilder;
import blueprints.graph.ProjectMapBuilder;
import blueprints.parse.SourceText;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;

import java.util.Optional;

/** Чертёж метода для конкретной версии проекта. Строится по требованию: 1000 строк разбираются за миллисекунды. */
public final class Blueprints {

    private final ProjectMapBuilder mapBuilder = new ProjectMapBuilder();
    private final MethodBlueprintBuilder blueprintBuilder = new MethodBlueprintBuilder();

    /**
     * Пусто — если метода нет в последней корректной версии или текущий текст не разбирается:
     * чертёж по устаревшему дереву дал бы неверные строки.
     */
    public Optional<Blueprint> forMethod(Project project, String methodId) {
        if (!project.errors().isEmpty() || project.lastGood().isEmpty()) {
            return Optional.empty();
        }
        CompilationUnit unit = project.lastGood().get();
        Node declaration = mapBuilder.declarations(unit).get(methodId);
        if (declaration == null) {
            return Optional.empty();
        }
        String label = project.map().nodes().stream()
                .filter(n -> n.id().equals(methodId))
                .map(GraphNode::label)
                .findFirst()
                .orElse(methodId);
        return Optional.of(blueprintBuilder.build(methodId, label, declaration, new SourceText(project.source())));
    }

    /** id метода, которому принадлежит узел чертежа: всё до первого «/». */
    public static String methodIdOf(String nodeId) {
        int slash = nodeId.indexOf('/');
        return slash < 0 ? nodeId : nodeId.substring(0, slash);
    }
}
