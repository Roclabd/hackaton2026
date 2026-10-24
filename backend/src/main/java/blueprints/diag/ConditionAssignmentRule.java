package blueprints.diag;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;

import java.util.ArrayList;
import java.util.List;

/** R6: присваивание занимает всё условие. Идиому (line = read()) != null не обвиняем. */
final class ConditionAssignmentRule {
    List<Finding> find(DiagnosticContext context) {
        List<Finding> found = new ArrayList<>();
        for (Node node : context.unit.findAll(Node.class)) {
            Expression condition = switch (node) {
                case IfStmt check -> check.getCondition();
                case WhileStmt loop -> loop.getCondition();
                case DoStmt loop -> loop.getCondition();
                case ForStmt loop -> loop.getCompare().orElse(null);
                case ConditionalExpr ternary -> ternary.getCondition();
                default -> null;
            };
            if (condition == null || context.methodId(node).isEmpty()) continue;
            if (!(DiagnosticContext.unwrapped(condition) instanceof AssignExpr assign)
                    || assign.getOperator() != AssignExpr.Operator.ASSIGN) continue;
            String type = context.type(assign.getTarget());
            boolean numeric = context.integral(assign.getTarget()) || type.equals("float") || type.equals("double");
            String id = "R6-" + (found.size() + 1);
            found.add(context.finding("R6", assign, "Присваивание в условии",
                    numeric ? "Оператор = записывает значение вместо сравнения. Числовое присваивание не даёт boolean, поэтому такое условие не компилируется."
                            : "Оператор = меняет переменную. Для boolean это допустимо и может быть намеренным; проверьте, не требовалось сравнение ==.",
                    List.of("В условии требуется сравнение, а не запись значения"),
                    numeric && (context.integral(assign.getValue()) || List.of("float", "double").contains(context.type(assign.getValue()))) ? context.bugfix(id, assign, assign.getTarget() + " == (" + assign.getValue() + ")", "Сравнить вместо присваивания",
                            "Заменяет запись сравнением; предполагаемое назначение нужно подтвердить.") : List.of(),
                    Finding.DEFECT, found.size() + 1));
        }
        return found;
    }
}

