package blueprints.commands;

import blueprints.graph.Blueprint;
import blueprints.graph.GraphNode;
import blueprints.project.Blueprints;
import blueprints.project.Project;
import blueprints.project.ProjectStore;
import blueprints.runner.InMemoryCompiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Правка узла на схеме: комментарии в тексте правки не должны задевать соседний код (аудит v4 №10). */
class EditStatementCommandTest {

    private static final String SOURCE = """
            public class EditTarget {
                static int twoOnOneLine() {
                    int a = 0, b = 0;
                    a = 1; b = 2;
                    return a * 10 + b;
                }

                static int branch(int n) {
                    if (n > 0) {
                        return 1;
                    }
                    return 0;
                }

                public static void main(String[] args) {
                    System.out.println(twoOnOneLine() + branch(1));
                }
            }
            """;
    private static final String TWO_ON_ONE_LINE = "M:EditTarget#twoOnOneLine()";
    private static final String BRANCH = "M:EditTarget#branch(int)";

    private final Blueprints blueprints = new Blueprints();
    private ProjectStore store;
    private EditStatementCommand command;
    private Project project;

    @BeforeEach
    void setUp() {
        store = new ProjectStore();
        command = new EditStatementCommand(store, new InMemoryCompiler());
        project = store.create("EditTarget.java", SOURCE);
    }

    @Test
    void lineCommentThatSwallowsNextStatementIsRejected() {
        EditResult result = edit(TWO_ON_ONE_LINE, "a = 1;", "a = 5; // поправил");

        assertFalse(result.ok());
        assertTrue(result.compileErrors().getFirst().text().contains("закомментирует код после узла"), result.compileErrors().toString());
        assertUnchanged();
    }

    @Test
    void lineCommentInConditionIsRejected() {
        EditResult result = edit(BRANCH, "n > 0", "n > 0 // положительное");

        assertFalse(result.ok());
        assertUnchanged();
    }

    @Test
    void unclosedBlockCommentIsRejected() {
        EditResult result = edit(TWO_ON_ONE_LINE, "a = 1;", "a = 5; /* поправил");

        assertFalse(result.ok());
        assertUnchanged();
    }

    /** Закрытый блочный комментарий ничего не съедает, поэтому правка допустима: код вокруг узла не меняется. */
    @Test
    void closedBlockCommentIsAccepted() {
        EditResult result = edit(TWO_ON_ONE_LINE, "a = 1;", "a = 5; /* ок */");

        assertTrue(result.ok(), result.compileErrors().toString());
        assertEquals(SOURCE.replace("a = 1; b = 2;", "a = 5; /* ок */ b = 2;"), result.project().source());
        assertEquals(2, result.project().version());
    }

    /** Слэши внутри строкового литерала — не комментарий: поиск «//» в тексте дал бы здесь ложный отказ. */
    @Test
    void doubleSlashInsideStringLiteralIsAccepted() {
        EditResult result = edit(TWO_ON_ONE_LINE, "a = 1;", "a = \"http://x\".length();");

        assertTrue(result.ok(), result.compileErrors().toString());
        assertTrue(result.project().source().contains("a = \"http://x\".length(); b = 2;"));
    }

    @Test
    void plainEditKeepsRestOfFileByteForByte() {
        EditResult result = edit(TWO_ON_ONE_LINE, "b = 2;", "b = 3;");

        assertTrue(result.ok(), result.compileErrors().toString());
        assertEquals(SOURCE.replace("b = 2;", "b = 3;"), result.project().source());
    }

    /** Исчезнувший узел не отображается на чужой узел, занявший его место в списке соседей. */
    @Test
    void idMatcherDoesNotMapVanishedNodeOntoNeighbour() {
        Blueprint before = blueprints.forMethod(project, TWO_ON_ONE_LINE).orElseThrow();
        Project shorter = store.updateSource(project.id(), project.version(), SOURCE.replace("a = 1; b = 2;", "a = 1;"));
        Blueprint after = blueprints.forMethod(shorter, TWO_ON_ONE_LINE).orElseThrow();

        assertEquals(Map.of(), IdMatcher.match(before, after));
    }

    @Test
    void idMatcherStillMapsEditedNode() {
        EditResult result = edit(TWO_ON_ONE_LINE, "b = 2;", "b = 3;");

        assertEquals(1, result.idMap().size(), result.idMap().toString());
        String newId = result.idMap().values().iterator().next();
        assertEquals("b = 3;", nodeByCode(result.blueprint(), "b = 3;").code());
        assertEquals(newId, nodeByCode(result.blueprint(), "b = 3;").id());
    }

    private EditResult edit(String methodId, String nodeCode, String text) {
        Blueprint blueprint = blueprints.forMethod(project, methodId).orElseThrow();
        return command.apply(project.id(), nodeByCode(blueprint, nodeCode).id(), text, project.version());
    }

    private static GraphNode nodeByCode(Blueprint blueprint, String code) {
        return blueprint.graph().nodes().stream().filter(n -> code.equals(n.code())).findFirst()
                .orElseThrow(() -> new AssertionError("нет узла с кодом " + code));
    }

    private void assertUnchanged() {
        Project current = store.find(project.id()).orElseThrow();
        assertEquals(1, current.version(), "версия не изменилась");
        assertEquals(SOURCE, current.source());
    }
}
