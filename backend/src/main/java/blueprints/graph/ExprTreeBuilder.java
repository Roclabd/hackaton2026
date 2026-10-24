package blueprints.graph;

import blueprints.parse.SourceText;
import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Строит дерево выражения ({@link ExprNode}) для оператора чертежа — «подробная схема».
 * Форма и роли — по docs/API.md, раздел «Дерево выражения ExprNode».
 *
 * Правила, о которых легко забыть:
 *  - скобки {@code (a + b)} прозрачны: отдельного узла нет, у ребёнка свой диапазон без скобок;
 *  - у узла не больше {@value #MAX_CHILDREN} детей, остальные сворачиваются в один узел «ещё N»;
 *  - {@code concat} — эвристика: типы не вычисляются, смотрим только на строковые литералы.
 */
public final class ExprTreeBuilder {

    private static final int MAX_CHILDREN = 12;
    private static final int MAX_CODE = 200;
    private static final int MAX_LITERAL = 30;
    private static final int MAX_LABEL = 60;

    private final SourceText source;

    public ExprTreeBuilder(SourceText source) {
        this.source = source;
    }

    /**
     * Выражение, которое показывает узел чертежа (таблица в контракте), или null, если показывать нечего:
     * {@code break}, {@code continue}, {@code return;}, {@code try} без ресурсов и прочее.
     *
     * @param nodeId id узла-оператора; id корня выражения — он же плюс «/e»
     */
    public ExprNode forStatement(Statement statement, String nodeId) {
        String id = nodeId + "/e";
        return switch (statement) {
            case ExpressionStmt s -> build(s.getExpression(), id, null);
            case IfStmt s -> build(s.getCondition(), id, null);
            case WhileStmt s -> build(s.getCondition(), id, null);
            case DoStmt s -> build(s.getCondition(), id, null);
            case ForStmt s -> forHeader(s, id);
            case ForEachStmt s -> forEachHeader(s, id);
            case SwitchStmt s -> build(s.getSelector(), id, null);
            case ReturnStmt s -> s.getExpression().map(e -> build(e, id, null)).orElse(null);
            case ThrowStmt s -> build(s.getExpression(), id, null);
            case TryStmt s -> resources(s, id);
            case AssertStmt s -> build(s.getCheck(), id, null);
            case ExplicitConstructorInvocationStmt s -> constructorCall(s, id);
            default -> null;
        };
    }

    // ---- Выбор вида узла по типу узла дерева разбора ----

    private ExprNode build(Node node, String id, String role) {
        return switch (node) {
            case EnclosedExpr e -> build(e.getInner(), id, role);
            case MethodCallExpr e -> call(e, id, role);
            case ObjectCreationExpr e -> newObject(e, id, role);
            case ArrayCreationExpr e -> newArray(e, id, role);
            case BinaryExpr e -> binary(e, id, role);
            case UnaryExpr e when isSignedNumber(e) -> leaf(id, "literal", oneLine(text(e), MAX_LITERAL), e, role);
            case UnaryExpr e -> unary(e, id, role);
            case AssignExpr e -> assign(e, id, role);
            case ConditionalExpr e -> ternary(e, id, role);
            case InstanceOfExpr e -> instanceOf(e, id, role);
            case CastExpr e -> cast(e, id, role);
            case LambdaExpr e -> lambda(e, id, role);
            case MethodReferenceExpr e -> leaf(id, "methodRef", text(e.getScope()) + "::" + e.getIdentifier(), e, role);
            case LiteralExpr e -> leaf(id, "literal", oneLine(text(e), MAX_LITERAL), e, role);
            case NameExpr e -> leaf(id, "name", e.getNameAsString(), e, role);
            case ThisExpr e -> leaf(id, "name", text(e), e, role);
            case SuperExpr e -> leaf(id, "name", text(e), e, role);
            case FieldAccessExpr e -> field(e, id, role);
            case ArrayAccessExpr e -> index(e, id, role);
            case ArrayInitializerExpr e -> arrayInit(e, id, role);
            case SwitchExpr e -> switchExpr(e, id, role);
            case VariableDeclarationExpr e -> declare(e, id, role);
            case VariableDeclarator e -> variable(e, id, role);
            case SwitchEntry e -> switchCase(e, id, role);
            case BlockStmt e -> block(e, id, role);
            default -> leaf(id, "other", oneLine(text(node), MAX_LABEL), node, role);
        };
    }

    // ---- Вызовы и создание объектов ----

    private ExprNode call(MethodCallExpr e, String id, String role) {
        List<Sub> subs = new ArrayList<>();
        String label = e.getNameAsString();
        if (e.getScope().isPresent()) {
            Expression scope = e.getScope().get();
            if (isTypeQualifier(scope)) {
                label = text(scope) + "." + label; // Objects.equals, System.out.println: класс — часть имени
            } else {
                subs.add(sub(scope, "объект"));
            }
        }
        subs.addAll(subs(e.getArguments(), "арг "));
        return node(id, "call", label, range(e), role, subs);
    }

    private ExprNode constructorCall(ExplicitConstructorInvocationStmt s, String id) {
        List<Sub> subs = new ArrayList<>();
        s.getExpression().ifPresent(outer -> subs.add(sub(outer, "объект")));
        subs.addAll(subs(s.getArguments(), "арг "));
        return node(id, "call", s.isThis() ? "this" : "super", withoutSemicolon(range(s)), null, subs);
    }

    private ExprNode newObject(ObjectCreationExpr e, String id, String role) {
        List<Sub> subs = new ArrayList<>();
        e.getScope().ifPresent(outer -> subs.add(sub(outer, "объект")));
        subs.addAll(subs(e.getArguments(), "арг "));
        String label = "new " + text(e.getType()) + (e.getAnonymousClassBody().isPresent() ? " {…}" : "");
        return node(id, "new", label, range(e), role, subs);
    }

    private ExprNode newArray(ArrayCreationExpr e, String id, String role) {
        List<Sub> subs = new ArrayList<>();
        e.getLevels().forEach(level -> level.getDimension().ifPresent(size -> subs.add(sub(size, "размер"))));
        e.getInitializer().ifPresent(init -> subs.add(sub(init, "значение")));
        String label = "new " + text(e.getElementType()) + "[]".repeat(e.getLevels().size());
        return node(id, "new", label, range(e), role, subs);
    }

    // ---- Операции ----

    private ExprNode binary(BinaryExpr e, String id, String role) {
        String kind = isConcat(e) ? "concat" : "binary";
        return node(id, kind, e.getOperator().asString(), range(e), role,
                List.of(sub(e.getLeft(), "слева"), sub(e.getRight(), "справа")));
    }

    /** Эвристика: «+», где хотя бы одна сторона — строковый литерал или такая же склейка. */
    private static boolean isConcat(BinaryExpr e) {
        if (e.getOperator() != BinaryExpr.Operator.PLUS) {
            return false;
        }
        return isStringy(strip(e.getLeft())) || isStringy(strip(e.getRight()));
    }

    private static boolean isStringy(Expression e) {
        return e instanceof StringLiteralExpr || e instanceof TextBlockLiteralExpr
                || (e instanceof BinaryExpr b && isConcat(b));
    }

    private ExprNode unary(UnaryExpr e, String id, String role) {
        UnaryExpr.Operator op = e.getOperator();
        boolean incDec = op == UnaryExpr.Operator.PREFIX_INCREMENT || op == UnaryExpr.Operator.PREFIX_DECREMENT
                || op == UnaryExpr.Operator.POSTFIX_INCREMENT || op == UnaryExpr.Operator.POSTFIX_DECREMENT;
        String label = !incDec ? op.asString() : op.isPostfix() ? "x" + op.asString() : op.asString() + "x";
        return node(id, "unary", label, range(e), role, List.of(sub(e.getExpression(), "значение")));
    }

    /** «-1» — это число, а не операция над единицей: так его и показываем. */
    private static boolean isSignedNumber(UnaryExpr e) {
        boolean sign = e.getOperator() == UnaryExpr.Operator.MINUS || e.getOperator() == UnaryExpr.Operator.PLUS;
        Expression operand = strip(e.getExpression());
        return sign && (operand instanceof IntegerLiteralExpr || operand instanceof LongLiteralExpr
                || operand instanceof DoubleLiteralExpr);
    }

    private ExprNode assign(AssignExpr e, String id, String role) {
        return node(id, "assign", e.getOperator().asString(), range(e), role,
                List.of(sub(e.getTarget(), "цель"), sub(e.getValue(), "значение")));
    }

    private ExprNode ternary(ConditionalExpr e, String id, String role) {
        return node(id, "ternary", "?:", range(e), role,
                List.of(sub(e.getCondition(), "условие"), sub(e.getThenExpr(), "да"), sub(e.getElseExpr(), "нет")));
    }

    private ExprNode instanceOf(InstanceOfExpr e, String id, String role) {
        String tested = e.getPattern().map(this::text).orElseGet(() -> text(e.getType())); // «String s» или «String»
        return node(id, "instanceof", "instanceof " + tested, range(e), role, List.of(sub(e.getExpression(), "значение")));
    }

    private ExprNode cast(CastExpr e, String id, String role) {
        return node(id, "cast", "(" + text(e.getType()) + ")", range(e), role, List.of(sub(e.getExpression(), "значение")));
    }

    private ExprNode lambda(LambdaExpr e, String id, String role) {
        String params = String.join(", ", e.getParameters().stream().map(this::text).toList());
        Sub body = e.getExpressionBody().isPresent() ? sub(e.getExpressionBody().get(), "тело") : sub(e.getBody(), "тело");
        return node(id, "lambda", "(" + params + ") ->", range(e), role, List.of(body));
    }

    // ---- Доступ к данным ----

    private ExprNode field(FieldAccessExpr e, String id, String role) {
        if (isTypeQualifier(e.getScope())) {
            return leaf(id, "field", text(e), e, role); // Integer.MAX_VALUE — один узел
        }
        return node(id, "field", "." + e.getNameAsString(), range(e), role, List.of(sub(e.getScope(), "объект")));
    }

    private ExprNode index(ArrayAccessExpr e, String id, String role) {
        return node(id, "index", "[ ]", range(e), role, List.of(sub(e.getName(), "массив"), sub(e.getIndex(), "индекс")));
    }

    private ExprNode arrayInit(ArrayInitializerExpr e, String id, String role) {
        int n = e.getValues().size();
        String label = "{ " + n + " " + plural(n, "элемент", "элемента", "элементов") + " }";
        return node(id, "arrayInit", label, range(e), role, subs(e.getValues(), "элемент", false));
    }

    // ---- switch-выражение ----

    private ExprNode switchExpr(SwitchExpr e, String id, String role) {
        List<Sub> subs = new ArrayList<>();
        subs.add(sub(e.getSelector(), "селектор"));
        for (int i = 0; i < e.getEntries().size(); i++) {
            subs.add(sub(e.getEntries().get(i), "case " + (i + 1)));
        }
        return node(id, "switchExpr", "switch", range(e), role, subs);
    }

    /** Одна ветка: подпись «case 1, 2 ->» и либо значение, либо блок «{ N операторов }». */
    private ExprNode switchCase(SwitchEntry entry, String id, String role) {
        String arrow = entry.getType() == SwitchEntry.Type.STATEMENT_GROUP ? ":" : " ->";
        String labels = String.join(", ", entry.getLabels().stream().map(this::text).toList());
        String guard = entry.getGuard().map(g -> " when " + text(g)).orElse("");
        String label = (entry.getLabels().isEmpty() ? "default" : "case " + labels) + guard + arrow;
        return node(id, "other", oneLine(label, MAX_LABEL), range(entry), role, List.of(caseBody(entry)));
    }

    private Sub caseBody(SwitchEntry entry) {
        List<Statement> statements = entry.getStatements();
        if (entry.getType() == SwitchEntry.Type.EXPRESSION && statements.get(0) instanceof ExpressionStmt s) {
            return sub(s.getExpression(), "значение");
        }
        if (entry.getType() == SwitchEntry.Type.THROWS_STATEMENT) {
            return sub(statements.get(0), "значение");
        }
        return new Sub("тело", childId -> statementsBlock(childId, "тело", statements, range(entry)));
    }

    // ---- Объявления и заголовки циклов ----

    private ExprNode declare(VariableDeclarationExpr e, String id, String role) {
        String type = text(e.getVariable(0).getType());
        return node(id, "declare", type, range(e), role, subs(e.getVariables(), "переменная", false));
    }

    /** Переменная из объявления: имя и (если есть) инициализатор. */
    private ExprNode variable(VariableDeclarator e, String id, String role) {
        List<Sub> subs = new ArrayList<>();
        e.getInitializer().ifPresent(init -> subs.add(sub(init, "инициализация")));
        return node(id, "name", e.getNameAsString(), range(e), role, subs);
    }

    private ExprNode forHeader(ForStmt s, String id) {
        List<Sub> subs = new ArrayList<>();
        s.getInitialization().forEach(e -> subs.add(sub(e, "инициализация")));
        s.getCompare().ifPresent(e -> subs.add(sub(e, "условие")));
        s.getUpdate().forEach(e -> subs.add(sub(e, "шаг")));
        Position last = lastEnd(s.getInitialization(), s.getCompare().orElse(null), s.getUpdate());
        Position begin = range(s).begin;
        Range header = last == null
                ? Range.range(begin.line, begin.column, begin.line, begin.column + 2) // for(;;) — только ключевое слово
                : Range.range(begin, afterClosingParen(last));
        return node(id, "forHeader", "for", header, null, subs);
    }

    private ExprNode forEachHeader(ForEachStmt s, String id) {
        List<Sub> subs = List.of(sub(s.getVariable(), "элемент"), sub(s.getIterable(), "коллекция"));
        Range header = Range.range(range(s).begin, afterClosingParen(range(s.getIterable()).end));
        return node(id, "forEachHeader", "for each", header, null, subs);
    }

    private ExprNode resources(TryStmt s, String id) {
        if (s.getResources().isEmpty()) {
            return null;
        }
        Range all = Range.range(range(s.getResources().get(0)).begin, range(s.getResources().get(s.getResources().size() - 1)).end);
        return node(id, "resources", "ресурсы", all, null, subs(s.getResources(), "ресурс", false));
    }

    /** Диапазон настоящего блока берём у AST целиком, вместе со скобками и комментариями. */
    private ExprNode block(BlockStmt block, String id, String role) {
        int n = block.getStatements().size();
        String label = "{ " + n + " " + plural(n, "оператор", "оператора", "операторов") + " }";
        return node(id, "block", label, range(block), role, List.of());
    }
    /** Блок из нескольких операторов не раскрываем: на узле только «{ N операторов }». */
    private ExprNode statementsBlock(String id, String role, List<Statement> statements, Range fallback) {
        if (statements.size() == 1 && statements.get(0) instanceof BlockStmt inner) {
            return block(inner, id, role);
        }
        Range range = statements.isEmpty() ? fallback
                : Range.range(range(statements.get(0)).begin, range(statements.get(statements.size() - 1)).end);
        int n = statements.size();
        String label = "{ " + n + " " + plural(n, "оператор", "оператора", "операторов") + " }";
        return node(id, "block", label, range, role, List.of());
    }

    // ---- Сборка узла ----

    /** Описание ребёнка: его роль и как построить узел по id, который выдаст родитель. */
    private record Sub(String role, Function<String, ExprNode> make) {}

    private Sub sub(Node ast, String role) {
        return new Sub(role, childId -> build(ast, childId, role));
    }

    /** Список однотипных детей с ролями «арг 1», «арг 2», … */
    private List<Sub> subs(List<? extends Node> nodes, String rolePrefix) {
        return subs(nodes, rolePrefix, true);
    }

    private List<Sub> subs(List<? extends Node> nodes, String rolePrefix, boolean numbered) {
        List<Sub> result = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            result.add(sub(nodes.get(i), numbered ? rolePrefix + (i + 1) : rolePrefix));
        }
        return result;
    }

    private ExprNode leaf(String id, String kind, String label, Node ast, String role) {
        return node(id, kind, label, range(ast), role, List.of());
    }

    private ExprNode node(String id, String kind, String label, Range range, String role, List<Sub> subs) {
        int[] position = {range.begin.line, range.begin.column, range.end.line, range.end.column};
        return new ExprNode(id, kind, label, code(range), position, role, children(id, range, subs));
    }

    /** Первые {@value #MAX_CHILDREN} детей, остальные — одним узлом «ещё N». */
    private List<ExprNode> children(String id, Range parentRange, List<Sub> subs) {
        List<ExprNode> result = new ArrayList<>();
        int shown = Math.min(subs.size(), MAX_CHILDREN);
        for (int i = 0; i < shown; i++) {
            result.add(subs.get(i).make().apply(id + "/" + i));
        }
        if (subs.size() > MAX_CHILDREN) {
            String more = "ещё " + (subs.size() - MAX_CHILDREN);
            int[] position = {parentRange.begin.line, parentRange.begin.column, parentRange.end.line, parentRange.end.column};
            result.add(new ExprNode(id + "/" + MAX_CHILDREN, "other", more, more, position, null, List.of()));
        }
        return List.copyOf(result);
    }

    // ---- Мелкие помощники ----

    private static Expression strip(Expression e) {
        return e instanceof EnclosedExpr enclosed ? strip(enclosed.getInner()) : e;
    }

    /** «Objects», «System.out», «Integer»: цепочка имён, начинающаяся с имени класса (а не константы и не переменной). */
    private static boolean isTypeQualifier(Expression e) {
        return switch (strip(e)) {
            case NameExpr n -> looksLikeClassName(n.getNameAsString());
            case FieldAccessExpr f -> isTypeQualifier(f.getScope());
            default -> false;
        };
    }

    private static boolean looksLikeClassName(String name) {
        return Character.isUpperCase(name.charAt(0)) && name.chars().anyMatch(Character::isLowerCase);
    }

    private static Range range(Node node) {
        return node.getRange().orElse(Range.range(1, 1, 1, 1));
    }

    private String text(Node node) {
        return node.getRange().map(source::slice).orElseGet(node::toString);
    }

    private String code(Range range) {
        String code = source.slice(range);
        return code.length() <= MAX_CODE ? code : code.substring(0, MAX_CODE - 1) + "…";
    }

    private static String oneLine(String text, int max) {
        String line = text.replaceAll("\\s+", " ").strip();
        return line.length() <= max ? line : line.substring(0, max - 1) + "…";
    }

    /** Конец последнего из элементов заголовка for или null, если заголовок пуст. */
    private static Position lastEnd(List<? extends Node> init, Node compare, List<? extends Node> update) {
        Position last = null;
        List<Node> all = new ArrayList<>(init);
        if (compare != null) {
            all.add(compare);
        }
        all.addAll(update);
        for (Node node : all) {
            Position end = range(node).end;
            if (last == null || end.isAfter(last)) {
                last = end;
            }
        }
        return last;
    }

    /** Если сразу после позиции (через пробелы на той же строке) стоит «)», заголовок заканчивается на ней. */
    private Position afterClosingParen(Position end) {
        String text = source.text();
        int offset = source.offset(end) + 1;
        int column = end.column;
        while (offset < text.length() && (text.charAt(offset) == ' ' || text.charAt(offset) == '\t')) {
            offset++;
            column++;
        }
        boolean closes = offset < text.length() && text.charAt(offset) == ')';
        return closes ? new Position(end.line, column + 1) : end;
    }

    /** У оператора «this(…);» диапазон включает «;», а у выражения его быть не должно. */
    private Range withoutSemicolon(Range range) {
        boolean endsWithSemicolon = source.slice(range).endsWith(";");
        return endsWithSemicolon ? Range.range(range.begin, new Position(range.end.line, range.end.column - 1)) : range;
    }

    /** 1 оператор, 2 оператора, 5 операторов. */
    private static String plural(int n, String one, String few, String many) {
        int lastTwo = n % 100;
        int last = n % 10;
        if (last == 1 && lastTwo != 11) {
            return one;
        }
        return last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14) ? few : many;
    }
}
