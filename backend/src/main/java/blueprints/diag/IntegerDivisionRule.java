package blueprints.diag;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ReturnStmt;

import java.util.ArrayList;
import java.util.List;

/** R8: целочисленный результат расширяется до double уже после потери дробной части. */
final class IntegerDivisionRule {
    List<Finding> find(DiagnosticContext context) {
        List<Finding> found = new ArrayList<>();
        for (BinaryExpr division : context.unit.findAll(BinaryExpr.class)) {
            if (division.getOperator() != BinaryExpr.Operator.DIVIDE || context.methodId(division).isEmpty()
                    || !context.integral(division.getLeft()) || !context.integral(division.getRight())
                    || !doubleDestination(context, division)) continue;
            String id = "R8-" + (found.size() + 1);
            String replacement = "((double) (" + division.getLeft() + ") / (" + division.getRight() + "))";
            found.add(context.finding("R8", division, "Возможная потеря дробной части при делении",
                    "Оба операнда имеют целочисленный тип. Деление отбрасывает дробную часть, затем результат становится double. "
                            + "Это дефект только если нужен дробный результат; усечение может быть намеренным.",
                    List.of("Нужен дробный результат деления", "Делитель ненулевой: переход к double меняет и поведение при делении на ноль"),
                    context.bugfix(id, division, replacement, "Выполнить деление в double",
                            "Преобразует первый операнд перед делением; меняет численный результат и обработку нулевого делителя."),
                    Finding.DEFECT, found.size() + 1));
        }
        return found;
    }

    private boolean doubleDestination(DiagnosticContext context, BinaryExpr division) {
        Node value = division;
        while (true) {
            Node parent = value.getParentNode().orElse(null);
            if (parent instanceof EnclosedExpr) { value = parent; continue; }
            if (parent instanceof CastExpr cast && cast.getTypeAsString().equals("double")) { value = parent; continue; }
            // Ветка тернарного оператора уходит туда же, куда весь оператор; условие — нет.
            if (parent instanceof ConditionalExpr ternary && ternary.getCondition() != value) { value = parent; continue; }
            if (parent instanceof VariableDeclarator variable) return variable.getTypeAsString().equals("double");
            if (parent instanceof AssignExpr assign && assign.getOperator() == AssignExpr.Operator.ASSIGN && assign.getValue() == value) {
                return context.type(assign.getTarget()).equals("double");
            }
            if (parent instanceof ReturnStmt result && result.getExpression().orElse(null) == value) {
                for (Node boundary = result.getParentNode().orElse(null); boundary != null; boundary = boundary.getParentNode().orElse(null)) {
                    if (boundary instanceof LambdaExpr) return false;
                    if (boundary instanceof MethodDeclaration method) return method.getTypeAsString().equals("double");
                    if (boundary instanceof com.github.javaparser.ast.body.TypeDeclaration<?>) return false;
                }
                return false;
            }
            return false;
        }
    }
}


