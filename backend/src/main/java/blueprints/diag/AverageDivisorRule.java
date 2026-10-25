package blueprints.diag;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** R5: подозрительный делитель среднего. Для выборочной статистики length - 1 может быть верным. */
final class AverageDivisorRule {
    List<Finding> find(DiagnosticContext context) {
        List<Finding> found = new ArrayList<>();
        for (BinaryExpr division : context.unit.findAll(BinaryExpr.class)) {
            if (division.getOperator() != BinaryExpr.Operator.DIVIDE || context.methodId(division).isEmpty()) continue;
            var method = division.findAncestor(MethodDeclaration.class);
            if (method.isEmpty() || !averageName(method.get().getNameAsString())) continue;
            Expression divisor = DiagnosticContext.unwrapped(division.getRight());
            if (!(divisor instanceof BinaryExpr minus) || minus.getOperator() != BinaryExpr.Operator.MINUS
                    || !minus.getRight().isIntegerLiteralExpr() || !minus.getRight().asIntegerLiteralExpr().getValue().equals("1")
                    || context.arrayLength(minus.getLeft()).isEmpty() || intervalOrSpread(division.getLeft(), method.get())) continue;
            String id = "R5-" + (found.size() + 1);
            found.add(context.finding("R5", minus, "Проверьте делитель среднего",
                    "Метод с именем среднего делит на length - 1. Для среднего всех элементов обычно нужен length; "
                            + "для выборочной оценки или исключённого элемента такой делитель может быть намеренным. При length == 1 делитель равен нулю.",
                    List.of("Вычисляется арифметическое среднее всех элементов", "Массив непустой; для пустого массива нужна отдельная политика"),
                    context.bugfix(id, minus, minus.getLeft().toString(), "Делить на число элементов",
                            "Только для среднего всех элементов; меняет поведение, а обработку пустого массива не добавляет."),
                    Finding.DEFECT, found.size() + 1));
        }
        return found;
    }

    /**
     * Числитель — интервал или разброс, а не сумма: разность ({@code span = last - first}) или имя вроде span, gap,
     * delta, diff, sq, var, dev. Средний интервал между n отметками и выборочная дисперсия делят на n - 1 правильно.
     */
    private static boolean intervalOrSpread(Expression numerator, MethodDeclaration method) {
        Expression value = DiagnosticContext.unwrapped(numerator);
        if (value instanceof CastExpr cast) value = DiagnosticContext.unwrapped(cast.getExpression());
        if (value instanceof BinaryExpr difference && difference.getOperator() == BinaryExpr.Operator.MINUS) return true;
        if (!(value instanceof NameExpr name)) return false;
        String n = name.getNameAsString();
        if (n.toLowerCase(Locale.ROOT).matches(".*(span|gap|delta|diff|interval|range|sq|var|dev).*")) return true;
        return method.findAll(com.github.javaparser.ast.body.VariableDeclarator.class).stream()
                .filter(v -> v.getNameAsString().equals(n))
                .anyMatch(v -> v.getInitializer().map(DiagnosticContext::unwrapped)
                        .filter(init -> init instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.MINUS).isPresent());
    }

    private boolean averageName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.matches("(?:calculate|compute|get|find)?(?:average|mean|avg)");
    }
}
