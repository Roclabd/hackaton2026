package blueprints.diag;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.Optional;

/**
 * Цикл с одной переменной: {@code for (int i = …; i < граница; …)} (счётчик) или {@code for (x : коллекция)}.
 * Общие проверки для правил R15–R17: растёт ли число шагов вместе с данными и какие выражения — «элемент,
 * выбранный этой переменной» ({@code a[i]}, {@code list.get(i)}, {@code s.charAt(i)} или сам x у for-each).
 */
record IndexLoop(Statement loop, String variable, boolean counting) {

    static Optional<IndexLoop> of(Statement statement) {
        if (statement instanceof ForEachStmt each) {
            return Optional.of(new IndexLoop(each, each.getVariableDeclarator().getNameAsString(), false));
        }
        if (statement instanceof ForStmt loop && loop.getInitialization().size() == 1
                && loop.getInitialization().get(0) instanceof VariableDeclarationExpr declaration
                && declaration.getVariables().size() == 1) {
            return Optional.of(new IndexLoop(loop, declaration.getVariable(0).getNameAsString(), true));
        }
        return Optional.empty();
    }

    Statement body() {
        return loop instanceof ForStmt f ? f.getBody() : ((ForEachStmt) loop).getBody();
    }

    /**
     * Число шагов зависит от данных: граница — переменная, длина массива, size() и т. п., а не литерал
     * или КОНСТАНТА. Цикл до 26 букв или по значениям enum — это не рост.
     */
    boolean growing() {
        if (loop instanceof ForEachStmt each) {
            Expression iterable = DiagnosticContext.unwrapped(each.getIterable());
            if (iterable instanceof ArrayCreationExpr || constantName(iterable)) return false;
            return !(iterable instanceof MethodCallExpr call && (call.getNameAsString().equals("values") && call.getArguments().isEmpty()
                    || call.getNameAsString().matches("of|asList") && call.getArguments().stream().allMatch(Expression::isLiteralExpr)));
        }
        ForStmt counter = (ForStmt) loop;
        if (!(counter.getCompare().map(DiagnosticContext::unwrapped).orElse(null) instanceof BinaryExpr compare)
                || !(DiagnosticContext.unwrapped(compare.getLeft()) instanceof NameExpr left)
                || !left.getNameAsString().equals(variable)) return false;
        Expression limit = switch (compare.getOperator()) {
            case LESS, LESS_EQUALS, NOT_EQUALS -> compare.getRight();
            // Обратный ход i = n - 1; i >= 0: от данных зависит начало, а не граница.
            case GREATER, GREATER_EQUALS -> ((VariableDeclarationExpr) counter.getInitialization().get(0))
                    .getVariable(0).getInitializer().orElse(null);
            default -> null;
        };
        return limit != null && dependsOnData(limit);
    }

    /** Элемент, выбранный переменной цикла; {@code other} — переменная второго цикла: a[other][i] — это обход матрицы. */
    boolean selects(Expression expression, String other) {
        Expression e = DiagnosticContext.unwrapped(expression);
        if (!counting) return e instanceof NameExpr n && n.getNameAsString().equals(variable);
        if (e instanceof ArrayAccessExpr access) {
            return isVariable(access.getIndex()) && !mentions(access.getName(), other) && !mentions(access.getName(), variable);
        }
        return e instanceof MethodCallExpr call && call.getNameAsString().matches("get|charAt")
                && call.getArguments().size() == 1 && isVariable(call.getArgument(0))
                && call.getScope().filter(s -> !mentions(s, other) && !mentions(s, variable)).isPresent();
    }

    boolean isVariable(Expression expression) {
        return DiagnosticContext.unwrapped(expression) instanceof NameExpr n && n.getNameAsString().equals(variable);
    }

    static boolean mentions(Expression expression, String name) {
        return expression.findAll(NameExpr.class).stream().anyMatch(n -> n.getNameAsString().equals(name));
    }

    private static boolean dependsOnData(Expression limit) {
        return limit.findAll(NameExpr.class).stream().anyMatch(n -> !constantName(n))
                || !limit.findAll(MethodCallExpr.class).isEmpty();
    }

    private static boolean constantName(Expression e) {
        return e instanceof NameExpr n && n.getNameAsString().matches("[A-Z][A-Z0-9_]*");
    }
}
