package blueprints.diag;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.List;
import java.util.Optional;

/** Структурные проверки, общие для заплаток: «двойной цикл, внутри ровно один if без else». */
final class LoopShape {

    private LoopShape() {}

    /** Единственный оператор тела: { s; } или просто s. Пусто, если операторов больше одного. */
    static Optional<Statement> sole(Statement body) {
        if (body instanceof BlockStmt block) {
            return block.getStatements().size() == 1 ? Optional.of(block.getStatement(0)) : Optional.empty();
        }
        return Optional.of(body);
    }

    /** Операторы тела: у блока — его содержимое, у одиночного оператора — он сам. */
    static List<Statement> statements(Statement body) {
        return body instanceof BlockStmt block ? block.getStatements() : List.of(body);
    }

    /** for (x : a) { for (y : b) { if (…) … } } — и ничего больше, if без else. */
    static boolean plainNestedIf(LoopPair p) {
        return sole(p.outer().getBody()).filter(s -> s == p.inner()).isPresent()
                && sole(p.inner().getBody()).filter(s -> s == p.check()).isPresent()
                && p.check().getElseStmt().isEmpty();
    }

    /**
     * Внешний цикл стоит прямо в блоке { … }: туда можно вставить объявление новой переменной.
     * С меткой (outer: for …) или без фигурных скобок (if (c) for …) объявление сломало бы код.
     */
    static boolean standsInBlock(ForEachStmt outer) {
        return outer.getParentNode().filter(BlockStmt.class::isInstance).isPresent();
    }

    /** Имя переменной, если выражение — просто имя (a, items). */
    static Optional<String> simpleName(Expression e) {
        return e instanceof NameExpr n ? Optional.of(n.getNameAsString()) : Optional.empty();
    }
}
