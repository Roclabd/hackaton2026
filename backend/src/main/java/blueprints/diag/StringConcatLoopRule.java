package blueprints.diag;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;

import java.util.*;

/**
 * R9: строка собирается склейкой в цикле — {@code s += x} или {@code s = s + x}. Каждая склейка копирует всё
 * накопленное, поэтому n добавлений копируют порядка n² символов. Заплатка: {@code StringBuilder} перед циклом,
 * {@code append} внутри, {@code s = ….toString()} после — поведение то же, это рефакторинг.
 *
 * Срабатываем только там, где эквивалентность видна по дереву:
 * <ul>
 *   <li>s — локальная переменная типа String, объявлена в том же блоке, что и цикл, раньше цикла;</li>
 *   <li>внутри цикла s только дописывается целыми операторами и больше нигде не читается и не меняется —
 *       тогда её промежуточные значения никому не видны, и достаточно записать итог после цикла;</li>
 *   <li>дописываемое значение — не массив {@code char[]}: склейка печатает его как объект, а append — как символы.</li>
 * </ul>
 * Если цикл бросит исключение, s выходит из области видимости вместе с блоком, поэтому её значение не наблюдаемо.
 */
final class StringConcatLoopRule {

    static final String RULE = "R9";

    List<Finding> find(DiagnosticContext context) {
        List<Finding> found = new ArrayList<>();
        for (VariableDeclarator variable : context.unit.findAll(VariableDeclarator.class)) {
            Optional<BlockStmt> block = declaringBlock(variable, context);
            if (block.isEmpty()) continue;
            List<Statement> statements = block.get().getStatements();
            int declaredAt = statements.indexOf(variable.findAncestor(ExpressionStmt.class).orElseThrow());
            for (Statement statement : statements.subList(declaredAt + 1, statements.size())) {
                Statement loop = statement instanceof LabeledStmt labeled ? labeled.getStatement() : statement;
                if (!isLoop(loop) || constantLoop(loop)) continue;
                appends(loop, variable.getNameAsString(), context).ifPresent(appends -> {
                    String id = RULE + "-" + (found.size() + 1);
                    found.add(finding(id, variable.getNameAsString(), statement, appends, context));
                });
            }
        }
        return found;
    }

    /** Блок, где s объявлена отдельным оператором {@code String s = …;} внутри метода. */
    private static Optional<BlockStmt> declaringBlock(VariableDeclarator variable, DiagnosticContext context) {
        if (!(variable.getParentNode().orElse(null) instanceof VariableDeclarationExpr declaration)
                || declaration.getVariables().size() != 1
                || !(declaration.getParentNode().orElse(null) instanceof ExpressionStmt statement)
                || !(statement.getParentNode().orElse(null) instanceof BlockStmt block)
                || variable.findAncestor(CallableDeclaration.class).isEmpty()
                || context.methodId(variable).isEmpty()) {
            return Optional.empty();
        }
        // String — именно java.lang.String: в файле нет своего типа или параметра типа с таким именем.
        String type = variable.getType().asString();
        boolean shadowed = context.unit.findAll(com.github.javaparser.ast.body.TypeDeclaration.class).stream()
                .anyMatch(t -> t.getNameAsString().equals("String"))
                || context.unit.findAll(com.github.javaparser.ast.type.TypeParameter.class).stream()
                .anyMatch(t -> t.getNameAsString().equals("String"));
        boolean string = type.equals("java.lang.String") || type.equals("String") && !shadowed;
        return string ? Optional.of(block) : Optional.empty();
    }

    /**
     * for (int i = 0; i < 40; i++): граница — число в коде, итераций всегда столько же, от входа не растёт —
     * это не неэффективность (строка-разделитель из 40 «=»).
     */
    private static boolean constantLoop(Statement loop) {
        if (loop instanceof ForEachStmt each) return fixedLocalCollection(each);
        if (!(loop instanceof ForStmt f) || f.getCompare().isEmpty()
                || !(DiagnosticContext.unwrapped(f.getCompare().get()) instanceof BinaryExpr compare)) return false;
        return compare.getRight() instanceof IntegerLiteralExpr
                && (compare.getOperator() == BinaryExpr.Operator.LESS || compare.getOperator() == BinaryExpr.Operator.LESS_EQUALS);
    }

    /**
     * for (var e : ordered.entrySet()), где ordered — локальная пустая коллекция, которую метод пополняет
     * только отдельными вызовами put/add вне циклов (ordered.put("zeta", 3); …): размер задан текстом, роста нет.
     */
    private static boolean fixedLocalCollection(ForEachStmt loop) {
        Expression iterable = DiagnosticContext.unwrapped(loop.getIterable());
        if (iterable instanceof MethodCallExpr view && view.getArguments().isEmpty()
                && view.getNameAsString().matches("entrySet|keySet|values") && view.getScope().isPresent()) {
            iterable = DiagnosticContext.unwrapped(view.getScope().get());
        }
        if (!(iterable instanceof NameExpr collection)) return false;
        var method = loop.findAncestor(CallableDeclaration.class);
        if (method.isEmpty()) return false;
        String name = collection.getNameAsString();
        boolean emptyLocal = method.get().findAll(VariableDeclarator.class).stream()
                .anyMatch(v -> v.getNameAsString().equals(name) && v.getInitializer().map(DiagnosticContext::unwrapped)
                        .filter(init -> init instanceof ObjectCreationExpr create && create.getArguments().isEmpty()).isPresent());
        if (!emptyLocal) return false;
        for (NameExpr use : method.get().findAll(NameExpr.class)) {
            if (!use.getNameAsString().equals(name) || loop.getIterable().isAncestorOf(use) || loop.getIterable() == use) continue;
            boolean fixedFill = use.getParentNode().orElse(null) instanceof MethodCallExpr call
                    && call.getScope().orElse(null) == use && call.getNameAsString().matches("put|add")
                    && !insideLoopOrLambda(call);
            if (!fixedFill) return false;
        }
        return true;
    }

    private static boolean insideLoopOrLambda(Node node) {
        for (Node up = node.getParentNode().orElse(null); up != null && !(up instanceof CallableDeclaration); up = up.getParentNode().orElse(null)) {
            if (up instanceof LambdaExpr || up instanceof Statement s && isLoop(s)) return true;
        }
        return false;
    }

    private static boolean isLoop(Statement statement) {
        return statement instanceof ForStmt || statement instanceof ForEachStmt
                || statement instanceof WhileStmt || statement instanceof DoStmt;
    }

    /**
     * Операторы-дописывания s внутри цикла и что дописать. Пусто, если s в цикле используется как-то ещё:
     * читается, меняется иначе, объявлена заново или упомянута в лямбде.
     */
    private static Optional<Map<ExpressionStmt, List<Expression>>> appends(Statement loop, String name, DiagnosticContext context) {
        if (loop.findAll(VariableDeclarator.class).stream().anyMatch(v -> v.getNameAsString().equals(name))
                || loop.findAll(com.github.javaparser.ast.body.Parameter.class).stream().anyMatch(p -> p.getNameAsString().equals(name))) {
            return Optional.empty();
        }
        Map<ExpressionStmt, List<Expression>> result = new LinkedHashMap<>();
        Set<NameExpr> allowed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (AssignExpr assign : loop.findAll(AssignExpr.class)) {
            if (!(assign.getTarget() instanceof NameExpr target) || !target.getNameAsString().equals(name)) continue;
            if (!(assign.getParentNode().orElse(null) instanceof ExpressionStmt statement)) return Optional.empty();
            List<Expression> parts = appendedParts(assign, name);
            if (parts.isEmpty()) return Optional.empty();
            result.put(statement, parts);
            allowed.add(target);
            if (assign.getOperator() == AssignExpr.Operator.ASSIGN) allowed.add(leftmost(assign.getValue()));
        }
        boolean otherUse = loop.findAll(NameExpr.class).stream()
                .anyMatch(n -> n.getNameAsString().equals(name) && !allowed.contains(n));
        boolean charArray = result.values().stream().flatMap(List::stream)
                .anyMatch(part -> part instanceof NameExpr n && context.declaredType(n)
                        .filter(t -> t.isArrayType() && t.asArrayType().getComponentType().asString().equals("char")).isPresent());
        boolean insideLambda = result.keySet().stream().anyMatch(s -> s.findAncestor(LambdaExpr.class)
                .filter(lambda -> loop.isAncestorOf(lambda)).isPresent());
        return result.isEmpty() || otherUse || charArray || insideLambda ? Optional.empty() : Optional.of(result);
    }

    /** {@code s += e} → [e]; {@code s = s + a + b} → [a, b]; иначе — пусто (это не дописывание в конец). */
    private static List<Expression> appendedParts(AssignExpr assign, String name) {
        if (assign.getOperator() == AssignExpr.Operator.PLUS) {
            return List.of(assign.getValue());
        }
        if (assign.getOperator() != AssignExpr.Operator.ASSIGN) return List.of();
        Deque<Expression> parts = new ArrayDeque<>();
        Expression value = assign.getValue();
        while (value instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.PLUS) {
            parts.addFirst(binary.getRight());
            value = binary.getLeft();
        }
        // Слева — сама s: значит, каждый следующий «+» — склейка строк, и append даёт тот же текст.
        return value instanceof NameExpr first && first.getNameAsString().equals(name) && !parts.isEmpty()
                ? List.copyOf(parts) : List.of();
    }

    /** Текст аргумента append. Голый null неоднозначен для перегрузок append — (Object) null даёт тот же «null», что и склейка. */
    private static String appendArgument(Expression part, DiagnosticContext context) {
        if (part instanceof NullLiteralExpr) return "(Object) null";
        return context.source.textOf(part).orElse(part.toString());
    }

    private static NameExpr leftmost(Expression value) {
        while (value instanceof BinaryExpr binary) value = binary.getLeft();
        return (NameExpr) value;
    }

    private Finding finding(String id, String name, Statement loopStatement,
                            Map<ExpressionStmt, List<Expression>> appends, DiagnosticContext context) {
        CallableDeclaration<?> method = loopStatement.findAncestor(CallableDeclaration.class).orElseThrow();
        String builder = new NameAllocator(method).fresh(name + "Builder");
        Map<Node, String> edits = new LinkedHashMap<>();
        appends.forEach((statement, parts) -> edits.put(statement, builder + parts.stream()
                .map(p -> ".append(" + appendArgument(p, context) + ")").reduce("", String::concat) + ";"));
        List<Patch> patches = context.source.textWithEdits(loopStatement, edits).map(loopText -> {
            int line = DiagnosticContext.lines(loopStatement)[0];
            String indent = context.source.indentOf(line);
            String sep = context.source.lineSeparator();
            String replacement = "StringBuilder " + builder + " = new StringBuilder(" + name + ");" + sep
                    + indent + loopText + sep + indent + name + " = " + builder + ".toString();";
            int end = line + (int) replacement.lines().count() - 1;
            return List.of(new Patch(id + "-builder", Patch.REFACTOR, "StringBuilder вместо склейки",
                    "Перед циклом — StringBuilder с текущим значением " + name + ", в цикле — append, после цикла — "
                            + name + " = " + builder + ".toString(). Итоговый текст тот же.",
                    context.source.replace(loopStatement, replacement), new int[]{line, end}));
        }).orElse(List.of());
        return context.finding(RULE, loopStatement, "Склейка строки в цикле",
                "Каждое " + name + " += … создаёт новую строку и копирует всё, что накоплено: при n добавлениях копируется "
                        + "порядка n² символов. StringBuilder дописывает в конец без копирования.",
                List.of("Внутри цикла " + name + " только дописывается и больше нигде не читается",
                        "После цикла " + name + " получает тот же текст через toString()"),
                patches, Finding.INEFFICIENCY, Integer.parseInt(id.substring(RULE.length() + 1)));
        // Поля роста не заполняем: n² здесь — скопированные символы, а замер роста считает шаги циклов,
        // которых до и после одинаково. Объяснение — в why.
    }
}
