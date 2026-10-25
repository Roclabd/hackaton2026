package blueprints.diag;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * R13: значение {@code map.get(k)} становится примитивом — {@code int c = m.get(k)}, {@code m.get(k) + 1},
 * {@code return m.get(k)} из метода, возвращающего double, или {@code Double v = m.get(k); return v;}.
 * Если ключа нет, get возвращает null, и распаковка бросает NullPointerException.
 *
 * Молчим, если в методе уже есть защита: {@code containsKey} у той же карты, сравнение с null,
 * или карта заранее заполнена ({@code put} у той же карты выше по методу — как «все жанры по нулям»).
 */
final class MapUnboxingRule {

    static final String RULE = "R13";
    private static final Set<String> MAPS = Set.of("Map", "HashMap", "LinkedHashMap", "TreeMap");
    private static final Map<String, String> DEFAULTS = Map.of("Integer", "0", "Long", "0L", "Double", "0.0", "Float", "0f");
    private static final Set<String> PRIMITIVES = Set.of("int", "long", "double", "float");

    List<Finding> find(DiagnosticContext context) {
        List<Finding> found = new ArrayList<>();
        for (MethodCallExpr get : context.unit.findAll(MethodCallExpr.class)) {
            if (!get.getNameAsString().equals("get") || get.getArguments().size() != 1
                    || !(get.getScope().map(DiagnosticContext::unwrapped).orElse(null) instanceof NameExpr map)
                    || context.methodId(get).isEmpty()) continue;
            Optional<String> value = valueType(context, map);
            if (value.isEmpty() || guarded(get, map)) continue;
            Optional<String> use = unboxingUse(get, context);
            if (use.isEmpty()) continue;
            String id = RULE + "-" + (found.size() + 1);
            String replacement = map + ".getOrDefault(" + get.getArgument(0) + ", " + DEFAULTS.get(value.get()) + ")";
            found.add(context.finding(RULE, get, "Распаковка null из Map.get",
                    "«" + get + "» вернёт null, если ключа нет, а значение тут же " + use.get()
                            + " — это NullPointerException. Ни containsKey, ни проверки на null в методе нет.",
                    List.of("Ключ может отсутствовать в «" + map + "»",
                            "Нужно решить, что значит отсутствие ключа: " + DEFAULTS.get(value.get()) + " или понятная ошибка"),
                    context.bugfix(id, get, replacement, "Значение по умолчанию " + DEFAULTS.get(value.get()),
                            "Исправление дефекта: вместо NullPointerException отсутствующий ключ даёт "
                                    + DEFAULTS.get(value.get()) + ". Меняет поведение на таких входах."),
                    Finding.DEFECT, found.size() + 1));
        }
        return found;
    }

    /** Тип значений стандартной карты: Integer, Long, Double, Float — то, что распаковывается в число. */
    private static Optional<String> valueType(DiagnosticContext context, NameExpr map) {
        return context.standardType(map, MAPS).flatMap(ClassOrInterfaceType::getTypeArguments)
                .filter(args -> args.size() == 2).map(args -> args.get(1).asString()).filter(DEFAULTS::containsKey);
    }

    /** Как значение распаковывается; пусто — не распаковывается или распаковку мы не видим. */
    private static Optional<String> unboxingUse(MethodCallExpr get, DiagnosticContext context) {
        Node value = get;
        while (value.getParentNode().orElse(null) instanceof EnclosedExpr parent) value = parent;
        Node parent = value.getParentNode().orElse(null);
        if (parent instanceof VariableDeclarator variable) {
            if (PRIMITIVES.contains(variable.getTypeAsString())) return Optional.of("записывается в " + variable.getTypeAsString());
            if (DEFAULTS.containsKey(variable.getTypeAsString())) return wrapperLaterUnboxed(variable);
            return Optional.empty();
        }
        if (parent instanceof AssignExpr assign && assign.getValue() == value && assign.getOperator() == AssignExpr.Operator.ASSIGN
                && PRIMITIVES.contains(context.type(assign.getTarget()))) return Optional.of("записывается в " + context.type(assign.getTarget()));
        if (parent instanceof BinaryExpr binary && arithmetic(binary.getOperator()) && !concatenation(binary, context)) return Optional.of("участвует в арифметике");
        if (parent instanceof ReturnStmt && returnsPrimitive(get)) return Optional.of("возвращается как примитив");
        return Optional.empty();
    }

    /** {@code Double v = m.get(k);} — дальше v возвращается как примитив или участвует в арифметике, без проверки на null. */
    private static Optional<String> wrapperLaterUnboxed(VariableDeclarator variable) {
        var method = variable.findAncestor(CallableDeclaration.class);
        if (method.isEmpty()) return Optional.empty();
        String name = variable.getNameAsString();
        boolean nullChecked = method.get().findAll(BinaryExpr.class).stream()
                .anyMatch(b -> (b.getOperator() == BinaryExpr.Operator.EQUALS || b.getOperator() == BinaryExpr.Operator.NOT_EQUALS)
                        && (b.getLeft().toString().equals(name) || b.getRight().toString().equals(name)));
        if (nullChecked) return Optional.empty();
        for (NameExpr use : method.get().findAll(NameExpr.class)) {
            if (!use.getNameAsString().equals(name) || use.getBegin().isEmpty() || variable.getBegin().isEmpty()
                    || !use.getBegin().get().isAfter(variable.getBegin().get())) continue;
            Node parent = use.getParentNode().orElse(null);
            if (parent instanceof ReturnStmt && returnsPrimitive(use)) return Optional.of("возвращается через «" + name + "» как примитив");
            if (parent instanceof BinaryExpr binary && arithmetic(binary.getOperator()) && !concatenation(binary, null)) return Optional.of("через «" + name + "» участвует в арифметике");
        }
        return Optional.empty();
    }

    /** «+» в цепочке со строкой — склейка: значение печатается как «null», распаковки нет. */
    private static boolean concatenation(BinaryExpr binary, DiagnosticContext context) {
        if (binary.getOperator() != BinaryExpr.Operator.PLUS) return false;
        BinaryExpr top = binary;
        while (top.getParentNode().orElse(null) instanceof BinaryExpr up && up.getOperator() == BinaryExpr.Operator.PLUS) top = up;
        return top.findAll(Expression.class).stream().anyMatch(e -> e instanceof StringLiteralExpr || e instanceof TextBlockLiteralExpr
                || e instanceof NameExpr n && context != null && context.string(n));
    }

    private static boolean returnsPrimitive(Node node) {
        for (Node boundary = node.getParentNode().orElse(null); boundary != null; boundary = boundary.getParentNode().orElse(null)) {
            if (boundary instanceof LambdaExpr) return false;
            if (boundary instanceof MethodDeclaration method) return PRIMITIVES.contains(method.getTypeAsString());
        }
        return false;
    }

    private static boolean arithmetic(BinaryExpr.Operator op) {
        return switch (op) {
            case PLUS, MINUS, MULTIPLY, DIVIDE, REMAINDER, LESS, GREATER, LESS_EQUALS, GREATER_EQUALS -> true;
            default -> false;
        };
    }

    /**
     * Защита в том же методе: {@code map.containsKey(…)}, {@code map.get(…) == / != null}, или карта заполнена
     * заранее — {@code map.put(…)} / {@code putIfAbsent} / {@code computeIfAbsent} выше по тексту метода.
     */
    private static boolean guarded(MethodCallExpr get, NameExpr map) {
        var method = get.findAncestor(CallableDeclaration.class);
        if (method.isEmpty() || get.getBegin().isEmpty()) return true;
        String owner = map.getNameAsString();
        for (MethodCallExpr call : method.get().findAll(MethodCallExpr.class)) {
            if (!(call.getScope().map(DiagnosticContext::unwrapped).orElse(null) instanceof NameExpr scope)
                    || !scope.getNameAsString().equals(owner)) continue;
            String name = call.getNameAsString();
            if (name.equals("containsKey")) return true;
            // Внешний вызов вокруг самого get (counts.put(w, counts.get(w) + 1)) защитой не считается.
            boolean earlier = !call.isAncestorOf(get) && call.getBegin().isPresent() && call.getBegin().get().isBefore(get.getBegin().get());
            if (earlier && (name.equals("put") || name.equals("putIfAbsent") || name.equals("computeIfAbsent") || name.equals("putAll"))) return true;
            if (name.equals("get") && call.getParentNode().orElse(null) instanceof BinaryExpr b
                    && (b.getOperator() == BinaryExpr.Operator.EQUALS || b.getOperator() == BinaryExpr.Operator.NOT_EQUALS)) return true;
        }
        return false;
    }
}
