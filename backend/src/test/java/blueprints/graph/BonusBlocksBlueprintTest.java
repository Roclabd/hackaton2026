package blueprints.graph;

import blueprints.parse.SourceParser;
import blueprints.parse.SourceText;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.stmt.YieldStmt;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BonusBlocksBlueprintTest {
    @Test
    void lambdaBlockHasRealNestedControlFlowAndAstTargets() {
        Blueprint blueprint = blueprint("""
                Runnable r = () -> {
                    int n = 0;
                    while (n < 3) { if (n == 1) { n++; } else { n += 2; } }
                };
                r.run();
                """);
        GraphNode outer = blueprint.graph().nodes().getFirst();
        assertEquals(NodeKind.STMT, outer.kind());
        assertEquals(1, outer.branches().size());
        String branch = outer.branches().getFirst().key();
        assertTrue(branch.startsWith("lambda:"));
        GraphNode init = node(blueprint, "int n = 0;");
        GraphNode loop = blueprint.graph().nodes().stream().filter(n -> n.kind() == NodeKind.LOOP).findFirst().orElseThrow();
        assertEquals(outer.id(), init.parent());
        assertEquals(branch, init.branch());
        assertEquals(outer.id(), loop.parent());
        assertEquals(branch, loop.branch());
        assertTrue(blueprint.graph().edges().stream().anyMatch(e -> e.from().equals(init.id()) && e.to().equals(loop.id())));
        assertEquals(7, blueprint.graph().nodes().size());
        assertTrue(blueprint.graph().nodes().stream().allMatch(n -> blueprint.astById().containsKey(n.id())));
    }

    @Test
    void switchBlocksExposeYieldAndKeepExpressionArmsInExpressionTree() {
        Blueprint blueprint = blueprint("""
                int r = switch (k) {
                    case 0 -> 10;
                    case 1 -> { int a = 2; if (a > 1) { yield a; } yield 0; }
                    default -> throw new IllegalArgumentException();
                };
                """);
        GraphNode outer = blueprint.graph().nodes().getFirst();
        assertEquals(2, outer.branches().size());
        assertTrue(outer.branches().getFirst().label().contains("case 1 ->"));
        GraphNode yield = node(blueprint, "yield a;");
        assertEquals(NodeKind.STMT, yield.kind());
        assertTrue(yield.editable());
        assertInstanceOf(YieldStmt.class, blueprint.astById().get(yield.id()));
        assertEquals("a", yield.expr().code());
        assertFalse(blueprint.graph().nodes().stream().anyMatch(n -> "10;".equals(n.code())));
        assertEquals(6, blueprint.graph().nodes().size());
    }

    @Test
    void repeatedAndNestedLambdasStayInDistinctBranchesWithoutDuplicates() {
        Blueprint blueprint = blueprint("""
                use(() -> { a(); }, () -> { b(); Runnable r = () -> { c(); }; });
                """);
        GraphNode outer = blueprint.graph().nodes().getFirst();
        assertEquals(2, outer.branches().size());
        assertNotEquals(outer.branches().get(0).key(), outer.branches().get(1).key());
        assertEquals(5, blueprint.graph().nodes().size());
        assertEquals(5, new HashSet<>(blueprint.graph().nodes().stream().map(GraphNode::id).toList()).size());
        assertEquals(node(blueprint, "Runnable r = () -> { c(); };").id(), node(blueprint, "c();").parent());
    }

    @Test
    void switchStatementGroupsAndNestedExpressionArmBlocksAreFound() {
        Blueprint grouped = blueprint("int r = switch (k) { case 1: int a = 2; yield a; default: yield 0; };");
        assertEquals(2, grouped.graph().nodes().getFirst().branches().size());
        assertEquals(4, grouped.graph().nodes().size());
        Blueprint nested = blueprint("Object r = switch (k) { default -> (Runnable) () -> { a(); }; };");
        assertEquals(2, nested.graph().nodes().size());
        assertTrue(nested.graph().nodes().getFirst().branches().getFirst().key().startsWith("lambda:"));
    }

    @Test
    void embeddedHeaderBlocksAreDistinctFromOrdinaryControlBranches() {
        Blueprint blueprint = blueprint("if (test(() -> { a(); })) { b(); }");
        GraphNode condition = blueprint.graph().nodes().getFirst();
        assertEquals("then", condition.branches().getFirst().key());
        assertEquals(2, condition.branches().size());
        assertEquals("then", node(blueprint, "b();").branch());
        assertTrue(node(blueprint, "a();").branch().startsWith("lambda:"));
        assertEquals(condition.id(), node(blueprint, "a();").parent());
    }

    @Test
    void lambdaBlockInPatternGuardIsNotHiddenByExpressionArm() {
        Blueprint blueprint = blueprint("int r = switch (o) { case String s when test(() -> { a(); }) -> 1; default -> 0; };");
        assertEquals(2, blueprint.graph().nodes().size());
        assertTrue(node(blueprint, "a();").branch().startsWith("lambda:"));
    }

    @Test
    void ordinaryBodiesAndClassBodiesAreNotVisitedTwice() {
        Blueprint blueprint = blueprint("""
                if (ok) { Runnable r = () -> { a(); }; }
                Object o = new Object() { void hidden() { b(); } };
                class Local { void hidden() { c(); } }
                """);
        assertEquals(5, blueprint.graph().nodes().size());
        assertEquals(1, blueprint.graph().nodes().stream().filter(n -> "a();".equals(n.code())).count());
        assertFalse(blueprint.graph().nodes().stream().anyMatch(n -> List.of("b();", "c();").contains(n.code())));
    }

    @Test
    void existingStatementIdsAndInsertedSiblingIdsRemainStable() {
        Blueprint before = blueprint("Runnable r = () -> { a(); b(); }; after();");
        Blueprint after = blueprint("before(); Runnable r = () -> { a(); b(); }; after();");
        before.graph().nodes().forEach(n -> assertEquals(n.id(), node(after, n.code()).id()));
        String outerCode = "Runnable r = () -> { a(); b(); };";
        assertEquals("M:T#m()/body.stmt:" + Integer.toHexString(outerCode.hashCode()), node(before, outerCode).id());
    }

    @Test
    void compactConstructorBodyBuildsAsNormalStatements() {
        String source = "record T(int n) { T { if (n < 0) throw new IllegalArgumentException(); } }";
        Node declaration = SourceParser.parseOrThrow(source).findFirst(CompactConstructorDeclaration.class).orElseThrow();
        Blueprint blueprint = new MethodBlueprintBuilder().build("M:T#<init>(int)", "T", declaration, new SourceText(source));
        assertEquals(2, blueprint.graph().nodes().size());
        assertEquals(NodeKind.IF, blueprint.graph().nodes().getFirst().kind());
    }

    @Test
    void scopeBonusSwitchBlockOperatorsBecomeEditable() throws Exception {
        String source = Files.readString(Path.of("../corpus/scope/Scope5Bonus.java"));
        var declarations = new ProjectMapBuilder().declarations(SourceParser.parseOrThrow(source));
        List<GraphNode> all = declarations.entrySet().stream()
                .flatMap(e -> new MethodBlueprintBuilder().build(e.getKey(), e.getKey(), e.getValue(), new SourceText(source))
                        .graph().nodes().stream()).toList();
        GraphNode yield = all.stream().filter(n -> "yield tmp + \"dle\";".equals(n.code())).findFirst().orElseThrow();
        assertTrue(yield.editable());
        assertEquals("tmp + \"dle\"", yield.expr().code());
        assertTrue(all.stream().anyMatch(n -> "String tmp = \"mid\";".equals(n.code())));
    }

    private static Blueprint blueprint(String body) {
        String source = "class T { void m() { " + body + " } }";
        var unit = SourceParser.parseOrThrow(source);
        return new MethodBlueprintBuilder().build("M:T#m()", "m", unit.getType(0).getMethodsByName("m").getFirst(), new SourceText(source));
    }

    private static GraphNode node(Blueprint blueprint, String code) {
        return blueprint.graph().nodes().stream().filter(n -> code.equals(n.code())).findFirst().orElseThrow();
    }
}
