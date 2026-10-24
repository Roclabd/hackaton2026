package blueprints.diag;

import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** R4: направление сравнения противоречит имени переменной экстремума. Намерение не доказано. */
final class ExtremumRule {
    List<Finding> find(DiagnosticContext context) {
        List<Finding> found = new ArrayList<>();
        for (IfStmt check : context.unit.findAll(IfStmt.class)) {
            if (context.methodId(check).isEmpty() || check.getElseStmt().isPresent()) continue;
            Expression condition = DiagnosticContext.unwrapped(check.getCondition());
            if (!(condition instanceof BinaryExpr compare) || !(compare.getLeft() instanceof NameExpr left)
                    || !(compare.getRight() instanceof NameExpr right)) continue;
            Statement then = check.getThenStmt();
            if (then.isBlockStmt() && then.asBlockStmt().getStatements().size() == 1) then = then.asBlockStmt().getStatement(0);
            if (!then.isExpressionStmt() || !(then.asExpressionStmt().getExpression() instanceof AssignExpr assign)
                    || assign.getOperator() != AssignExpr.Operator.ASSIGN || !(assign.getTarget() instanceof NameExpr target)
                    || !(assign.getValue() instanceof NameExpr candidate)) continue;
            boolean targetLeft = target.equals(left) && candidate.equals(right);
            boolean targetRight = target.equals(right) && candidate.equals(left);
            if (!targetLeft && !targetRight) continue;
            int direction = intent(target.getNameAsString());
            if (direction == 0 || !context.integral(target) || !context.integral(candidate)) continue;
            var loop = check.findAncestor(ForEachStmt.class);
            if (loop.isEmpty() || !loop.get().getVariableDeclarator().getNameAsString().equals(candidate.getNameAsString())) continue;
            BinaryExpr.Operator wrong = direction > 0
                    ? (targetLeft ? BinaryExpr.Operator.GREATER : BinaryExpr.Operator.LESS)
                    : (targetLeft ? BinaryExpr.Operator.LESS : BinaryExpr.Operator.GREATER);
            if (compare.getOperator() != wrong) continue;
            BinaryExpr.Operator corrected = wrong == BinaryExpr.Operator.LESS ? BinaryExpr.Operator.GREATER : BinaryExpr.Operator.LESS;
            String id = "R4-" + (found.size() + 1);
            found.add(context.finding("R4", compare, "Возможно, сравнение направлено обратно",
                    "Имя «" + target + "» предполагает поиск " + (direction > 0 ? "максимума" : "минимума")
                            + ", но это условие сохраняет " + (direction > 0 ? "меньшее" : "большее")
                            + " значение. Имя — подсказка, а не доказательство ошибки.",
                    List.of("Переменная действительно должна хранить " + (direction > 0 ? "максимум" : "минимум"),
                            "Отдельно проверить начальное значение и пустой массив"),
                    context.bugfix(id, compare, left + " " + corrected.asString() + " " + right,
                            "Развернуть сравнение", "Предложение по предполагаемому назначению; меняет результат и требует проверки."),
                    Finding.DEFECT, found.size() + 1));
        }
        return found;
    }

    private int intent(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.matches("max(imum)?|highest|largest") || name.matches("(?:max|maximum|highest|largest)[A-Z_].*")) return 1;
        if (lower.matches("min(imum)?|lowest|smallest") || name.matches("(?:min|minimum|lowest|smallest)[A-Z_].*")) return -1;
        return 0;
    }
}
