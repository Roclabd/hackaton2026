package blueprints.graph;

import blueprints.commands.EditStatementCommand;
import blueprints.heat.HeatService;
import blueprints.parse.SourceParser;
import blueprints.project.Blueprints;
import blueprints.project.ProjectStore;
import blueprints.runner.InMemoryCompiler;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class BonusMapTest {
    private final ProjectMapBuilder builder = new ProjectMapBuilder();

    @Test
    void corpusExposesCompactConstructorAndConstantMethodsWithRealDeclarations() throws Exception {
        var unit = SourceParser.parseOrThrow(Files.readString(Path.of("../corpus/scope/Scope5Bonus.java")));
        Graph map = builder.build(unit);
        assertEquals(42, map.nodes().stream().filter(n -> n.kind() == NodeKind.METHOD).count());
        var declarations = builder.declarations(unit);
        assertInstanceOf(CompactConstructorDeclaration.class, declarations.get("M:Scope5Bonus.Pair#<init>(String,int)"));
        for (String constant : Set.of("ADD", "MUL")) {
            String owner = "Scope5Bonus.Op.<constant:" + constant + ">";
            assertInstanceOf(MethodDeclaration.class, declarations.get("M:" + owner + "#apply(int,int)"));
            assertEquals("C:Scope5Bonus.Op", node(map, "C:" + owner).parent());
            assertEquals("C:" + owner, node(map, "M:" + owner + "#apply(int,int)").parent());
        }
        assertNotNull(declarations.get("M:Scope5Bonus.Op#apply(int,int)"));
    }

    @Test
    void constantOwnerDoesNotCollideWithExistingNestedTypeOrMethodIds() {
        String source = "enum E { ADD { int foo(){return 1;} }; static class ADD { int foo(){return 2;} } }";
        assertTrue(new InMemoryCompiler().compile("E", source).ok());
        var unit = SourceParser.parseOrThrow(source);
        Graph map = builder.build(unit);
        assertEquals("class ADD", node(map, "C:E.ADD").label());
        assertEquals("C:E.ADD", node(map, "M:E.ADD#foo()").parent());
        assertEquals("константа ADD", node(map, "C:E.<constant:ADD>").label());
        assertEquals("C:E.<constant:ADD>", node(map, "M:E.<constant:ADD>#foo()").parent());
        assertEquals(map.nodes().size(), map.nodes().stream().map(GraphNode::id).distinct().count());
    }

    @Test
    void compactSignatureAndCallsUseRecordComponentsIncludingVarargs() {
        var unit = SourceParser.parseOrThrow("""
                class Helper { void ping(){} }
                record R(Helper helper, String... names) {
                    R { helper.ping(); check(); }
                    static void check(){}
                    static R make(){ return new R(new Helper(), "a", "b"); }
                }
                """);
        Graph map = builder.build(unit);
        String compact = "M:R#<init>(Helper,String...)";
        assertEquals(Set.of("M:Helper#ping()", "M:R#check()"), targets(map, compact));
        assertEquals(Set.of(compact), targets(map, "M:R#make()"));
        Graph shifted = builder.build(SourceParser.parseOrThrow("\n\n" + unit));
        assertEquals(ids(map), ids(shifted));
    }

    @Test
    void enumConstantMethodResolvesOwnAndEnumHelpersWithoutChangingDispatch() {
        Graph map = builder.build(SourceParser.parseOrThrow("""
                enum E {
                    A { int f(){ return own() + common(); } int own(){return 1;} },
                    B { int f(){ return 2; } };
                    abstract int f();
                    int common(){return 3;}
                    static int run(E e){return e.f();}
                }
                """));
        assertEquals(Set.of("M:E.<constant:A>#own()", "M:E#common()"), targets(map, "M:E.<constant:A>#f()"));
        assertEquals(Set.of("M:E#f()"), targets(map, "M:E#run(E)"));
    }

    @Test
    void complexityUsesCompactParametersAndKeepsConstantBodiesSeparate() {
        Graph map = builder.build(SourceParser.parseOrThrow("""
                import java.util.List;
                record R(List<String> items) { R { items.contains("x"); } }
                enum E {
                    A { int f(int n){return work(n);} int work(int n){for(int i=0;i<n;i++){} return n;} },
                    B { int f(int n){return work(n);} int work(int n){return n;} };
                    abstract int f(int n);
                    static int plain(E value){return value.f(10);}
                }
                """));
        assertEquals("O(n)", node(map, "M:R#<init>(List<String>)").complexity().big());
        assertEquals("O(n)", node(map, "M:E.<constant:A>#f(int)").complexity().big());
        assertEquals("O(1)", node(map, "M:E.<constant:B>#f(int)").complexity().big());
        assertEquals("O(1)", node(map, "M:E#plain(E)").complexity().big());
    }

    @Test
    void newDeclarationsSupportBlueprintEditAndMeasuredHeat() {
        String source = """
                class Probe {
                    record R(int value) { R { value += 1; } }
                    enum E { A { int value(){ return 3; } }; abstract int value(); }
                    public static void main(String[] args) { new R(1); E.A.value(); }
                }
                """;
        for (String method : Set.of("M:Probe.R#<init>(int)", "M:Probe.E.<constant:A>#value()")) {
            ProjectStore store = new ProjectStore();
            var project = store.create("Probe.java", source);
            Blueprint blueprint = new Blueprints().forMethod(project, method).orElseThrow();
            assertEquals(1, blueprint.graph().nodes().size());
            GraphNode statement = blueprint.graph().nodes().getFirst();
            assertTrue(statement.editable());
            assertNotNull(blueprint.astById().get(statement.id()));
            assertEquals(1L, new HeatService().measure(project, method, "").counts().get(statement.id()));
            String replacement = method.contains("<init>") ? "value += 2;" : "return 4;";
            var edited = new EditStatementCommand(store, new InMemoryCompiler())
                    .apply(project.id(), statement.id(), replacement, project.version());
            assertTrue(edited.ok(), edited.compileErrors().toString());
            assertNotNull(new Blueprints().forMethod(edited.project(), method).orElseThrow());
            assertEquals(1L, new HeatService().measure(edited.project(), method, "").maxCount());
        }
    }

    private static GraphNode node(Graph map, String id) {
        return map.nodes().stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
    }

    private static Set<String> targets(Graph map, String from) {
        return map.edges().stream().filter(e -> e.from().equals(from)).map(GraphEdge::to).collect(Collectors.toSet());
    }

    private static Set<String> ids(Graph map) {
        return map.nodes().stream().map(GraphNode::id).collect(Collectors.toSet());
    }
}
