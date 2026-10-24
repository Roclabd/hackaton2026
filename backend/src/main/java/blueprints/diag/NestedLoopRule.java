package blueprints.diag;

import blueprints.graph.ProjectMapBuilder;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.IfStmt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Правило R1 «Вложенный цикл»: for (x : a) { … for (y : b) { … if (x равно y) … } }.
 *
 * Ищет только пары, где условие сравнивает переменные двух циклов, — это признак
 * «поиска совпадений перебором», который заменяется индексом. Просто вложенные циклы
 * (например, обход строк и столбцов) находкой не считаются.
 */
final class NestedLoopRule {

    List<LoopPair> find(CompilationUnit unit) {
        Map<String, Node> declarations = new ProjectMapBuilder().declarations(unit);
        List<LoopPair> pairs = new ArrayList<>();
        declarations.forEach((methodId, declaration) -> {
            for (ForEachStmt outer : declaration.findAll(ForEachStmt.class)) {
                firstMatch(methodId, declaration, outer).ifPresent(pairs::add);
            }
        });
        pairs.sort(Comparator.comparingInt(p -> p.outer().getBegin().map(b -> b.line).orElse(0)));
        return pairs;
    }

    /** Первый подходящий внутренний цикл. findAll идёт в глубину по порядку, так что непосредственный — первым. */
    private Optional<LoopPair> firstMatch(String methodId, Node declaration, ForEachStmt outer) {
        String x = outer.getVariableDeclarator().getNameAsString();
        for (ForEachStmt inner : outer.getBody().findAll(ForEachStmt.class)) {
            // for (y : x.children()) — обход вложенной структуры, а не второго списка: это не n².
            if (mentions(inner.getIterable(), x)) {
                continue;
            }
            String y = inner.getVariableDeclarator().getNameAsString();
            for (IfStmt check : inner.getBody().findAll(IfStmt.class)) {
                Optional<EqualityTest> test = EqualityTest.recognize(check.getCondition(), x, y);
                if (test.isPresent()) {
                    return Optional.of(new LoopPair(methodId, declaration, outer, inner, check, test.get()));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean mentions(Expression expression, String name) {
        return expression.findAll(NameExpr.class).stream().anyMatch(n -> n.getNameAsString().equals(name));
    }
}
