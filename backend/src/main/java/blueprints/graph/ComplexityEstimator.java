package blueprints.graph;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;

import java.util.*;

/**
 * Оценка сложности по структуре кода, без запуска. Правила простые и объяснимые:
 * <ul>
 *   <li>цикл по данным умножает стоимость тела на n; цикл с постоянной границей ({@code i < 10}, {@code X.values()})
 *       — на константу; цикл с умножением или делением шага ({@code i *= 2}, бинарный поиск) — на log n;</li>
 *   <li>сортировка — n log n; {@code contains / indexOf / remove} у списка — n; поток ({@code stream()}) и
 *       {@code forEach} — проход по коллекции;</li>
 *   <li>вызов метода из этого же файла стоит столько же, сколько сам метод; рекурсию по структуре не оценить —
 *       так и пишем.</li>
 * </ul>
 * Из последовательных частей берётся самая дорогая. Это оценка сверху по виду кода, а не замер:
 * замер роста делает пакет profile.
 */
public final class ComplexityEstimator {

    /**
     * Стоимость: n^power · (log n)^logs; why — что её дало.
     *
     * @param recursion имя рекурсивного метода, до которого дошёл подсчёт (его вклад не оценить), или null
     */
    private record Cost(int power, int logs, String recursion, String why) {
        static final Cost CONSTANT = new Cost(0, 0, null, null);

        Cost max(Cost other) {
            Cost bigger = power != other.power ? (power > other.power ? this : other)
                    : logs >= other.logs ? this : other;
            return new Cost(bigger.power, bigger.logs, recursion != null ? recursion : other.recursion, bigger.why);
        }

        /** Тело, выполненное {@code this} раз. */
        Cost times(Cost body) {
            String reason = body.isConstant() ? why : why + " → " + body.why;
            return new Cost(power + body.power, logs + body.logs, recursion != null ? recursion : body.recursion, reason);
        }

        boolean isConstant() {
            return power == 0 && logs == 0;
        }
    }

    private static final Set<String> STREAM_STAGES = Set.of("map", "filter", "forEach", "anyMatch", "allMatch",
            "noneMatch", "reduce", "collect", "flatMap", "mapToInt", "mapToLong", "mapToDouble", "mapToObj", "count",
            "sum", "max", "min", "average", "distinct", "toList", "findFirst", "findAny", "peek", "takeWhile", "dropWhile");
    private static final Set<String> LIST_LINEAR = Set.of("contains", "indexOf", "lastIndexOf", "remove");
    private static final Set<String> LIST_TYPES = Set.of("List", "ArrayList", "LinkedList", "Collection", "Vector");

    private final CompilationUnit unit;
    private final Map<CallableDeclaration<?>, Cost> methodCosts = new IdentityHashMap<>();
    /** Методы, чья оценка считается сейчас, — в порядке вызова: по нему виден цикл рекурсии. */
    private final Deque<CallableDeclaration<?>> stack = new ArrayDeque<>();
    /** Методы, которые сами входят в цикл вызовов (прямая или взаимная рекурсия). */
    private final Set<CallableDeclaration<?>> cyclic = Collections.newSetFromMap(new IdentityHashMap<>());

    public ComplexityEstimator(CompilationUnit unit) {
        this.unit = unit;
    }

    /** Оценка метода, конструктора или блока инициализации целиком. */
    public Complexity ofDeclaration(Node declaration) {
        if (declaration instanceof CallableDeclaration<?> callable) {
            Cost cost = methodCost(callable);
            return cyclic.contains(callable) ? recursive(callable) : present(cost);
        }
        return present(cost(declaration));
    }

    /** Оценка одного цикла (фрагмента) — для узла цикла на чертеже. */
    public Complexity ofLoop(Statement loop) {
        return present(cost(loop));
    }

    private Cost methodCost(CallableDeclaration<?> method) {
        Cost known = methodCosts.get(method);
        if (known != null) return known;
        if (stack.contains(method)) {
            // Цикл вызовов: в него входят все методы стека от method до вершины.
            for (Iterator<CallableDeclaration<?>> it = stack.descendingIterator(); it.hasNext(); ) {
                CallableDeclaration<?> onStack = it.next();
                cyclic.add(onStack);
                if (onStack == method) break;
            }
            return new Cost(0, 0, method.getNameAsString(), null);
        }
        stack.push(method);
        Cost result = cost(method);
        stack.pop();
        if (cyclic.contains(method)) {
            result = new Cost(result.power, result.logs, method.getNameAsString(), result.why);
        }
        methodCosts.put(method, result);
        return result;
    }

    private Cost cost(Node node) {
        return switch (node) {
            case ForStmt loop -> loopFactor(loop).times(maxOf(childrenExcept(loop, loop.getInitialization())));
            case ForEachStmt loop -> loopFactor(loop).times(cost(loop.getBody()));
            case WhileStmt loop -> loopFactor(loop).times(maxOf(List.of(loop.getCondition(), loop.getBody())));
            case DoStmt loop -> loopFactor(loop).times(maxOf(List.of(loop.getCondition(), loop.getBody())));
            case MethodCallExpr call -> callCost(call).max(maxOf(call.getChildNodes()));
            // Тела локальных и анонимных классов сами по себе не выполняются.
            case LocalClassDeclarationStmt local -> Cost.CONSTANT;
            case LocalRecordDeclarationStmt local -> Cost.CONSTANT;
            case ObjectCreationExpr creation when creation.getAnonymousClassBody().isPresent() -> maxOf(creation.getArguments());
            default -> maxOf(node.getChildNodes());
        };
    }

    private Cost maxOf(List<? extends Node> nodes) {
        Cost result = Cost.CONSTANT;
        for (Node child : nodes) {
            result = result.max(cost(child));
        }
        return result;
    }

    private static List<Node> childrenExcept(Node parent, List<? extends Node> skipped) {
        List<Node> result = new ArrayList<>(parent.getChildNodes());
        result.removeAll(skipped);
        return result;
    }

    // ——— множитель цикла ———

    private Cost loopFactor(Statement loop) {
        int line = loop.getBegin().map(p -> p.line).orElse(0);
        if (constantBound(loop)) {
            return new Cost(0, 0, null, "цикл с постоянным числом шагов (стр. " + line + ")");
        }
        if (halvingLoop(loop)) {
            return new Cost(0, 1, null, "цикл с делением/умножением шага (стр. " + line + ") — log n");
        }
        return new Cost(1, 0, null, "цикл (стр. " + line + ")");
    }

    /**
     * {@code for (int i = 0; i < 10; i++)}, перебор {@code X.values()}, {@code List.of(...)}, литерала массива
     * или аргументов varargs — число аргументов задаётся в месте вызова, а не размером входа.
     */
    private static boolean constantBound(Statement loop) {
        if (loop instanceof ForStmt f) {
            return f.getCompare().filter(c -> c instanceof BinaryExpr b
                    && (literal(b.getRight()) || literal(b.getLeft()))).isPresent()
                    && f.getInitialization().stream().allMatch(ComplexityEstimator::initializedWithLiteral);
        }
        if (loop instanceof ForEachStmt f) {
            Expression iterable = f.getIterable();
            if (iterable instanceof ArrayCreationExpr || iterable instanceof ArrayInitializerExpr) return true;
            if (iterable instanceof NameExpr name && isVarargsParameter(name)) return true;
            return iterable instanceof MethodCallExpr call && (call.getNameAsString().equals("values") && call.getArguments().isEmpty()
                    || call.getNameAsString().equals("of") && call.getArguments().stream().allMatch(ComplexityEstimator::literal));
        }
        return false;
    }

    private static boolean isVarargsParameter(NameExpr name) {
        return name.findAncestor(CallableDeclaration.class).stream()
                .flatMap(c -> ((CallableDeclaration<?>) c).getParameters().stream())
                .anyMatch(p -> p.isVarArgs() && p.getNameAsString().equals(name.getNameAsString()));
    }

    private static boolean initializedWithLiteral(Expression init) {
        return init instanceof VariableDeclarationExpr declaration && declaration.getVariables().stream()
                .allMatch(v -> v.getInitializer().map(ComplexityEstimator::literal).orElse(false));
    }

    private static boolean literal(Expression expression) {
        Expression e = expression instanceof UnaryExpr u ? u.getExpression() : expression;
        return e instanceof IntegerLiteralExpr || e instanceof LongLiteralExpr || e instanceof CharLiteralExpr
                || e instanceof StringLiteralExpr;
    }

    /** Шаг умножается или делится ({@code i *= 2}, {@code n /= 10}) либо бинарный поиск ({@code mid = (lo + hi) / 2}). */
    private static boolean halvingLoop(Statement loop) {
        List<Node> scanned = loop instanceof ForStmt f ? new ArrayList<>(f.getUpdate()) : new ArrayList<>();
        if (loop instanceof WhileStmt || loop instanceof DoStmt) scanned.add(loop);
        for (Node node : scanned) {
            for (AssignExpr assign : node.findAll(AssignExpr.class)) {
                if (Set.of(AssignExpr.Operator.MULTIPLY, AssignExpr.Operator.DIVIDE, AssignExpr.Operator.SIGNED_RIGHT_SHIFT,
                        AssignExpr.Operator.UNSIGNED_RIGHT_SHIFT, AssignExpr.Operator.LEFT_SHIFT).contains(assign.getOperator())
                        && literal(assign.getValue())) {
                    return true;
                }
                if (assign.getOperator() == AssignExpr.Operator.ASSIGN && halves(assign.getValue())) return true;
            }
            for (VariableDeclarator variable : node.findAll(VariableDeclarator.class)) {
                if (variable.getInitializer().filter(ComplexityEstimator::halves).isPresent()) return true;
            }
        }
        return false;
    }

    /** {@code (lo + hi) / 2}, {@code x / 10}, {@code (lo + hi) >>> 1}, {@code lo + (hi - lo) / 2}. */
    private static boolean halves(Expression value) {
        return value.findAll(BinaryExpr.class).stream().anyMatch(b -> literal(b.getRight())
                && (b.getOperator() == BinaryExpr.Operator.DIVIDE || b.getOperator() == BinaryExpr.Operator.UNSIGNED_RIGHT_SHIFT
                || b.getOperator() == BinaryExpr.Operator.SIGNED_RIGHT_SHIFT));
    }

    // ——— стоимость вызова ———

    private Cost callCost(MethodCallExpr call) {
        String name = call.getNameAsString();
        int line = call.getBegin().map(p -> p.line).orElse(0);
        if (isLibrarySort(call)) {
            return new Cost(1, 1, null, "сортировка (стр. " + line + ") — n log n");
        }
        if (isStreamStage(call) || isIterableForEach(call)) {
            Cost perElement = maxOf(call.getArguments().stream().filter(Expression::isLambdaExpr).toList());
            return new Cost(1, 0, null, "проход по коллекции " + name + "() (стр. " + line + ")").times(perElement);
        }
        if (LIST_LINEAR.contains(name) && call.getScope().filter(this::isListVariable).isPresent()) {
            return new Cost(1, 0, null, name + "() у списка — просмотр всех элементов (стр. " + line + ")");
        }
        return sameFileMethod(call).map(target -> {
            Cost callee = methodCost(target);
            String reason = callee.why == null ? null : "вызов " + name + "() (стр. " + line + "): " + callee.why;
            return new Cost(callee.power, callee.logs, callee.recursion, reason);
        }).orElse(Cost.CONSTANT);
    }

    /**
     * Сортировка библиотеки: {@code Collections.sort}, {@code Arrays.sort}, {@code список.sort(…)}, {@code sorted()} потока.
     * Свой метод {@code sort(…)} сюда не относится — его стоимость считается по его телу (пузырёк — это n²).
     */
    private boolean isLibrarySort(MethodCallExpr call) {
        Optional<Expression> scope = call.getScope();
        if (call.getNameAsString().equals("sorted")) return isStreamStage(call) || scope.isPresent();
        if (!call.getNameAsString().equals("sort") || scope.isEmpty()) return false;
        Expression s = scope.get();
        return s instanceof NameExpr n && (n.getNameAsString().equals("Collections") || n.getNameAsString().equals("Arrays")
                || isListVariable(n));
    }

    private static boolean isStreamStage(MethodCallExpr call) {
        if (!STREAM_STAGES.contains(call.getNameAsString())) return false;
        for (Expression scope = call.getScope().orElse(null); scope instanceof MethodCallExpr inner;
             scope = inner.getScope().orElse(null)) {
            if (inner.getNameAsString().equals("stream") || inner.getNameAsString().equals("chars")) return true;
        }
        return false;
    }

    /** {@code list.forEach(...)}, {@code removeIf}, {@code replaceAll} — проход без потока. Только у первой стадии. */
    private static boolean isIterableForEach(MethodCallExpr call) {
        return Set.of("forEach", "removeIf", "replaceAll").contains(call.getNameAsString())
                && call.getScope().filter(s -> s instanceof NameExpr || s instanceof FieldAccessExpr).isPresent();
    }

    private boolean isListVariable(Expression scope) {
        if (!(scope instanceof NameExpr name)) return false;
        return declaredTypeName(name).map(t -> LIST_TYPES.contains(t.replaceAll("<.*", ""))).orElse(false);
    }

    /** Тип имени: параметр или локальная переменная метода, иначе поле класса. Для оценки этого достаточно. */
    private static Optional<String> declaredTypeName(NameExpr name) {
        String id = name.getNameAsString();
        Optional<CallableDeclaration<?>> callable = name.findAncestor(CallableDeclaration.class).map(c -> (CallableDeclaration<?>) c);
        if (callable.isPresent()) {
            for (Parameter p : callable.get().getParameters()) {
                if (p.getNameAsString().equals(id)) return Optional.of(p.getTypeAsString());
            }
            for (VariableDeclarator v : callable.get().findAll(VariableDeclarator.class)) {
                if (v.getNameAsString().equals(id)) return Optional.of(v.getTypeAsString());
            }
        }
        return name.findAncestor(TypeDeclaration.class).flatMap(type -> ((TypeDeclaration<?>) type).getFields().stream()
                .flatMap(f -> f.getVariables().stream()).filter(v -> v.getNameAsString().equals(id))
                .map(VariableDeclarator::getTypeAsString).findFirst());
    }

    /**
     * Метод этого файла, который вызывается. Сопоставляем по имени и числу аргументов, но не угадываем:
     * <ul>
     *   <li>{@code obj.f()} — только если тип obj объявлен в этом файле, и метод ищется в этом классе
     *       ({@code tokens.get(i)} у списка не должен попасть в свой метод get);</li>
     *   <li>{@code f()} или {@code this.f()} — сначала свой класс, иначе единственный такой метод в файле.</li>
     * </ul>
     */
    private Optional<MethodDeclaration> sameFileMethod(MethodCallExpr call) {
        List<MethodDeclaration> candidates = unit.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals(call.getNameAsString())
                        && m.getParameters().size() == call.getArguments().size() && m.getBody().isPresent())
                .toList();
        Optional<Expression> scope = call.getScope();
        if (scope.isPresent() && !(scope.get() instanceof ThisExpr)) {
            if (!(scope.get() instanceof NameExpr variable)) return Optional.empty();
            Optional<String> type = declaredTypeName(variable).map(t -> t.replaceAll("<.*", ""));
            if (type.isEmpty()) return Optional.empty();
            List<MethodDeclaration> inType = candidates.stream()
                    .filter(m -> m.findAncestor(TypeDeclaration.class)
                            .map(t -> ((TypeDeclaration<?>) t).getNameAsString().equals(type.get())).orElse(false))
                    .toList();
            return inType.size() == 1 ? Optional.of(inType.getFirst()) : Optional.empty();
        }
        Optional<TypeDeclaration> own = call.findAncestor(TypeDeclaration.class);
        List<MethodDeclaration> inOwnClass = candidates.stream()
                .filter(m -> own.isPresent() && m.findAncestor(TypeDeclaration.class).orElse(null) == own.get()).toList();
        if (inOwnClass.size() == 1) return Optional.of(inOwnClass.getFirst());
        return inOwnClass.isEmpty() && candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    // ——— запись для схемы ———

    /** Метод сам входит в цикл вызовов: сложность зависит от глубины рекурсии — по структуре её не оценить. */
    private static Complexity recursive(CallableDeclaration<?> method) {
        return new Complexity("рекурсия", "unknown", "Метод " + method.getNameAsString()
                + "() рекурсивный (сам или через другие методы): сложность зависит от глубины вызовов — по структуре кода её не оценить.");
    }

    private static Complexity present(Cost cost) {
        String big = notation(cost.power, cost.logs);
        String level = cost.power >= 2 ? "high" : cost.power == 1 ? "linear" : "low";
        String why = cost.why == null ? "Нет циклов по данным и вызовов, зависящих от размера входа."
                : "Оценка сверху по структуре кода (вложенность циклов и вызовов): " + cost.why + ". n — размер входных данных; если внутренний цикл продолжает счётчик внешнего, настоящий рост меньше.";
        if (cost.recursion != null) {
            why += " Вызывает рекурсивный метод " + cost.recursion + "() — его вклад здесь не учтён.";
        }
        return new Complexity(big, level, why);
    }

    static String notation(int power, int logs) {
        String n = switch (power) {
            case 0 -> "";
            case 1 -> "n";
            case 2 -> "n²";
            case 3 -> "n³";
            default -> "n^" + power;
        };
        String log = logs == 0 ? "" : logs == 1 ? "log n" : "log^" + logs + " n";
        String body = (n + " " + log).strip();
        return "O(" + (body.isEmpty() ? "1" : body) + ")";
    }
}
