package blueprints.diag;

import blueprints.project.Project;
import com.github.javaparser.ast.CompilationUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Находки последней разобранной версии; общее дерево проекта никогда не меняется. */
public final class FindingsService {
    private final NestedLoopRule nestedLoops = new NestedLoopRule();
    private final NestedLoopAdvisor advisor = new NestedLoopAdvisor();

    public List<Finding> find(Project project) {
        if (project.lastGood().isEmpty()) return List.of();
        CompilationUnit unit = project.lastGood().get();
        Optional<String> text = project.errors().isEmpty() ? Optional.of(project.source()) : SourceText.fromTokens(unit);
        return text.map(t -> find(unit, t)).orElse(List.of());
    }

    /** То же для готового дерева и его текста (тесты, утилиты). */
    public List<Finding> find(CompilationUnit unit, String source) {
        SourceText text = new SourceText(source);
        List<Finding> findings = new ArrayList<>();
        for (LoopPair pair : nestedLoops.find(unit)) {
            String id = NestedLoopAdvisor.RULE + "-" + (findings.size() + 1);
            findings.add(advisor.advise(pair, text, id));
        }
        DiagnosticContext context = new DiagnosticContext(unit, text);
        findings.addAll(new ArrayBoundaryRule().find(context));
        findings.addAll(new DeadPrivateMethodRule().find(context));
        findings.addAll(new ExtremumRule().find(context));
        findings.addAll(new AverageDivisorRule().find(context));
        findings.addAll(new ConditionAssignmentRule().find(context));
        findings.addAll(new StringEqualityRule().find(context));
        findings.addAll(new IntegerDivisionRule().find(context));
        return List.copyOf(findings);
    }
}
