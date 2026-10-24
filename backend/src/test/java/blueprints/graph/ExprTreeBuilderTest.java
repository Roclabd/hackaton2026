package blueprints.graph;

import blueprints.parse.SourceParser;
import blueprints.parse.SourceText;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Дерево выражения: каждый вид узла, роли, диапазоны, скобки, ограничения и два настоящих монолита. */
class ExprTreeBuilderTest {

    private static final Path MATCH_DEMO = Path.of("../corpus/ours/MatchDemo/MatchDemo.java");
    private static final Path MINILANG = Path.of("../corpus/ours/MiniLang/MiniLang.java");

    private final ObjectMapper json = new ObjectMapper();

    // ---- Вызовы и создание объектов ----

    @Test
    void staticCallHasClassInLabelAndNumberedArguments() {
        String stmt = "Objects.equals(x, y);";
        ExprNode call = exprOf(stmt);

        assertNode(call, "call", "Objects.equals", null);
        assertRange(call, stmt, "Objects.equals(x, y)");
        assertEquals(2, call.children().size());
        assertNode(call.children().get(0), "name", "x", "арг 1");
        assertNode(call.children().get(1), "name", "y", "арг 2");
        assertRange(call.children().get(1), stmt, "y");
    }

    @Test
    void callOnVariableKeepsObjectAsChild() {
        ExprNode call = exprOf("list.add(x + 1);");

        assertNode(call, "call", "add", null);
        assertNode(call.children().get(0), "name", "list", "объект");
        assertNode(call.children().get(1), "binary", "+", "арг 1");
    }

    @Test
    void chainedCallNestsReceiver() {
        ExprNode call = exprOf("line.trim().split(sep);");

        assertNode(call, "call", "split", null);
        assertNode(call.children().get(0), "call", "trim", "объект");
        assertNode(call.children().get(0).children().get(0), "name", "line", "объект");
        assertNode(call.children().get(1), "name", "sep", "арг 1");
    }

    @Test
    void newObjectAndNewArray() {
        ExprNode declare = exprOf("Foo f = new Foo(1, \"a\");");
        ExprNode created = declare.children().get(0).children().get(0);
        assertNode(created, "new", "new Foo", "инициализация");
        assertNode(created.children().get(1), "literal", "\"a\"", "арг 2");

        ExprNode sized = exprOf("a = new int[n + 1][];").children().get(1);
        assertNode(sized, "new", "new int[][]", "значение");
        assertNode(sized.children().get(0), "binary", "+", "размер");

        ExprNode withValues = exprOf("a = new int[]{1, 2};").children().get(1);
        assertNode(withValues, "new", "new int[]", "значение");
        assertNode(withValues.children().get(0), "arrayInit", "{ 2 элемента }", "значение");
    }

    @Test
    void thisCallInConstructorIsCallWithoutSemicolon() {
        String source = "class T {\n    T() {\n        this(1);\n    }\n    T(int a) {}\n}\n";
        ExprNode call = firstExpr(source);

        assertNode(call, "call", "this", null);
        assertEquals("this(1)", call.code());
        assertArrayEquals(new int[]{3, 9, 3, 15}, call.range());
        assertNode(call.children().get(0), "literal", "1", "арг 1");
    }

    // ---- Операции ----

    @Test
    void binaryOperationHasLeftAndRight() {
        String stmt = "r = a * b;";
        ExprNode binary = exprOf(stmt).children().get(1);

        assertNode(binary, "binary", "*", "значение");
        assertRange(binary, stmt, "a * b");
        assertNode(binary.children().get(0), "name", "a", "слева");
        assertNode(binary.children().get(1), "name", "b", "справа");
    }

    @Test
    void plusWithStringLiteralIsConcatAndNestedConcatStaysConcat() {
        ExprNode text = exprOf("s = \"n=\" + n + 1;").children().get(1);
        assertNode(text, "concat", "+", "значение");
        assertNode(text.children().get(0), "concat", "+", "слева");

        ExprNode numbers = exprOf("s = a + b;").children().get(1);
        assertEquals("binary", numbers.kind(), "без строкового литерала типы не угадываем");

        ExprNode tail = exprOf("s = a + b + \"!\";").children().get(1);
        assertEquals("concat", tail.kind());
        assertEquals("binary", tail.children().get(0).kind(), "a + b — пока число");
    }

    @Test
    void parenthesesAreTransparent() {
        String stmt = "r = (a + b) * c;";
        ExprNode product = exprOf(stmt).children().get(1);

        assertNode(product, "binary", "*", "значение");
        ExprNode sum = product.children().get(0);
        assertNode(sum, "binary", "+", "слева");
        assertRange(sum, stmt, "a + b");
        assertEquals(product.id() + "/0", sum.id());
    }

    @Test
    void unaryOperatorsHaveSignAsLabel() {
        assertNode(exprOf("i++;"), "unary", "x++", null);
        assertNode(exprOf("++i;"), "unary", "++x", null);
        assertNode(exprOf("i--;"), "unary", "x--", null);
        assertNode(exprOf("--i;"), "unary", "--x", null);
        ExprNode not = exprOf("ok = !flag;").children().get(1);
        assertNode(not, "unary", "!", "значение");
        assertNode(not.children().get(0), "name", "flag", "значение");
        assertNode(exprOf("m = -x;").children().get(1), "unary", "-", "значение");
        assertNode(exprOf("m = ~x;").children().get(1), "unary", "~", "значение");
    }

    @Test
    void negativeNumberIsOneLiteral() {
        ExprNode number = exprOf("m = -1;").children().get(1);
        assertNode(number, "literal", "-1", "значение");
        assertTrue(number.children().isEmpty());
    }

    @Test
    void assignHasTargetAndValue() {
        String stmt = "total += price * qty;";
        ExprNode assign = exprOf(stmt);

        assertNode(assign, "assign", "+=", null);
        assertRange(assign, stmt, "total += price * qty");
        assertNode(assign.children().get(0), "name", "total", "цель");
        assertNode(assign.children().get(1), "binary", "*", "значение");
    }

    @Test
    void ternaryHasConditionThenElse() {
        ExprNode ternary = exprOf("r = ok ? 1 : 2;").children().get(1);

        assertNode(ternary, "ternary", "?:", "значение");
        assertNode(ternary.children().get(0), "name", "ok", "условие");
        assertNode(ternary.children().get(1), "literal", "1", "да");
        assertNode(ternary.children().get(2), "literal", "2", "нет");
    }

    @Test
    void instanceofWithAndWithoutPattern() {
        String stmt = "if (o instanceof String s) { }";
        ExprNode test = exprOf(stmt);
        assertNode(test, "instanceof", "instanceof String s", null);
        assertRange(test, stmt, "o instanceof String s");
        assertNode(test.children().get(0), "name", "o", "значение");

        assertNode(exprOf("if (o instanceof String) { }"), "instanceof", "instanceof String", null);
    }

    @Test
    void castShowsType() {
        String stmt = "x = (long) y;";
        ExprNode cast = exprOf(stmt).children().get(1);

        assertNode(cast, "cast", "(long)", "значение");
        assertRange(cast, stmt, "(long) y");
        assertNode(cast.children().get(0), "name", "y", "значение");
    }

    // ---- Лямбды и ссылки на методы ----

    @Test
    void lambdaWithExpressionBody() {
        ExprNode lambda = exprOf("f = (p, q) -> p + q;").children().get(1);

        assertNode(lambda, "lambda", "(p, q) ->", "значение");
        assertNode(lambda.children().get(0), "binary", "+", "тело");
    }

    @Test
    void lambdaWithBlockBodyShowsStatementCount() {
        String stmt = "r = () -> { a(); b(); };";
        ExprNode body = exprOf(stmt).children().get(1).children().get(0);

        assertNode(body, "block", "{ 2 оператора }", "тело");
        assertRange(body, stmt, "{ a(); b(); }");
        assertTrue(body.children().isEmpty());
    }

    @Test
    void emptyLambdaBlockKeepsBracesAndComment() {
        String stmt = "r = () -> { /* пусто */ };";
        ExprNode body = exprOf(stmt).children().get(1).children().get(0);

        assertNode(body, "block", "{ 0 операторов }", "тело");
        assertRange(body, stmt, "{ /* пусто */ }");
    }

    @Test
    void nestedLambdaBlockKeepsOuterBraces() {
        String stmt = "r = () -> { { a(); b(); } };";
        ExprNode body = exprOf(stmt).children().get(1).children().get(0);

        assertNode(body, "block", "{ 1 оператор }", "тело");
        assertRange(body, stmt, "{ { a(); b(); } }");
    }

    @Test
    void multilineLambdaBlockKeepsFullAstRange() {
        String stmt = "r = () -> {\n      a();\n      b();\n    };";
        ExprNode body = exprOf(stmt).children().get(1).children().get(0);

        assertNode(body, "block", "{ 2 оператора }", "тело");
        assertEquals("{\n      a();\n      b();\n    }", body.code());
        assertArrayEquals(new int[]{3, 15, 6, 5}, body.range());
    }

    @Test
    void methodReference() {
        ExprNode reference = exprOf("g = String::valueOf;").children().get(1);

        assertNode(reference, "methodRef", "String::valueOf", "значение");
        assertTrue(reference.children().isEmpty());
    }

    // ---- Значения и доступ ----

    @Test
    void literalsAndLongStringIsCutTo30() {
        assertNode(exprOf("a = 42;").children().get(1), "literal", "42", "значение");
        assertNode(exprOf("a = 'c';").children().get(1), "literal", "'c'", "значение");
        assertNode(exprOf("a = true;").children().get(1), "literal", "true", "значение");
        assertNode(exprOf("a = null;").children().get(1), "literal", "null", "значение");

        String longString = "\"" + "x".repeat(40) + "\"";
        ExprNode literal = exprOf("a = " + longString + ";").children().get(1);
        assertEquals(30, literal.label().length());
        assertTrue(literal.label().endsWith("…"));
        assertEquals(longString, literal.code(), "в code остаётся весь текст");
    }

    @Test
    void fieldOfObjectHasChildAndStaticFieldIsOneNode() {
        ExprNode instanceField = exprOf("n = this.count;").children().get(1);
        assertNode(instanceField, "field", ".count", "значение");
        assertNode(instanceField.children().get(0), "name", "this", "объект");

        ExprNode staticField = exprOf("n = Integer.MAX_VALUE;").children().get(1);
        assertNode(staticField, "field", "Integer.MAX_VALUE", "значение");
        assertTrue(staticField.children().isEmpty());
    }

    @Test
    void indexHasArrayAndIndex() {
        String stmt = "x = arr[i + 1];";
        ExprNode index = exprOf(stmt).children().get(1);

        assertNode(index, "index", "[ ]", "значение");
        assertRange(index, stmt, "arr[i + 1]");
        assertNode(index.children().get(0), "name", "arr", "массив");
        assertNode(index.children().get(1), "binary", "+", "индекс");
    }

    @Test
    void arrayInitializerListsElements() {
        ExprNode declare = exprOf("int[] a = {1, 2, 3};");
        ExprNode init = declare.children().get(0).children().get(0);

        assertNode(init, "arrayInit", "{ 3 элемента }", "инициализация");
        assertEquals(3, init.children().size());
        assertNode(init.children().get(2), "literal", "3", "элемент");
    }

    @Test
    void switchExpressionHasSelectorAndCases() {
        ExprNode declare = exprOf("int r = switch (k) { case 1, 2 -> 10; case 3 -> { yield 5; } default -> throw new X(); };");
        ExprNode choice = declare.children().get(0).children().get(0);

        assertNode(choice, "switchExpr", "switch", "инициализация");
        assertNode(choice.children().get(0), "name", "k", "селектор");
        ExprNode first = choice.children().get(1);
        assertNode(first, "other", "case 1, 2 ->", "case 1");
        assertNode(first.children().get(0), "literal", "10", "значение");
        ExprNode second = choice.children().get(2);
        assertNode(second, "other", "case 3 ->", "case 2");
        assertNode(second.children().get(0), "block", "{ 1 оператор }", "тело");
        assertRange(second.children().get(0), "int r = switch (k) { case 1, 2 -> 10; case 3 -> { yield 5; } default -> throw new X(); };", "{ yield 5; }");
        ExprNode last = choice.children().get(3);
        assertNode(last, "other", "default ->", "case 3");
        assertEquals("other", last.children().get(0).kind(), "throw в ветке — просто текст");
    }

    // ---- Объявления и заголовки ----

    @Test
    void declareHasVariablesWithInitializers() {
        String stmt = "int a = 1, b;";
        ExprNode declare = exprOf(stmt);

        assertNode(declare, "declare", "int", null);
        assertRange(declare, stmt, "int a = 1, b");
        assertEquals(2, declare.children().size());
        ExprNode a = declare.children().get(0);
        assertNode(a, "name", "a", "переменная");
        assertNode(a.children().get(0), "literal", "1", "инициализация");
        assertTrue(declare.children().get(1).children().isEmpty(), "у b нет инициализатора");
    }

    @Test
    void forHeaderHasInitConditionStep() {
        String stmt = "for (int i = 0; i < n; i++) { }";
        ExprNode header = exprOf(stmt);

        assertNode(header, "forHeader", "for", null);
        assertRange(header, stmt, "for (int i = 0; i < n; i++)");
        assertNode(header.children().get(0), "declare", "int", "инициализация");
        assertNode(header.children().get(1), "binary", "<", "условие");
        assertNode(header.children().get(2), "unary", "x++", "шаг");
    }

    @Test
    void forEachHeaderHasElementAndCollection() {
        String stmt = "for (String s : names) { }";
        ExprNode header = exprOf(stmt);

        assertNode(header, "forEachHeader", "for each", null);
        assertRange(header, stmt, "for (String s : names)");
        assertNode(header.children().get(0), "declare", "String", "элемент");
        assertNode(header.children().get(1), "name", "names", "коллекция");
    }

    @Test
    void tryResourcesAreListed() {
        String stmt = "try (Scanner in = new Scanner(System.in)) { }";
        ExprNode resources = exprOf(stmt);

        assertNode(resources, "resources", "ресурсы", null);
        assertRange(resources, stmt, "Scanner in = new Scanner(System.in)");
        ExprNode declare = resources.children().get(0);
        assertNode(declare, "declare", "Scanner", "ресурс");
        ExprNode created = declare.children().get(0).children().get(0);
        assertNode(created.children().get(0), "field", "System.in", "арг 1");
    }

    // ---- Остальное ----

    @Test
    void unknownExpressionBecomesOther() {
        ExprNode literal = exprOf("c = Foo.class;").children().get(1);

        assertNode(literal, "other", "Foo.class", "значение");
    }

    @Test
    void statementsWithoutExpressionHaveNone() {
        Blueprint blueprint = blueprintOf("while (true) { break; }");
        for (GraphNode node : blueprint.graph().nodes()) {
            if (node.kind() == NodeKind.BREAK) {
                assertNull(node.expr());
            } else {
                assertNotNull(node.expr(), node.label());
            }
        }
        assertNull(firstNode("return;").expr(), "у return; значения нет");
    }

    @Test
    void conditionsSelectorsAndValuesAreRoots() {
        assertNode(exprOf("while (i < n) { }"), "binary", "<", null);
        assertNode(exprOf("do { } while (more());"), "call", "more", null);
        assertNode(exprOf("switch (k) { default: break; }"), "name", "k", null);
        assertNode(exprOf("return a + b;"), "binary", "+", null);
        assertNode(exprOf("throw new IllegalStateException(\"x\");"), "new", "new IllegalStateException", null);
    }

    @Test
    void moreThanTwelveChildrenAreFolded() {
        String args = String.join(", ", java.util.stream.IntStream.rangeClosed(1, 14).mapToObj(String::valueOf).toList());
        ExprNode call = exprOf("foo(" + args + ");");

        assertEquals(13, call.children().size());
        assertNode(call.children().get(11), "literal", "12", "арг 12");
        ExprNode more = call.children().get(12);
        assertNode(more, "other", "ещё 2", null);
        assertEquals(call.id() + "/12", more.id());
    }

    @Test
    void codeIsCutTo200Characters() {
        ExprNode assign = exprOf("s = \"" + "a".repeat(300) + "\";");

        assertEquals(200, assign.code().length());
        assertTrue(assign.code().endsWith("…"));
    }

    @Test
    void idIsStatementIdPlusPath() {
        Blueprint blueprint = blueprintOf("r = f(a, b);");
        GraphNode node = blueprint.graph().nodes().get(0);

        assertEquals(node.id() + "/e", node.expr().id());
        assertEquals(node.id() + "/e/1/0", node.expr().children().get(1).children().get(0).id());
    }

    @Test
    void mapNodesHaveNoExprAndBlueprintJsonOmitsNullExpr() throws Exception {
        String source = Files.readString(MATCH_DEMO, StandardCharsets.UTF_8);
        CompilationUnit unit = SourceParser.parseOrThrow(source);

        JsonNode map = json.valueToTree(new ProjectMapBuilder().build(unit));
        map.get("nodes").forEach(n -> assertFalse(n.has("expr"), "на карте expr нет: " + n.get("id")));

        JsonNode blueprint = json.valueToTree(blueprintOfMatchDemo().view());
        boolean sawExpr = false;
        for (JsonNode n : blueprint.get("nodes")) {
            if (n.get("kind").asText().equals("return")) {
                sawExpr |= n.has("expr");
            }
            if (n.get("kind").asText().equals("break")) {
                assertFalse(n.has("expr"));
            }
        }
        assertTrue(sawExpr);
    }

    // ---- Настоящие монолиты ----

    @Test
    void matchDemoConditionIsCallWithTwoNameArguments() throws Exception {
        Blueprint blueprint = blueprintOfMatchDemo();
        GraphNode condition = blueprint.graph().nodes().stream()
                .filter(n -> n.label().equals("if (Objects.equals(x, y))")).findFirst().orElseThrow();
        ExprNode call = condition.expr();

        assertEquals(condition.id() + "/e", call.id());
        assertNode(call, "call", "Objects.equals", null);
        assertEquals("Objects.equals(x, y)", call.code());
        assertArrayEquals(new int[]{16, 21, 16, 40}, call.range());
        assertEquals(2, call.children().size());
        assertNode(call.children().get(0), "name", "x", "арг 1");
        assertNode(call.children().get(1), "name", "y", "арг 2");
        assertArrayEquals(new int[]{16, 36, 16, 36}, call.children().get(0).range());
        assertArrayEquals(new int[]{16, 39, 16, 39}, call.children().get(1).range());
    }

    @Test
    void everyMiniLangBlueprintBuildsAndExprTreesAreWellFormed() throws Exception {
        String source = Files.readString(MINILANG, StandardCharsets.UTF_8);
        CompilationUnit unit = SourceParser.parseOrThrow(source);
        SourceText text = new SourceText(source);
        Map<String, Node> declarations = new ProjectMapBuilder().declarations(unit);
        MethodBlueprintBuilder builder = new MethodBlueprintBuilder();

        int methods = 0;
        int exprNodes = 0;
        int largestJson = 0;
        String largestId = null;
        for (Map.Entry<String, Node> entry : declarations.entrySet()) {
            Blueprint blueprint = builder.build(entry.getKey(), entry.getKey(), entry.getValue(), text);
            methods++;
            Set<String> ids = new HashSet<>();
            for (GraphNode node : blueprint.graph().nodes()) {
                if (node.expr() != null) {
                    exprNodes += checkTree(node.expr(), node.id() + "/e", ids);
                }
            }
            int size = json.writeValueAsString(blueprint.view()).getBytes(StandardCharsets.UTF_8).length;
            if (size > largestJson) {
                largestJson = size;
                largestId = entry.getKey();
            }
        }
        System.out.println("MiniLang: методов " + methods + ", узлов выражений " + exprNodes
                + ", самый большой чертёж " + largestId + " — " + largestJson + " байт JSON");

        assertEquals(109, methods);
        assertTrue(exprNodes > 1000, "узлов выражений: " + exprNodes);
    }

    /** Проверяет дерево и возвращает число узлов в нём. */
    private int checkTree(ExprNode node, String expectedId, Set<String> ids) {
        assertEquals(expectedId, node.id());
        assertTrue(ids.add(node.id()), "повтор id " + node.id());
        assertNotNull(node.kind());
        assertFalse(node.label().isBlank(), "пустая подпись у " + node.id());
        assertTrue(node.code().length() <= 200);
        assertEquals(4, node.range().length);
        assertTrue(node.range()[0] >= 1 && node.range()[1] >= 1, "диапазон " + node.id());
        assertTrue(node.range()[2] > node.range()[0] || node.range()[3] >= node.range()[1], "диапазон " + node.id());
        assertTrue(node.children().size() <= 13);
        int count = 1;
        for (int i = 0; i < node.children().size(); i++) {
            count += checkTree(node.children().get(i), node.id() + "/" + i, ids);
        }
        return count;
    }

    // ---- Помощники ----

    private static void assertNode(ExprNode node, String kind, String label, String role) {
        assertEquals(kind, node.kind(), "kind у «" + node.code() + "»");
        assertEquals(label, node.label(), "label у «" + node.code() + "»");
        assertEquals(role, node.role(), "role у «" + node.code() + "»");
    }

    /** Диапазон узла совпадает с положением фрагмента в операторе, который стоит на 3-й строке с отступом 4. */
    private static void assertRange(ExprNode node, String statement, String fragment) {
        int from = statement.indexOf(fragment);
        assertTrue(from >= 0, "в операторе нет «" + fragment + "»");
        int[] expected = {3, 5 + from, 3, 5 + from + fragment.length() - 1};
        assertArrayEquals(expected, node.range(), "range у «" + node.code() + "»");
        assertEquals(fragment, node.code());
    }

    private static String wrap(String body) {
        return "class T {\n  void m() {\n    " + body + "\n  }\n}\n";
    }

    private ExprNode exprOf(String statement) {
        return firstNode(statement).expr();
    }

    private GraphNode firstNode(String statement) {
        return blueprintOf(statement).graph().nodes().get(0);
    }

    private Blueprint blueprintOf(String statement) {
        return blueprintFor(wrap(statement));
    }

    private ExprNode firstExpr(String source) {
        return blueprintFor(source).graph().nodes().get(0).expr();
    }

    private Blueprint blueprintFor(String source) {
        Map.Entry<String, Node> first = new ProjectMapBuilder().declarations(SourceParser.parseOrThrow(source))
                .entrySet().iterator().next();
        return new MethodBlueprintBuilder().build(first.getKey(), first.getKey(), first.getValue(), new SourceText(source));
    }

    private Blueprint blueprintOfMatchDemo() throws Exception {
        String source = Files.readString(MATCH_DEMO, StandardCharsets.UTF_8);
        Map<String, Node> declarations = new ProjectMapBuilder().declarations(SourceParser.parseOrThrow(source));
        String id = "M:MatchDemo#countMatches(List<Integer>,List<Integer>)";
        return new MethodBlueprintBuilder().build(id, id, declarations.get(id), new SourceText(source));
    }
}
