package blueprints.commands;

import blueprints.graph.Blueprint;
import blueprints.graph.GraphNode;
import blueprints.heat.HeatService;
import blueprints.project.Blueprints;
import blueprints.project.Project;
import blueprints.project.ProjectStore;
import blueprints.runner.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BonusBlocksCommandTest {
    private static final String SOURCE = """
            public class BonusEdit {
                static int value(int k) {
                    java.util.function.IntSupplier f = () -> {
                        int a = 1;
                        a += 2;
                        return a;
                    };
                    return switch (k) {
                        case 1 -> { int n = f.getAsInt(); yield n + 1; }
                        default -> { yield 0; }
                    };
                }
                public static void main(String[] args) { System.out.print(value(1)); }
            }
            """;
    private static final String METHOD = "M:BonusEdit#value(int)";
    private final ProjectStore store = new ProjectStore();
    private final InMemoryCompiler compiler = new InMemoryCompiler();
    private final EditStatementCommand command = new EditStatementCommand(store, compiler);
    private final Blueprints blueprints = new Blueprints();

    @Test
    void lambdaStatementEditPreservesSourceAndObservedBehavior() {
        Project project = store.create("BonusEdit.java", SOURCE);
        EditResult result = edit(project, "a += 2;", "a = a + 2;");
        assertTrue(result.ok(), result.compileErrors().toString());
        assertEquals(SOURCE.replace("a += 2;", "a = a + 2;"), result.project().source());
        assertEquals(run(SOURCE), run(result.project().source()));
        assertEquals(node(result.blueprint(), "a = a + 2;").id(), result.idMap().get(node(blueprints.forMethod(project, METHOD).orElseThrow(), "a += 2;").id()));
    }

    @Test
    void yieldEditUsesItsAstRangeAndChangesExecutedSource() {
        Project project = store.create("BonusEdit.java", SOURCE);
        EditResult result = edit(project, "yield n + 1;", "yield n + 2;");
        assertTrue(result.ok(), result.compileErrors().toString());
        assertEquals(SOURCE.replace("yield n + 1;", "yield n + 2;"), result.project().source());
        assertEquals("4", run(SOURCE));
        assertEquals("5", run(result.project().source()));
    }

    @Test
    void nestedEditStillRejectsInvalidJavaAndPreservesVersion() {
        Project project = store.create("BonusEdit.java", SOURCE);
        EditResult result = edit(project, "yield n + 1;", "yield missing;");
        assertFalse(result.ok());
        assertEquals(1, store.find(project.id()).orElseThrow().version());
        assertEquals(SOURCE, store.find(project.id()).orElseThrow().source());
    }

    @Test
    void nestedCommentCannotSwallowFollowingYield() {
        Project project = store.create("BonusEdit.java", SOURCE);
        EditResult result = edit(project, "int n = f.getAsInt();", "int n = f.getAsInt(); // скрыть");
        assertFalse(result.ok());
        assertEquals(SOURCE, store.find(project.id()).orElseThrow().source());
    }

    @Test
    void nestedBlueprintTargetsHaveRealHeatCounters() {
        Project project = store.create("BonusEdit.java", SOURCE);
        Blueprint blueprint = blueprints.forMethod(project, METHOD).orElseThrow();
        var measured = new HeatService().measure(project, METHOD, "");
        assertEquals(1L, measured.counts().get(node(blueprint, "a += 2;").id()));
        assertEquals(1L, measured.counts().get(node(blueprint, "yield n + 1;").id()));
        assertEquals(0L, measured.counts().get(node(blueprint, "yield 0;").id()));
        assertEquals(SOURCE, store.find(project.id()).orElseThrow().source());
    }

    private EditResult edit(Project project, String code, String replacement) {
        Blueprint blueprint = blueprints.forMethod(project, METHOD).orElseThrow();
        return command.apply(project.id(), node(blueprint, code).id(), replacement, project.version());
    }

    private static GraphNode node(Blueprint blueprint, String code) {
        return blueprint.graph().nodes().stream().filter(n -> code.equals(n.code())).findFirst().orElseThrow();
    }

    private String run(String source) {
        CompileResult compiled = compiler.compile("BonusEdit", source);
        assertTrue(compiled.ok(), compiled.messages().toString());
        RunResult result = new ProgramRunner().runOne(new WorkerProtocol.Program("BonusEdit", "BonusEdit", compiled.classes(), true), "", Duration.ofSeconds(5));
        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        return result.stdout();
    }
}
