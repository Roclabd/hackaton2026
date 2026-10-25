package blueprints.scope;

import blueprints.App;
import blueprints.commands.EditResult;
import blueprints.commands.EditStatementCommand;
import blueprints.gate.GateService;
import blueprints.gate.Intent;
import blueprints.gate.Verdict;
import blueprints.gate.VerdictStatus;
import blueprints.graph.Blueprint;
import blueprints.graph.GraphNode;
import blueprints.graph.NodeKind;
import blueprints.project.Blueprints;
import blueprints.project.Project;
import blueprints.project.ProjectStore;
import blueprints.runner.InMemoryCompiler;
import blueprints.runner.RunResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.LabeledStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Корпус «обязательный набор конструкций Java» (docs/ТРЕБОВАНИЯ_JAVA.md): пять монолитов в corpus/scope/.
 * Тест держится зелёным при известных пробелах инструмента: проверяет «не падает», «запускается как java»,
 * «проверка самого с собой не расходится», «правка узла проходит». Всё, что инструмент не показывает или не
 * правит, печатается строками «[scope] …» — из них собрана матрица docs/SCOPE.md.
 * Выгрузка чертежей в текст: -Dscope.dump=<папка>.
 */
class ScopeCorpusTest {

    private static final Path DIR = Path.of("../corpus/scope");
    private static final List<String> REPORT = Collections.synchronizedList(new ArrayList<>());

    private static GateService gate;

    @BeforeAll
    static void setUp() {
        gate = new GateService();
    }

    @AfterAll
    static void tearDown() throws Exception {
        gate.close();
        List<String> lines = REPORT.stream().sorted().toList();
        lines.forEach(System.out::println);
        String file = System.getProperty("scope.report");
        if (file != null && !file.isBlank()) {
            Files.write(Path.of(file), lines, StandardCharsets.UTF_8);
        }
    }

    // ---------- 1. разбор и карта ----------

    @ParameterizedTest
    @ValueSource(strings = {"Scope1Lang", "Scope2Console", "Scope3Collections", "Scope4Exceptions", "Scope5Bonus"})
    void parsesAndMapHasAllClassesAndMethods(String name) throws Exception {
        Project project = load(name);
        assertTrue(project.errors().isEmpty(), "разбор без ошибок: " + project.errors());
        CompilationUnit unit = project.lastGood().orElseThrow();

        List<String> expectedTypes = java.util.stream.Stream.concat(
                unit.findAll(TypeDeclaration.class).stream().filter(t -> !insideBody(t)).map(t -> t.getNameAsString()),
                unit.findAll(EnumConstantDeclaration.class).stream().filter(c -> !insideBody(c))
                        .filter(c -> !c.getClassBody().isEmpty()).map(EnumConstantDeclaration::getNameAsString))
                .sorted().toList();
        List<String> mapTypes = project.map().nodes().stream().filter(n -> n.kind() == NodeKind.CLASS)
                .map(n -> n.label().substring(n.label().lastIndexOf(' ') + 1)).sorted().toList();
        assertEquals(expectedTypes, mapTypes, "все объявленные типы есть на карте");

        long expectedCallables = unit.findAll(CallableDeclaration.class).stream()
                .filter(c -> c instanceof MethodDeclaration || c instanceof ConstructorDeclaration)
                .filter(c -> !insideBody(c)).count()
                + unit.findAll(InitializerDeclaration.class).stream().filter(i -> !insideBody(i)).count()
                + unit.findAll(CompactConstructorDeclaration.class).stream().filter(c -> !insideBody(c)).count();
        long mapMethods = project.map().nodes().stream().filter(n -> n.kind() == NodeKind.METHOD).count();
        assertEquals(expectedCallables, mapMethods, "все методы, конструкторы и блоки инициализации есть на карте");

        long hiddenCallables = unit.findAll(MethodDeclaration.class).stream().filter(ScopeCorpusTest::insideBody).count();
        long compact = unit.findAll(CompactConstructorDeclaration.class).stream().filter(c -> !insideBody(c)).count();
        long enumConstants = unit.findAll(EnumConstantDeclaration.class).size();
        long enumConstantBodies = unit.findAll(EnumConstantDeclaration.class).stream()
                .filter(c -> !insideBody(c) && !c.getClassBody().isEmpty()).count();
        long edges = project.map().edges().size();
        REPORT.add(String.format("[scope] карта %s: типов=%d, методов=%d, полей=%d, рёбер вызовов=%d; "
                        + "не на карте: методов внутри анонимных/локальных классов=%d; "
                        + "на карте компактных конструкторов record=%d, тел констант enum=%d; всего констант enum в AST=%d",
                name, mapTypes.size(), mapMethods,
                project.map().nodes().stream().filter(n -> n.kind() == NodeKind.FIELD).count(), edges,
                hiddenCallables, compact, enumConstantBodies, enumConstants));
    }

    // ---------- 2. чертёж каждого метода ----------

    @ParameterizedTest
    @ValueSource(strings = {"Scope1Lang", "Scope2Console", "Scope3Collections", "Scope4Exceptions", "Scope5Bonus"})
    void blueprintForEveryMethod(String name) throws Exception {
        Project project = load(name);
        Blueprints blueprints = new Blueprints();
        Map<NodeKind, Integer> kinds = new EnumMap<>(NodeKind.class);
        List<String> raws = new ArrayList<>();
        int methods = 0;
        int[] notDrawn = new int[3];
        List<String> notDrawnSamples = new ArrayList<>();
        Map<String, Node> declarations = new blueprints.graph.ProjectMapBuilder().declarations(project.lastGood().orElseThrow());
        StringBuilder dump = new StringBuilder();
        for (GraphNode method : project.map().nodes()) {
            if (method.kind() != NodeKind.METHOD) {
                continue;
            }
            methods++;
            Optional<Blueprint> maybe = blueprints.forMethod(project, method.id());
            assertTrue(maybe.isPresent(), "чертёж построен: " + method.id());
            Blueprint blueprint = maybe.get();
            Set<String> ids = blueprint.graph().nodes().stream().map(GraphNode::id).collect(Collectors.toSet());
            assertEquals(blueprint.graph().nodes().size(), ids.size(), "id узлов уникальны в " + method.id());
            for (GraphNode node : blueprint.graph().nodes()) {
                kinds.merge(node.kind(), 1, Integer::sum);
                if (node.kind() == NodeKind.RAW) {
                    raws.add(method.label() + ": " + node.label());
                }
                assertTrue(node.parent().equals(method.id()) || ids.contains(node.parent()), "родитель есть: " + node.id());
            }
            int[] undrawn = undrawnStatements(declarations.get(method.id()), blueprint, notDrawnSamples);
            for (int i = 0; i < 3; i++) {
                notDrawn[i] += undrawn[i];
            }
            appendDump(dump, method, blueprint);
        }
        dumpIfAsked(name, dump);
        assertTrue(methods > 0);
        String kindText = kinds.entrySet().stream().map(e -> e.getKey().wire() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        REPORT.add(String.format("[scope] чертёж %s: методов=%d; узлов по видам: %s; серых raw=%d; "
                + "операторов потеряно=%d %s; без отдельных узлов в лямбдах/анонимных/локальных классах=%d; "
                + "без отдельных узлов в ветках switch-выражений=%d", name, methods, kindText, kinds.getOrDefault(NodeKind.RAW, 0),
                notDrawn[0], notDrawnSamples.stream().limit(5).toList(), notDrawn[1], notDrawn[2]));
        raws.forEach(r -> REPORT.add("[scope] raw " + name + " | " + r));
    }

    // ---------- 3. запуск как обычный java ----------

    @ParameterizedTest
    @ValueSource(strings = {"Scope1Lang", "Scope2Console", "Scope3Collections", "Scope4Exceptions", "Scope5Bonus"})
    void runOnceMatchesPlainJava(String name) throws Exception {
        String source = read(name + ".java");
        RunResult result = gate.runOnce(source, read("input/" + name + ".txt"));

        assertFalse(result.timedOut(), "не уложились во время");
        assertNull(result.exception(), "нет необработанного исключения: " + result.exception());
        assertEquals(0, result.exitCode());
        assertEquals(read("expected/" + name + ".txt"), unix(result.stdout()), "stdout как у обычного java");
        Path errFile = DIR.resolve("expected/" + name + ".err.txt");
        String expectedErr = Files.exists(errFile) ? Files.readString(errFile, StandardCharsets.UTF_8) : "";
        boolean sameErr = expectedErr.equals(unix(result.stderr()));
        assertEquals(expectedErr, unix(result.stderr()), "stderr как у обычного java");
        REPORT.add("[scope] запуск " + name + ": stdout совпал с java=true, exit=0, stderr совпал с java=" + sameErr
                + ", длительность=" + result.durationMs() + " мс");
    }

    // ---------- 4. проверка кода самого с собой ----------

    @ParameterizedTest
    @ValueSource(strings = {"Scope1Lang", "Scope2Console", "Scope3Collections", "Scope4Exceptions", "Scope5Bonus"})
    void gateOfSourceAgainstItself(String name) throws Exception {
        Project project = load(name);
        Verdict verdict = await(gate.start(project, project.source(), Intent.REFACTOR));

        assertNotEquals(VerdictStatus.BUILD_ERROR, verdict.status(), String.valueOf(verdict.buildErrors()));
        assertNotEquals(VerdictStatus.MISMATCH, verdict.status(), "код не может расходиться сам с собой");
        assertNotEquals(VerdictStatus.BEHAVIOR_CHANGE, verdict.status());
        if (verdict.status() == VerdictStatus.INCOMPLETE) {
            assertNotNull(verdict.incompleteReason(), "честный INCOMPLETE называет причину");
        }
        REPORT.add(String.format("[scope] проверка %s: %s, входов=%d, совпало=%d, расхождений=%d, случайных=%d, причина=%s, %d мс",
                name, verdict.status(), verdict.inputs(), verdict.matched(), verdict.mismatches(),
                verdict.nondeterministic(), verdict.incompleteReason(), verdict.durationMs()));
    }

    // ---------- 5. правка узла ----------

    /** Правка, которую пробуем на первом подходящем узле файла. */
    private record EditCase(String label, Predicate<GraphNode> picks, Function<GraphNode, String> newText, boolean shouldWork) {}

    private static final List<EditCase> EDIT_CASES = List.of(
            new EditCase("stmt: вызов println → println(\"edited\")",
                    n -> n.kind() == NodeKind.STMT && n.code().startsWith("System.out.println("),
                    n -> "System.out.println(\"edited\");", true),
            new EditCase("stmt: объявление переменной (тот же текст)",
                    n -> n.kind() == NodeKind.STMT && n.code().matches("(?s)(final )?(int|long|double|String|boolean|var|List<[^>]*>|Map<[^>]*>) \\w+ = .*;"),
                    n -> n.code(), true),
            new EditCase("stmt: присваивание или инкремент (тот же текст)",
                    n -> n.kind() == NodeKind.STMT && n.code().matches("(?s)\\w+(\\[[^\\]]*\\])? ?([-+*/%]=|=|\\+\\+|--)[^=].*;|\\w+(\\+\\+|--);"),
                    n -> n.code(), true),
            new EditCase("if: условие → (условие)", n -> n.kind() == NodeKind.IF, n -> "(" + n.code() + ")", true),
            new EditCase("while/do: условие → (условие)",
                    n -> n.kind() == NodeKind.LOOP && n.editable(), n -> "(" + n.code() + ")", true),
            new EditCase("return: return (выражение)",
                    n -> n.kind() == NodeKind.RETURN && n.code().startsWith("return ") && n.code().length() > 8,
                    n -> "return (" + n.code().substring(7, n.code().length() - 1) + ");", true),
            new EditCase("throw: тот же текст", n -> n.kind() == NodeKind.THROW, n -> n.code(), true),
            new EditCase("for / for-each: заголовок цикла",
                    n -> n.kind() == NodeKind.LOOP && !n.editable(), n -> n.label(), false),
            new EditCase("switch", n -> n.kind() == NodeKind.SWITCH, n -> n.label(), false),
            new EditCase("try", n -> n.kind() == NodeKind.TRY, n -> n.label(), false),
            new EditCase("break", n -> n.kind() == NodeKind.BREAK, n -> n.code(), false),
            new EditCase("continue", n -> n.kind() == NodeKind.CONTINUE, n -> n.code(), false),
            new EditCase("raw (серый узел)", n -> n.kind() == NodeKind.RAW, n -> n.code(), false));

    @ParameterizedTest
    @ValueSource(strings = {"Scope1Lang", "Scope2Console", "Scope3Collections", "Scope4Exceptions", "Scope5Bonus"})
    void editingNodesWorksWhereTheyAreEditable(String name) throws Exception {
        ProjectStore store = new ProjectStore();
        EditStatementCommand command = new EditStatementCommand(store, new InMemoryCompiler());
        Blueprints blueprints = new Blueprints();
        String source = read(name + ".java");
        Project base = store.create(name + ".java", source);
        int editedOk = 0;

        for (EditCase editCase : EDIT_CASES) {
            Optional<Found> found = firstNode(base, blueprints, editCase.picks());
            if (found.isEmpty()) {
                REPORT.add("[scope] правка " + name + " | " + editCase.label() + " | в файле нет такого узла");
                continue;
            }
            Project fresh = store.create(name + ".java", source);
            GraphNode node = found.get().node();
            EditResult result = command.apply(fresh.id(), node.id(), editCase.newText().apply(node), fresh.version());
            String detail = result.ok() ? "принята" : "отклонена: " + result.compileErrors().getFirst().text();
            REPORT.add("[scope] правка " + name + " | " + editCase.label() + " | узел «" + shorten(node.label()) + "» | " + detail);
            if (editCase.shouldWork()) {
                assertTrue(result.ok(), editCase.label() + " → " + detail);
                editedOk++;
            } else {
                assertFalse(result.ok(), editCase.label() + " должна быть отклонена");
                assertTrue(result.compileErrors().getFirst().text().contains("нельзя править"), detail);
            }
        }
        assertTrue(editedOk > 0, "в каждом файле хотя бы один узел правится");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Scope1Lang"})
    void editThroughHttpCommandsRoute(String name) throws Exception {
        ObjectMapper json = new ObjectMapper();
        HttpClient http = HttpClient.newHttpClient();
        Javalin app = App.create(new ProjectStore()).start(0);
        try {
            String source = read(name + ".java");
            JsonNode project = json.readTree(send(http, json, app, "POST", "/api/projects",
                    Map.of("fileName", name + ".java", "source", source)).body());
            String projectId = project.get("id").asText();
            String mainId = null;
            for (JsonNode n : project.get("map").get("nodes")) {
                if (n.get("kind").asText().equals("method") && n.get("label").asText().equals("static void main(String[])")) {
                    mainId = n.get("id").asText();
                }
            }
            assertNotNull(mainId, "main на карте");
            HttpResponse<String> blueprintResponse = send(http, json, app, "GET", "/api/projects/" + projectId + "/methods/"
                    + URLEncoder.encode(mainId, StandardCharsets.UTF_8).replace("+", "%20"), null);
            assertEquals(200, blueprintResponse.statusCode(), blueprintResponse.body());
            String printId = null;
            for (JsonNode n : json.readTree(blueprintResponse.body()).get("nodes")) {
                if (n.get("code").asText().startsWith("System.out.println(GREETING")) {
                    printId = n.get("id").asText();
                }
            }
            assertNotNull(printId, "строка приветствия на чертеже main");
            JsonNode edited = json.readTree(send(http, json, app, "POST", "/api/projects/" + projectId + "/commands",
                    Map.of("type", "editStatement", "nodeId", printId, "text", "System.out.println(\"Hello\");",
                            "baseVersion", 1)).body());
            assertTrue(edited.get("ok").asBoolean(), edited.toString());
            assertTrue(edited.get("source").asText().contains("System.out.println(\"Hello\");"));
            assertEquals(2, edited.get("version").asInt());
        } finally {
            app.stop();
        }
    }

    // ---------- помощники: дерево разбора ----------

    /** Объявления внутри исполняемого кода и анонимных классов на карту не выносятся; тело константы enum выносится. */
    private static boolean insideBody(Node node) {
        Optional<Node> parent = node.getParentNode();
        while (parent.isPresent()) {
            Node p = parent.get();
            if (p instanceof MethodDeclaration || p instanceof ConstructorDeclaration || p instanceof InitializerDeclaration
                    || p instanceof CompactConstructorDeclaration || p instanceof ObjectCreationExpr
                    || p instanceof LambdaExpr) {
                return true;
            }
            parent = p.getParentNode();
        }
        return false;
    }

    /**
     * Операторы тела метода, которых нет на чертеже отдельным узлом: [0] потеряны совсем (ни узла, ни условия
     * контейнера); [1] лежат в лямбде, анонимном или локальном классе — чертёж метода их не раскрывает;
     * [2] лежат в ветках switch-выражения — это часть выражения, видна текстом внутри оператора.
     */
    private static int[] undrawnStatements(Node declaration, Blueprint blueprint, List<String> samples) {
        int[] counts = new int[3];
        if (declaration == null) {
            return counts;
        }
        java.util.Set<Node> targets = Collections.newSetFromMap(new IdentityHashMap<>());
        targets.addAll(blueprint.astById().values());
        for (Statement s : declaration.findAll(Statement.class)) {
            if (s instanceof BlockStmt || s instanceof EmptyStmt || s instanceof LabeledStmt) {
                continue;
            }
            boolean drawn = targets.contains(s)
                    || (s instanceof IfStmt i && targets.contains(i.getCondition()))
                    || (s instanceof WhileStmt w && targets.contains(w.getCondition()))
                    || (s instanceof DoStmt d && targets.contains(d.getCondition()));
            if (!drawn) {
                int hidden = hiddenCategory(s, declaration);
                if (hidden > 0) {
                    counts[hidden]++;
                    continue;
                }
                counts[0]++;
                samples.add(shorten(s.toString().replaceAll("\s+", " ")));
            }
        }
        return counts;
    }

    /** 0 — оператор самого метода; 1 — в лямбде, анонимном или локальном классе; 2 — в switch-выражении. */
    private static int hiddenCategory(Statement statement, Node declaration) {
        Optional<Node> parent = statement.getParentNode();
        while (parent.isPresent() && parent.get() != declaration) {
            Node p = parent.get();
            if (p instanceof SwitchExpr) {
                return 2;
            }
            if (p instanceof LambdaExpr || p instanceof ObjectCreationExpr || p instanceof MethodDeclaration
                    || p instanceof com.github.javaparser.ast.stmt.LocalClassDeclarationStmt
                    || p instanceof com.github.javaparser.ast.stmt.LocalRecordDeclarationStmt) {
                return 1;
            }
            parent = p.getParentNode();
        }
        return 0;
    }

    // ---------- помощники: чертёж ----------

    private record Found(GraphNode node, String methodId) {}

    private static Optional<Found> firstNode(Project project, Blueprints blueprints, Predicate<GraphNode> picks) {
        for (GraphNode method : project.map().nodes()) {
            if (method.kind() != NodeKind.METHOD) {
                continue;
            }
            Optional<Blueprint> blueprint = blueprints.forMethod(project, method.id());
            if (blueprint.isEmpty()) {
                continue;
            }
            for (GraphNode node : blueprint.get().graph().nodes()) {
                if (picks.test(node)) {
                    return Optional.of(new Found(node, method.id()));
                }
            }
        }
        return Optional.empty();
    }

    private static void appendDump(StringBuilder dump, GraphNode method, Blueprint blueprint) {
        dump.append("== ").append(method.label()).append("  ").append(method.id()).append('\n');
        Map<String, Integer> depth = new LinkedHashMap<>();
        depth.put(method.id(), 0);
        for (GraphNode node : blueprint.graph().nodes()) {
            int d = depth.getOrDefault(node.parent(), 0) + 1;
            depth.put(node.id(), d);
            dump.append("  ".repeat(d)).append(String.format("%-8s", node.kind().wire()))
                    .append(node.editable() ? "[ред] " : "[---] ")
                    .append(node.branch() == null ? "" : "<" + node.branch() + "> ")
                    .append("L").append(node.lines()[0]).append(' ').append(node.label())
                    .append(node.branches() == null ? "" : "   ветки: " + node.branches().stream()
                            .map(b -> b.key() + "=«" + b.label() + "»").collect(Collectors.joining(", ")))
                    .append('\n');
        }
    }

    private static void dumpIfAsked(String name, StringBuilder dump) throws Exception {
        String dir = System.getProperty("scope.dump");
        if (dir != null && !dir.isBlank()) {
            Files.createDirectories(Path.of(dir));
            Files.writeString(Path.of(dir, name + ".txt"), dump.toString(), StandardCharsets.UTF_8);
        }
    }

    // ---------- помощники: файлы, проверка, HTTP ----------

    private static Project load(String name) throws Exception {
        return new ProjectStore().create(name + ".java", read(name + ".java"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(DIR.resolve(relative), StandardCharsets.UTF_8);
    }

    private static String unix(String text) {
        return text.replace("\r\n", "\n");
    }

    private static String shorten(String text) {
        String one = text.replaceAll("\\s+", " ").strip();
        return one.length() <= 60 ? one : one.substring(0, 59) + "…";
    }

    private static Verdict await(String jobId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 180_000;
        while (System.currentTimeMillis() < deadline) {
            Verdict verdict = gate.job(jobId).orElseThrow();
            if (verdict.status() != VerdictStatus.RUNNING) {
                return verdict;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("проверка не закончилась за 3 минуты");
    }

    private static HttpResponse<String> send(HttpClient http, ObjectMapper json, Javalin app, String method, String path,
                                             Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path));
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8));
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
