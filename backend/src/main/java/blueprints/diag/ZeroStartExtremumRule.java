package blueprints.diag;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * R12: максимум или минимум начинается с 0, а не с первого элемента. {@code int max = 0; for (v : a) if (v > max) max = v;}
 * вернёт 0 для массива из одних отрицательных чисел; минимум с нуля вернёт 0 для одних положительных.
 *
 * Срабатываем, только если:
 * <ul>
 *   <li>переменная — локальная целая или дробная, начальное значение — литерал 0;</li>
 *   <li>намерение видно по имени переменной (max, min, best, highest…) или метода (max, min…);</li>
 *   <li>в цикле того же метода есть {@code if (x > v) v = x} (для максимума) или {@code if (x < v) v = x} — значение
 *       действительно накапливается сравнением.</li>
 * </ul>
 * Это вероятный дефект, а не доказанный: если значения по смыслу не бывают отрицательными, 0 — нормальный старт.
 */
final class ZeroStartExtremumRule {

    static final String RULE = "R12";

    List<Finding> find(DiagnosticContext context) {
        List<Finding> found = new ArrayList<>();
        for (VariableDeclarator variable : context.unit.findAll(VariableDeclarator.class)) {
            Optional<Expression> init = variable.getInitializer().map(DiagnosticContext::unwrapped);
            if (init.isEmpty() || !zero(init.get()) || context.methodId(variable).isEmpty()) continue;
            String type = variable.getTypeAsString();
            if (!List.of("int", "long", "double", "float", "short").contains(type)) continue;
            String name = variable.getNameAsString();
            String method = variable.findAncestor(MethodDeclaration.class).map(MethodDeclaration::getNameAsString).orElse("");
            int direction = ExtremumRule.intent(name);
            if (direction == 0 && ExtremumRule.neutral(name)) direction = ExtremumRule.intent(method);
            if (direction == 0 || countLike(name) || countLike(method)) continue;
            if (!accumulates(variable, name, direction)) continue;
            String id = RULE + "-" + (found.size() + 1);
            String start = direction > 0 ? minValue(type) : maxValue(type);
            found.add(context.finding(RULE, variable, direction > 0 ? "Максимум начинается с нуля" : "Минимум начинается с нуля",
                    "«" + name + "» ищет " + (direction > 0 ? "максимум" : "минимум") + ", но начинает с 0. "
                            + (direction > 0 ? "Если все значения отрицательные, результат останется 0 — такого значения в данных нет."
                                             : "Если все значения положительные, результат останется 0 — такого значения в данных нет.")
                            + " Обычно стартуют с первого элемента или с " + start + ".",
                    List.of(direction > 0 ? "Значения могут быть отрицательными" : "Значения могут быть больше нуля",
                            "Для пустых данных исправление меняет результат: вместо 0 будет " + start),
                    context.bugfix(id, init.get(), start, "Начать с " + start,
                            "Исправление дефекта: меняет результат, когда все значения " + (direction > 0 ? "отрицательные" : "положительные")
                                    + ", и для пустых данных."),
                    Finding.DEFECT, found.size() + 1));
        }
        return found;
    }

    /** Счётчики, частоты, длины, количества не бывают отрицательными — для них старт с 0 правильный. */
    private static boolean countLike(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).matches(".*(count|freq|len|size|qty|quantity|amount|num|times|votes|hits).*");
    }

    private static boolean zero(Expression e) {
        return e instanceof IntegerLiteralExpr i && i.getValue().equals("0")
                || e instanceof LongLiteralExpr l && l.getValue().matches("0[lL]")
                || e instanceof DoubleLiteralExpr d && d.getValue().matches("0(\\.0*)?[dDfF]?");
    }

    /** В цикле того же метода: {@code if (x OP v) v = x;}, где OP сохраняет большее (max) или меньшее (min). */
    private static boolean accumulates(VariableDeclarator variable, String name, int direction) {
        var method = variable.findAncestor(com.github.javaparser.ast.body.CallableDeclaration.class);
        if (method.isEmpty()) return false;
        for (IfStmt check : method.get().findAll(IfStmt.class)) {
            if (DiagnosticContext.enclosingLoop(check).isEmpty() || check.getElseStmt().isPresent()) continue;
            if (!(DiagnosticContext.unwrapped(check.getCondition()) instanceof BinaryExpr compare)) continue;
            Statement then = check.getThenStmt();
            if (then.isBlockStmt() && then.asBlockStmt().getStatements().size() == 1) then = then.asBlockStmt().getStatement(0);
            if (!then.isExpressionStmt() || !(then.asExpressionStmt().getExpression() instanceof AssignExpr assign)
                    || assign.getOperator() != AssignExpr.Operator.ASSIGN || !assign.getTarget().toString().equals(name)) continue;
            String candidate = assign.getValue().toString();
            boolean leftCandidate = compare.getLeft().toString().equals(candidate) && compare.getRight().toString().equals(name);
            boolean rightCandidate = compare.getRight().toString().equals(candidate) && compare.getLeft().toString().equals(name);
            if (!leftCandidate && !rightCandidate || !dataElement(assign.getValue(), check)) continue;
            BinaryExpr.Operator op = compare.getOperator();
            boolean keepsLarger = leftCandidate ? (op == BinaryExpr.Operator.GREATER || op == BinaryExpr.Operator.GREATER_EQUALS)
                                                : (op == BinaryExpr.Operator.LESS || op == BinaryExpr.Operator.LESS_EQUALS);
            boolean keepsSmaller = leftCandidate ? (op == BinaryExpr.Operator.LESS || op == BinaryExpr.Operator.LESS_EQUALS)
                                                 : (op == BinaryExpr.Operator.GREATER || op == BinaryExpr.Operator.GREATER_EQUALS);
            if (direction > 0 ? keepsLarger : keepsSmaller) return true;
        }
        return false;
    }

    /**
     * Кандидат — сам элемент данных: переменная for-each или {@code a[i]}. Длины, счётчики и размеры не бывают
     * отрицательными, и для них старт с 0 правильный — их не трогаем.
     */
    private static boolean dataElement(Expression candidate, IfStmt check) {
        if (candidate instanceof ArrayAccessExpr) return true;
        if (!(candidate instanceof NameExpr name)) return false;
        return check.findAncestor(com.github.javaparser.ast.stmt.ForEachStmt.class)
                .filter(loop -> loop.getVariableDeclarator().getNameAsString().equals(name.getNameAsString()))
                .isPresent();
    }

    private static String minValue(String type) {
        return switch (type) {
            case "long" -> "Long.MIN_VALUE";
            case "double" -> "Double.NEGATIVE_INFINITY";
            case "float" -> "Float.NEGATIVE_INFINITY";
            case "short" -> "Short.MIN_VALUE";
            default -> "Integer.MIN_VALUE";
        };
    }

    private static String maxValue(String type) {
        return switch (type) {
            case "long" -> "Long.MAX_VALUE";
            case "double" -> "Double.POSITIVE_INFINITY";
            case "float" -> "Float.POSITIVE_INFINITY";
            case "short" -> "Short.MAX_VALUE";
            default -> "Integer.MAX_VALUE";
        };
    }
}
