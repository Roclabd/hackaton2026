package blueprints.diag;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;

import java.util.ArrayList;
import java.util.List;

/** R2: включённая верхняя граница в простом индексном обходе массива. */
final class ArrayBoundaryRule {
    List<Finding> find(DiagnosticContext context) {
        List<Finding> found = new ArrayList<>();
        for (ForStmt loop : context.unit.findAll(ForStmt.class)) {
            if (context.methodId(loop).isEmpty() || loop.getCompare().isEmpty()) continue;
            Expression expression = DiagnosticContext.unwrapped(loop.getCompare().get());
            if (!(expression instanceof BinaryExpr compare) || compare.getOperator() != BinaryExpr.Operator.LESS_EQUALS
                    || !(compare.getLeft() instanceof NameExpr index) || context.arrayLength(compare.getRight()).isEmpty()) continue;
            String array = context.arrayLength(compare.getRight()).get().getScope().toString();
            String counter = index.getNameAsString();
            if (loop.getInitialization().size() != 1 || loop.getUpdate().size() != 1) continue;
            if (!(loop.getInitialization().get(0) instanceof VariableDeclarationExpr declaration)
                    || declaration.getVariables().size() != 1) continue;
            VariableDeclarator variable = declaration.getVariable(0);
            if (!variable.getNameAsString().equals(counter) || !variable.getTypeAsString().equals("int")
                    || variable.getInitializer().filter(i -> i.isIntegerLiteralExpr()
                    && i.asIntegerLiteralExpr().getValue().equals("0")).isEmpty()) continue;
            if (!(loop.getUpdate().get(0) instanceof UnaryExpr update)
                    || !update.getExpression().toString().equals(counter)
                    || (update.getOperator() != UnaryExpr.Operator.POSTFIX_INCREMENT
                    && update.getOperator() != UnaryExpr.Operator.PREFIX_INCREMENT)) continue;
            // Ветвления, досрочный выход и изменения индекса могут делать включённую границу намеренной.
            if (!straightLine(loop.getBody()) || mutates(loop.getBody(), counter) || mutates(loop.getBody(), array)) continue;
            boolean access = loop.getBody().findAll(ArrayAccessExpr.class).stream()
                    .anyMatch(a -> a.getName().toString().equals(array) && a.getIndex().toString().equals(counter));
            if (!access) continue;
            String id = "R2-" + (found.size() + 1);
            String replacement = compare.getLeft() + " < " + compare.getRight();
            found.add(context.finding("R2", compare, "Возможный выход за границу массива",
                    "Условие допускает " + counter + " == " + array + ".length. Если эта итерация достигнута, обращение "
                            + array + "[" + counter + "] выходит за границу: последний индекс равен length - 1.",
                    List.of("Цикл предназначен для перебора элементов массива", "До граничной итерации выполнение доходит без исключения"),
                    context.bugfix(id, compare, replacement, "Исключить индекс length",
                            "Меняет поведение на граничной итерации; проверять как исправление дефекта."), Finding.DEFECT, found.size() + 1));
        }
        return found;
    }

    private boolean straightLine(Statement body) {
        if (body instanceof ExpressionStmt) return true;
        return body instanceof BlockStmt block && block.getStatements().stream().allMatch(ExpressionStmt.class::isInstance);
    }

    private boolean mutates(Statement body, String name) {
        return body.findAll(AssignExpr.class).stream().anyMatch(a -> a.getTarget().toString().equals(name))
                || body.findAll(UnaryExpr.class).stream().anyMatch(u -> u.getExpression().toString().equals(name)
                && List.of(UnaryExpr.Operator.PREFIX_INCREMENT, UnaryExpr.Operator.POSTFIX_INCREMENT,
                UnaryExpr.Operator.PREFIX_DECREMENT, UnaryExpr.Operator.POSTFIX_DECREMENT).contains(u.getOperator()));
    }
}

