package blueprints.diag;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * R15: вложенный цикл по индексам, в котором элементы сравниваются на равенство —
 * {@code for (i …) for (j …) if (a[j] == a[i])}. Это поиск совпадений перебором: n² сравнений вместо
 * одного прохода с подсчётом в HashMap. R1 видит ту же ошибку только у пары for-each, здесь — циклы со счётчиком
 * (хотя бы один из двух).
 *
 * Консервативно: оба цикла зависят от данных (не «до 26» и не по enum), во внутреннем if одна сторона ==
 * или equals — элемент, выбранный внутренним индексом, другая — элемент внешнего (или сама внешняя переменная).
 * Обход матрицы a[i][j] и сравнение индексов i == j находкой не считаются. Заплатки нет.
 */
final class NestedIndexLoopRule {

    static final String RULE = "R15";

    List<Finding> find(DiagnosticContext context) {
        List<Finding> found = new ArrayList<>();
        List<Node> reported = new ArrayList<>();
        for (Statement statement : context.unit.findAll(Statement.class, s -> s instanceof ForStmt || s instanceof ForEachStmt)) {
            Optional<IndexLoop> outer = IndexLoop.of(statement).filter(IndexLoop::growing);
            if (outer.isEmpty() || context.methodId(statement).isEmpty() || DiagnosticContext.inputLoop(statement)
                    || reported.stream().anyMatch(r -> r.isAncestorOf(statement))) continue;
            Optional<IfStmt> match = firstMatch(outer.get());
            if (match.isEmpty()) continue;
            reported.add(statement);
            found.add(new Finding(RULE + "-" + (found.size() + 1), RULE, context.methodId(statement).orElseThrow(),
                    DiagnosticContext.lines(statement), "Вложенный цикл по индексам",
                    "Для каждого шага внешнего цикла внутренний цикл заново просматривает данные и сравнивает элементы ("
                            + "«" + match.get().getCondition() + "»): при n элементах это порядка n² сравнений. "
                            + "Один проход с подсчётом в HashMap (значение → сколько раз встретилось) или множество "
                            + "находят совпадение за одно обращение.",
                    "n²", "n",
                    List.of("У элементов согласованы equals и hashCode (для чисел и строк — да)",
                            "Порядок, в котором находятся совпадения, не влияет на вывод",
                            "Заплатки нет: перестройку на индекс нужно писать вручную и проверить на ваших входах"),
                    List.of(), Finding.INEFFICIENCY));
        }
        return found;
    }

    private static Optional<IfStmt> firstMatch(IndexLoop outer) {
        for (Statement statement : outer.body().findAll(Statement.class, s -> s instanceof ForStmt || s instanceof ForEachStmt)) {
            Optional<IndexLoop> inner = IndexLoop.of(statement).filter(IndexLoop::growing);
            // Пара for-each — забота R1; for (y : x.items()) — обход вложенной структуры, а не второго списка.
            if (inner.isEmpty() || !outer.counting() && !inner.get().counting()
                    || statement instanceof ForEachStmt each && IndexLoop.mentions(each.getIterable(), outer.variable())) continue;
            for (IfStmt check : inner.get().body().findAll(IfStmt.class)) {
                if (compares(check.getCondition(), outer, inner.get())) return Optional.of(check);
            }
        }
        return Optional.empty();
    }

    /** В условии есть == или equals между элементом внутреннего цикла и элементом (значением) внешнего. */
    private static boolean compares(Expression condition, IndexLoop outer, IndexLoop inner) {
        for (BinaryExpr equality : condition.findAll(BinaryExpr.class, b -> b.getOperator() == BinaryExpr.Operator.EQUALS)) {
            if (pairs(equality.getLeft(), equality.getRight(), outer, inner)) return true;
        }
        for (MethodCallExpr call : condition.findAll(MethodCallExpr.class, c -> c.getNameAsString().equals("equals")
                && c.getArguments().size() == 1 && c.getScope().isPresent())) {
            if (pairs(call.getScope().get(), call.getArgument(0), outer, inner)) return true;
        }
        return false;
    }

    private static boolean pairs(Expression a, Expression b, IndexLoop outer, IndexLoop inner) {
        return inner.selects(a, outer.variable()) && outerValue(b, outer, inner)
                || inner.selects(b, outer.variable()) && outerValue(a, outer, inner);
    }

    /** a[i], сама i (findMissing: «есть ли значение v в массиве») или локальная x = a[i], объявленная до внутреннего цикла. */
    private static boolean outerValue(Expression e, IndexLoop outer, IndexLoop inner) {
        if (outer.selects(e, inner.variable()) || outer.isVariable(e)) return true;
        if (!(DiagnosticContext.unwrapped(e) instanceof NameExpr name)) return false;
        return outer.body().findAll(VariableDeclarator.class).stream()
                .filter(v -> v.getNameAsString().equals(name.getNameAsString()) && !inner.loop().isAncestorOf(v))
                .anyMatch(v -> v.getInitializer().filter(init -> outer.selects(init, inner.variable())).isPresent());
    }
}
