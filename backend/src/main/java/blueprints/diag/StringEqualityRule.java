package blueprints.diag;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;

import java.util.ArrayList;
import java.util.List;

/** R7: только доказанные строковые операнды, с явным условием сравнения содержимого. */
final class StringEqualityRule {
    List<Finding> find(DiagnosticContext context) {
        List<Finding> found = new ArrayList<>();
        for (BinaryExpr compare : context.unit.findAll(BinaryExpr.class)) {
            boolean equal = compare.getOperator() == BinaryExpr.Operator.EQUALS;
            if ((!equal && compare.getOperator() != BinaryExpr.Operator.NOT_EQUALS) || context.methodId(compare).isEmpty()
                    || !context.string(compare.getLeft()) || !context.string(compare.getRight())) continue;
            if (compare.getLeft().equals(compare.getRight())
                    || (compare.getLeft().isStringLiteralExpr() && compare.getRight().isStringLiteralExpr())) continue;
            if (identityShortcut(compare)) continue;
            String id = "R7-" + (found.size() + 1);
            String replacement = (equal ? "" : "!") + "java.util.Objects.equals(" + compare.getLeft() + ", " + compare.getRight() + ")";
            // Полностью квалифицированное имя пакета тоже может быть затенено переменной java.
            boolean javaShadowed = context.unit.findAll(VariableDeclarator.class).stream().anyMatch(v -> v.getNameAsString().equals("java"))
                    || context.unit.findAll(Parameter.class).stream().anyMatch(p -> p.getNameAsString().equals("java"));
            found.add(context.finding("R7", compare, "Возможно, строки нужно сравнить по содержимому",
                    "Оператор " + compare.getOperator().asString() + " сравнивает ссылки String. Разные объекты с одинаковым текстом "
                            + "считаются разными; сравнение идентичности объектов при этом может быть намеренным.",
                    List.of("Нужно сравнить текст, а не идентичность объектов", "Предложенная проверка считает два null равными"),
                    javaShadowed ? List.of() : context.bugfix(id, compare, replacement, "Сравнить содержимое строк",
                            "Objects.equals допускает null и меняет семантику сравнения ссылок; это исправление поведения."),
                    Finding.DEFECT, found.size() + 1));
        }
        return found;
    }

    /**
     * {@code if (s == t) return true;} перед {@code s.equals(t)} — быстрая проверка того же объекта, а не ошибка:
     * в том же методе эти же две строки дальше сравниваются по содержимому.
     */
    private static boolean identityShortcut(BinaryExpr compare) {
        String a = compare.getLeft().toString(), b = compare.getRight().toString();
        return compare.findAncestor(com.github.javaparser.ast.body.CallableDeclaration.class).map(method -> method
                .findAll(com.github.javaparser.ast.expr.MethodCallExpr.class).stream()
                .filter(call -> call.getNameAsString().matches("equals|equalsIgnoreCase|compareTo|compareToIgnoreCase|contentEquals")
                        && call.getScope().isPresent() && call.getArguments().size() == 1)
                .anyMatch(call -> {
                    String scope = call.getScope().get().toString(), arg = call.getArgument(0).toString();
                    return scope.equals(a) && arg.equals(b) || scope.equals(b) && arg.equals(a);
                })).orElse(false);
    }
}
