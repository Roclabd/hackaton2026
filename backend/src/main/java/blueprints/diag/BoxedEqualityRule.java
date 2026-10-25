package blueprints.diag;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * R14: {@code ==} / {@code !=} между двумя обёртками Integer или Long. Сравниваются ссылки, а не числа:
 * Java кэширует только значения от −128 до 127, поэтому на маленьких числах сравнение «работает»,
 * а на больших молча даёт false. Срабатываем, только если тип ОБЕИХ сторон виден точно: переменная
 * или поле, объявленные как Integer / Long, или {@code get} у стандартного List / Map с таким типом элементов.
 * Сравнение с null и смешанное сравнение с int (там распаковка) не трогаем.
 */
final class BoxedEqualityRule {

    static final String RULE = "R14";
    private static final Set<String> BOXED = Set.of("Integer", "Long", "Short", "Byte");
    private static final Set<String> LISTS = Set.of("List", "ArrayList", "LinkedList");
    private static final Set<String> MAPS = Set.of("Map", "HashMap", "LinkedHashMap", "TreeMap");

    List<Finding> find(DiagnosticContext context) {
        if (BOXED.stream().anyMatch(name -> context.unit.findAll(com.github.javaparser.ast.body.TypeDeclaration.class).stream()
                .anyMatch(t -> t.getNameAsString().equals(name)))) {
            return List.of(); // свой класс Integer / Long — обёртки java.lang здесь не угадываем
        }
        List<Finding> found = new ArrayList<>();
        for (BinaryExpr compare : context.unit.findAll(BinaryExpr.class)) {
            boolean equals = compare.getOperator() == BinaryExpr.Operator.EQUALS;
            if (!equals && compare.getOperator() != BinaryExpr.Operator.NOT_EQUALS || context.methodId(compare).isEmpty()) continue;
            Optional<String> left = boxed(compare.getLeft(), context), right = boxed(compare.getRight(), context);
            if (left.isEmpty() || right.isEmpty() || !left.get().equals(right.get())) continue;
            String id = RULE + "-" + (found.size() + 1);
            String replacement = (equals ? "" : "!") + "java.util.Objects.equals(" + compare.getLeft() + ", " + compare.getRight() + ")";
            found.add(context.finding(RULE, compare, "Сравнение " + left.get() + " через " + compare.getOperator().asString(),
                    "Обе стороны — " + left.get() + ": «" + compare.getOperator().asString() + "» сравнивает ссылки, а не числа. "
                            + "Java переиспользует объекты только для значений от −128 до 127, поэтому на маленьких числах "
                            + "результат верный, а на больших — нет.",
                    List.of("Нужно сравнить значения, а не объекты",
                            "Числа могут выходить за −128…127"),
                    context.bugfix(id, compare, replacement, "Сравнить значения через Objects.equals",
                            "Исправление дефекта: результат меняется для чисел вне −128…127; для null поведение то же."),
                    Finding.DEFECT, found.size() + 1));
        }
        return found;
    }

    /** Точный тип-обёртка выражения; пусто — не обёртка или тип не виден. */
    private static Optional<String> boxed(Expression expression, DiagnosticContext context) {
        Expression e = DiagnosticContext.unwrapped(expression);
        if (e instanceof MethodCallExpr call && call.getNameAsString().equals("get") && call.getArguments().size() == 1
                && call.getScope().map(DiagnosticContext::unwrapped).orElse(null) instanceof NameExpr owner) {
            Optional<ClassOrInterfaceType> list = context.standardType(owner, LISTS);
            if (list.isPresent()) return argument(list.get(), 0, 1);
            Optional<ClassOrInterfaceType> map = context.standardType(owner, MAPS);
            if (map.isPresent()) return argument(map.get(), 1, 2);
            return Optional.empty();
        }
        String type = context.type(e);
        return BOXED.contains(type) ? Optional.of(type) : Optional.empty();
    }

    private static Optional<String> argument(ClassOrInterfaceType type, int index, int count) {
        return type.getTypeArguments().filter(args -> args.size() == count).map(args -> args.get(index).asString())
                .filter(BOXED::contains);
    }
}
