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
                    || context.arrayLength(minus.getLeft()).isEmpty()) continue;
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

    private boolean averageName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.matches("(?:calculate|compute|get|find)?(?:average|mean|avg)");
    }
}
