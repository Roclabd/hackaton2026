package blueprints.graph;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.type.Type;

import java.util.*;

/**
 * Находит рёбра «A вызывает B» внутри одного файла — без компилятора, по правилам видимости Java.
 *
 * «вызов» (точно) — цель одна, и у класса, где её нашли, нет предков вне файла (иначе там мог бы быть ещё кандидат).
 * «вызов?» (предположение) — несколько перегрузок с тем же числом аргументов, или предок вне файла,
 * или вызов на переменной неизвестного типа (по имени, кроме частых имён JDK).
 * Нет ребра — если цель не в этом файле или её нельзя определить честно.
 *
 * Как ищется цель:
 *   foo(…)              — свой класс и его предки; если нет — внешний класс и его предки, и т.д.
 *                         Если у класса есть предок вне файла или имя — метод Object, поиск наружу не идёт:
 *                         в Java метод нашёлся бы у предка, а не у внешнего класса (аудит v3, G4).
 *   this.foo(…)         — свой класс и предки;  Home.this.foo(…) — класс Home (G2)
 *   super.foo(…)        — родитель и его предки;  X.super.foo(…) — интерфейс/класс X
 *   Type.foo(…)         — статический вызов; но если Type — имя переменной или поля, это вызов на объекте (G5)
 *   x.foo(…)            — тип x из объявления (параметр, локальная переменная, поле); тип не из файла — нет ребра (M1)
 *   new Type(…)         — конструктор этого класса
 * Имена классов разрешаются лексически: вложенный класс скрывает одноимённый внешний, Nest.Base — полное имя (G1).
 * Перегрузки собираются по всей цепочке предков; одинаковая сигнатура у потомка скрывает предка (G3).
 * Вызов принадлежит методу, только если он написан в его теле (лямбды — часть метода), а не в локальном
 * или анонимном классе внутри.
 */
final class CallResolver {

    static final String EXACT = "вызов";
    static final String GUESS = "вызов?";

    /** Метод, конструктор или блок инициализации из файла. Блок вызывать нельзя, но он сам вызывает методы. */
    record MethodInfo(String id, String typeName, String name, String signature, int arity, boolean varargs, Node declaration) {
        boolean accepts(int argumentCount) {
            return varargs ? argumentCount >= arity - 1 : argumentCount == arity;
        }
    }

    /** Класс из файла: родитель и интерфейсы — как записаны (с квалификатором, без type-аргументов), типы полей. */
    record TypeInfo(String name, String superclass, List<String> interfaces, boolean isInterface, Map<String, String> fieldTypes) {}

    /** Что нашли: цели и можно ли считать связь точной. */
    private record Found(List<MethodInfo> targets, boolean certain) {
        static final Found NOTHING = new Found(List.of(), false);
    }

    private static final Set<String> OBJECT_METHODS = Set.of(
            "equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait", "clone", "finalize");

    /** Частые имена методов JDK: на переменной неизвестного типа по ним не гадаем, иначе каждый list.add — ложное ребро. */
    private static final Set<String> COMMON_JDK_NAMES = Set.of(
            "add", "addAll", "get", "set", "put", "remove", "size", "isEmpty", "contains", "containsKey",
            "equals", "hashCode", "toString", "compareTo", "length", "charAt", "substring", "append",
            "next", "hasNext", "close", "clear", "sort", "stream", "map", "filter", "forEach",
            "of", "valueOf", "format", "print", "println", "printf", "apply", "accept", "test", "call");

    /** Тип переменной записан, но это не класс (int, массив) или он не ясен (var без new). */
    private static final String UNKNOWN_TYPE = "";
    /** Тип переменной не записан вовсе — параметр лямбды без типа: тогда гадаем по имени метода. */
    private static final String UNTYPED = "?";

    private final List<MethodInfo> methods;
    private final Map<String, TypeInfo> types;
    private final Map<String, List<MethodInfo>> byName = new HashMap<>();

    CallResolver(List<MethodInfo> methods, Map<String, TypeInfo> types) {
        this.methods = methods;
        this.types = types;
        methods.forEach(m -> byName.computeIfAbsent(m.name(), k -> new ArrayList<>()).add(m));
    }

    List<GraphEdge> resolve() {
        Map<String, GraphEdge> edges = new LinkedHashMap<>();
        for (MethodInfo caller : methods) {
            for (MethodCallExpr call : caller.declaration().findAll(MethodCallExpr.class)) {
                if (belongsTo(call, caller.declaration())) {
                    link(caller, resolveCall(caller, call), edges);
                }
            }
            for (ObjectCreationExpr creation : caller.declaration().findAll(ObjectCreationExpr.class)) {
                if (belongsTo(creation, caller.declaration())) {
                    String type = resolveType(creation.getType().getNameWithScope(), caller.typeName());
                    if (type != null) {
                        List<MethodInfo> constructors = candidatesIn(type, "<init>", creation.getArguments().size());
                        link(caller, new Found(constructors, constructors.size() == 1), edges);
                    }
                }
            }
        }
        return List.copyOf(edges.values());
    }

    private Found resolveCall(MethodInfo caller, MethodCallExpr call) {
        String name = call.getNameAsString();
        int args = call.getArguments().size();
        if (call.getScope().isEmpty()) {
            return lookupUnqualified(caller.typeName(), name, args);
        }
        Expression scope = call.getScope().get();
        return switch (scope) {
            case ThisExpr t -> lookupIn(t.getTypeName().map(n -> resolveType(n.asString(), caller.typeName()))
                    .orElse(caller.typeName()), name, args);
            case SuperExpr s -> lookupIn(s.getTypeName().map(n -> resolveType(n.asString(), caller.typeName()))
                    .orElseGet(() -> superclassOf(caller.typeName())), name, args);
            case NameExpr n -> onNamedScope(caller, n.getNameAsString(), name, args);
            case FieldAccessExpr f when f.getScope().isThisExpr() -> onTypedVariable(fieldType(caller.typeName(), f.getNameAsString()), caller, name, args);
            case FieldAccessExpr f -> {
                String type = resolveType(f.toString(), caller.typeName());
                yield type != null ? lookupIn(type, name, args) : guessByName(name, args);
            }
            default -> guessByName(name, args);
        };
    }

    /** x.foo(): x — переменная (тип из объявления) или имя класса (статический вызов). Переменная скрывает класс. */
    private Found onNamedScope(MethodInfo caller, String scopeName, String name, int args) {
        Optional<String> variable = variableType(caller, scopeName);
        if (variable.isPresent()) {
            return onTypedVariable(variable.get(), caller, name, args);
        }
        String type = resolveType(scopeName, caller.typeName());
        return type != null ? lookupIn(type, name, args) : guessByName(name, args);
    }

    /** Вызов на переменной с типом из объявления. Тип не из файла — это метод JDK, ребра нет. */
    private Found onTypedVariable(String declaredType, MethodInfo caller, String name, int args) {
        if (declaredType == null || declaredType.equals(UNTYPED)) {
            return guessByName(name, args);
        }
        if (declaredType.equals(UNKNOWN_TYPE)) {
            return Found.NOTHING;
        }
        String type = resolveType(declaredType, caller.typeName());
        return type != null ? lookupIn(type, name, args) : Found.NOTHING;
    }

    // ---- поиск методов ----

    /** foo(…) без объекта: от своего класса наружу, но не дальше класса с неизвестным предком. */
    private Found lookupUnqualified(String startType, String name, int args) {
        for (String type = startType; type != null; type = outerOf(type)) {
            Found found = lookupIn(type, name, args);
            if (!found.targets().isEmpty()) {
                return found;
            }
            if (hasUnknownAncestor(type) || OBJECT_METHODS.contains(name)) {
                return Found.NOTHING;
            }
        }
        return Found.NOTHING;
    }

    /** Методы класса и его предков из файла; одинаковая сигнатура у ближнего скрывает дальнего. */
    private Found lookupIn(String type, String name, int args) {
        if (type == null || !types.containsKey(type)) {
            return Found.NOTHING;
        }
        List<MethodInfo> targets = new ArrayList<>();
        Set<String> signatures = new HashSet<>();
        for (String ancestor : ancestry(type)) {
            for (MethodInfo m : candidatesIn(ancestor, name, args)) {
                if (signatures.add(m.signature())) {
                    targets.add(m);
                }
            }
        }
        return new Found(targets, targets.size() == 1 && !hasUnknownAncestor(type));
    }

    private List<MethodInfo> candidatesIn(String type, String name, int args) {
        return byName.getOrDefault(name, List.of()).stream()
                .filter(m -> m.typeName().equals(type) && m.accepts(args))
                .toList();
    }

    /** Вызов на переменной неизвестного типа: по имени среди всех методов файла, но не для частых имён JDK. */
    private Found guessByName(String name, int args) {
        if (COMMON_JDK_NAMES.contains(name)) {
            return Found.NOTHING;
        }
        List<MethodInfo> targets = byName.getOrDefault(name, List.of()).stream().filter(m -> m.accepts(args)).toList();
        return new Found(targets, false);
    }

    // ---- классы и предки ----

    /** Сам класс, затем родители, затем интерфейсы — в ширину, без повторов. Только классы из файла. */
    private List<String> ancestry(String type) {
        List<String> order = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>(List.of(type));
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current == null || order.contains(current) || !types.containsKey(current)) {
                continue;
            }
            order.add(current);
            TypeInfo info = types.get(current);
            if (info.superclass() != null) {
                queue.add(Objects.requireNonNullElse(resolveType(info.superclass(), current), ""));
            }
            info.interfaces().forEach(i -> queue.add(Objects.requireNonNullElse(resolveType(i, current), "")));
        }
        return order;
    }

    /** Есть ли у класса (или его предков) предок, объявленный не в этом файле, — Exception, Runnable и т.п. */
    private boolean hasUnknownAncestor(String type) {
        for (String known : ancestry(type)) {
            TypeInfo info = types.get(known);
            if (info.superclass() != null && resolveType(info.superclass(), known) == null) {
                return true;
            }
            for (String i : info.interfaces()) {
                if (resolveType(i, known) == null) {
                    return true;
                }
            }
        }
        return false;
    }

    private String superclassOf(String type) {
        TypeInfo info = types.get(type);
        return info == null || info.superclass() == null ? null : resolveType(info.superclass(), type);
    }

    /**
     * Имя класса → полное имя в файле, по правилам видимости: член своего класса, затем внешних, затем верхний уровень.
     * «Nest.Base» — первое слово разрешается так же, остальное дописывается. Не класс из файла → null.
     */
    private String resolveType(String written, String context) {
        if (written == null || written.isEmpty()) {
            return null;
        }
        String name = written.replaceAll("<.*>", "").replace("[]", "");
        int dot = name.indexOf('.');
        if (dot > 0) {
            if (types.containsKey(name)) {
                return name;
            }
            String head = resolveType(name.substring(0, dot), context);
            String full = head == null ? null : head + name.substring(dot);
            return full != null && types.containsKey(full) ? full : null;
        }
        for (String scope = context; scope != null; scope = outerOf(scope)) {
            if (types.containsKey(scope + "." + name)) {
                return scope + "." + name;
            }
            if (scope.equals(name) || scope.endsWith("." + name)) {
                return scope;
            }
        }
        return types.containsKey(name) ? name : null;
    }

    // ---- переменные ----

    /**
     * Тип переменной scopeName, видимой в методе: параметр, локальная переменная, переменная цикла или catch,
     * затем поле своего класса или внешних. Пусто — это не переменная (значит, имя класса).
     * UNKNOWN_TYPE — переменная есть, но тип не класс или не ясен (int, массив, var без new).
     * UNTYPED — переменная есть, тип не записан совсем (параметр лямбды без типа).
     */
    private Optional<String> variableType(MethodInfo caller, String scopeName) {
        Node declaration = caller.declaration();
        if (declaration instanceof CallableDeclaration<?> callable) {
            for (Parameter p : callable.getParameters()) {
                if (p.getNameAsString().equals(scopeName)) {
                    return Optional.of(typeNameOf(p.getType()));
                }
            }
        }
        if (declaration instanceof CompactConstructorDeclaration compact) {
            for (Parameter p : compact.findAncestor(RecordDeclaration.class).orElseThrow().getParameters()) {
                if (p.getNameAsString().equals(scopeName)) return Optional.of(typeNameOf(p.getType()));
            }
        }
        for (VariableDeclarator v : declaration.findAll(VariableDeclarator.class)) {
            if (v.getNameAsString().equals(scopeName) && belongsTo(v, declaration)) {
                if (v.getType().isVarType()) {
                    return Optional.of(v.getInitializer().filter(Expression::isObjectCreationExpr)
                            .map(i -> i.asObjectCreationExpr().getType().getNameWithScope()).orElse(UNKNOWN_TYPE));
                }
                return Optional.of(typeNameOf(v.getType()));
            }
        }
        for (Parameter p : declaration.findAll(Parameter.class)) {
            if (p.getNameAsString().equals(scopeName) && (p.getParentNode().orElse(null) instanceof CatchClause
                    || p.getParentNode().orElse(null) instanceof LambdaExpr)) {
                return Optional.of(p.getType().isUnknownType() ? UNTYPED : typeNameOf(p.getType()));
            }
        }
        for (String type = caller.typeName(); type != null; type = outerOf(type)) {
            String field = fieldType(type, scopeName);
            if (field != null) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }

    /** Тип поля класса или его предков из файла; null — такого поля нет. */
    private String fieldType(String type, String fieldName) {
        for (String ancestor : ancestry(type)) {
            String field = types.get(ancestor).fieldTypes().get(fieldName);
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    /** Имя класса из записи типа: Map<String, X> → Map, Nest.Base → Nest.Base; int, массивы → UNKNOWN_TYPE. */
    static String typeNameOf(Type type) {
        return type.isClassOrInterfaceType() ? type.asClassOrInterfaceType().getNameWithScope() : UNKNOWN_TYPE;
    }

    // ---- общее ----

    /**
     * Выражение написано непосредственно в этом методе, а не в локальном или анонимном классе внутри него.
     * Поднимаемся к первой «границе» — объявлению со своим кодом. Это должен быть сам метод.
     */
    private static boolean belongsTo(Node expression, Node callerDeclaration) {
        for (Node node = expression.getParentNode().orElse(null); node != null; node = node.getParentNode().orElse(null)) {
            if (node instanceof CallableDeclaration<?> || node instanceof CompactConstructorDeclaration
                    || node instanceof InitializerDeclaration
                    || node instanceof TypeDeclaration<?> || node instanceof FieldDeclaration
                    || node instanceof EnumConstantDeclaration) {
                return node == callerDeclaration;
            }
        }
        return false;
    }

    private static String outerOf(String typeName) {
        int dot = typeName.lastIndexOf('.');
        return dot < 0 ? null : typeName.substring(0, dot);
    }

    private static void link(MethodInfo caller, Found found, Map<String, GraphEdge> edges) {
        String label = found.certain() ? EXACT : GUESS;
        for (MethodInfo target : found.targets()) {
            String edgeId = caller.id() + "->" + target.id();
            edges.putIfAbsent(edgeId, new GraphEdge(edgeId, caller.id(), target.id(), EdgeKind.CALL, label));
        }
    }
}
