package blueprints.diag;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * R4: направление сравнения противоречит назначению переменной экстремума. Намерение не доказано.
 * Назначение — по имени переменной (max, min, highest…); у нейтрального имени (best, result…) — по имени метода.
 * Кандидат — переменная for-each или элемент {@code a[i]} в цикле с индексом i.
 */
final class ExtremumRule {
    List<Finding> find(DiagnosticContext context) {
        List<Finding> found = new ArrayList<>();
        for (IfStmt check : context.unit.findAll(IfStmt.class)) {
            if (context.methodId(check).isEmpty() || check.getElseStmt().isPresent()) continue;
            Expression condition = DiagnosticContext.unwrapped(check.getCondition());
            if (!(condition instanceof BinaryExpr compare)) continue;
            Statement then = check.getThenStmt();
            if (then.isBlockStmt() && then.asBlockStmt().getStatements().size() == 1) then = then.asBlockStmt().getStatement(0);
            if (!then.isExpressionStmt() || !(then.asExpressionStmt().getExpression() instanceof AssignExpr assign)
                    || assign.getOperator() != AssignExpr.Operator.ASSIGN || !(assign.getTarget() instanceof NameExpr target)) continue;
            Expression candidate = assign.getValue();
            String left = compare.getLeft().toString(), right = compare.getRight().toString();
            boolean targetLeft = left.equals(target.toString()) && right.equals(candidate.toString());
            boolean targetRight = right.equals(target.toString()) && left.equals(candidate.toString());
            if (!targetLeft && !targetRight) continue;
            // Направление — по точному имени (max, min, highest…): maxBudget, maximumAllowed — это потолки, а не поиск максимума.
            int direction = exactIntent(target.getNameAsString());
            // maxDelay, minPrice… — да, если переменная начинается как поиск экстремума: с первого элемента данных
            // или с крайней константы. Потолок (maxBudget из параметра) так не начинается — его не трогаем.
            if (direction == 0 && runningExtremum(target, check)) direction = intent(target.getNameAsString());
            if (direction == 0 && neutral(target.getNameAsString())) {
                direction = intent(check.findAncestor(MethodDeclaration.class).map(MethodDeclaration::getNameAsString).orElse(""));
            }
            if (direction == 0 || !context.integral(target) || !loopElement(candidate, check, context)) continue;
            BinaryExpr.Operator wrong = direction > 0
                    ? (targetLeft ? BinaryExpr.Operator.GREATER : BinaryExpr.Operator.LESS)
                    : (targetLeft ? BinaryExpr.Operator.LESS : BinaryExpr.Operator.GREATER);
            if (compare.getOperator() != wrong) continue;
            BinaryExpr.Operator corrected = wrong == BinaryExpr.Operator.LESS ? BinaryExpr.Operator.GREATER : BinaryExpr.Operator.LESS;
            String id = "R4-" + (found.size() + 1);
            found.add(context.finding("R4", compare, "Возможно, сравнение направлено обратно",
                    "«" + target + "» по " + (exactIntent(target.getNameAsString()) != 0 ? "имени" : "имени метода") + " ищет "
                            + (direction > 0 ? "максимум" : "минимум") + ", но это условие сохраняет "
                            + (direction > 0 ? "меньшее" : "большее") + " значение. Имя — подсказка, а не доказательство ошибки.",
                    List.of("Переменная действительно должна хранить " + (direction > 0 ? "максимум" : "минимум"),
                            "Отдельно проверить начальное значение и пустой массив"),
                    context.bugfix(id, compare, left + " " + corrected.asString() + " " + right,
                            "Развернуть сравнение", "Предложение по предполагаемому назначению; меняет результат и требует проверки."),
                    Finding.DEFECT, found.size() + 1));
        }
        return found;
    }

    /**
     * Кандидат — элемент перебираемых данных целого типа: переменная for-each или {@code a[i]}, где i — счётчик
     * цикла с индексом.
     */
    private static boolean loopElement(Expression candidate, IfStmt check, DiagnosticContext context) {
        if (candidate instanceof NameExpr name) {
            return context.integral(name) && check.findAncestor(ForEachStmt.class)
                    .filter(loop -> loop.getVariableDeclarator().getNameAsString().equals(name.getNameAsString())).isPresent();
        }
        if (candidate instanceof ArrayAccessExpr access && access.getIndex() instanceof NameExpr index
                && access.getName() instanceof NameExpr array) {
            boolean intArray = context.declaredType(array).map(t -> t.asString().matches("(int|long|short|byte)\\[\\]")).orElse(false);
            return intArray && check.findAncestor(ForStmt.class).filter(loop -> loop.getUpdate().stream()
                    .anyMatch(update -> update.toString().matches(java.util.regex.Pattern.quote(index.getNameAsString()) + "\\+\\+|\\+\\+"
                            + java.util.regex.Pattern.quote(index.getNameAsString())))).isPresent();
        }
        return false;
    }

    /** Локальная переменная объявлена со стартом a[0], list.get(0), 0 или Integer.MIN_VALUE / MAX_VALUE. */
    private static boolean runningExtremum(NameExpr target, IfStmt check) {
        return check.findAncestor(com.github.javaparser.ast.body.CallableDeclaration.class).map(method -> method
                .findAll(com.github.javaparser.ast.body.VariableDeclarator.class).stream()
                .filter(v -> v.getNameAsString().equals(target.getNameAsString()))
                .anyMatch(v -> v.getInitializer().map(DiagnosticContext::unwrapped).filter(init ->
                        init instanceof ArrayAccessExpr a && a.getIndex().toString().equals("0")
                        || init instanceof MethodCallExpr m && m.getNameAsString().equals("get") && m.getArguments().size() == 1
                                && m.getArgument(0).toString().equals("0")
                        || init.toString().matches("0|0L|(Integer|Long)\\.(MIN|MAX)_VALUE")).isPresent()))
                .orElse(false);
    }

    static int exactIntent(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.matches("max(imum)?|highest|largest|maxvalue|maxval")) return 1;
        if (lower.matches("min(imum)?|lowest|smallest|minvalue|minval")) return -1;
        return 0;
    }

    static int intent(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.matches("max(imum)?|highest|largest") || name.matches("(?:max|maximum|highest|largest)[A-Z_].*")) return 1;
        if (lower.matches("min(imum)?|lowest|smallest") || name.matches("(?:min|minimum|lowest|smallest)[A-Z_].*")) return -1;
        return 0;
    }

    /** Имя без направления: тогда назначение берём из имени метода. */
    static boolean neutral(String name) {
        return name.toLowerCase(Locale.ROOT).matches("best|result|res|answer|ans|extreme");
    }
}
