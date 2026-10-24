package blueprints.project;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** JavaParser пропускает {@code if (x = 100)}, javac — нет: ошибка должна быть видна сразу при загрузке и правке. */
class CompileErrorsTest {

    private static final String BROKEN = """
            public class Main {
                public static void main(String[] args) {
                    int percent = 100;
                    if (percent = 100) {
                        System.out.println("max");
                    }
                }
            }
            """;

    private final ProjectStore store = new ProjectStore();

    @Test
    void assignmentInConditionIsReportedOnUpload() {
        Project project = store.create("Main.java", BROKEN);

        assertTrue(project.errors().isEmpty(), "синтаксически код корректен");
        assertEquals(1, project.compileErrors().size());
        CompileError error = project.compileErrors().get(0);
        assertEquals(4, error.line());
        assertTrue(error.text().contains("cannot be converted to boolean"), error.text());
    }

    @Test
    void fixingTheCodeClearsCompileErrors() {
        Project project = store.create("Main.java", BROKEN);
        Project fixed = store.updateSource(project.id(), 1, BROKEN.replace("percent = 100)", "percent == 100)"));

        assertTrue(fixed.compileErrors().isEmpty());
    }

    @Test
    void syntaxErrorsAreNotCompiled() {
        Project project = store.create("Main.java", "public class Main { void f( }");

        assertFalse(project.errors().isEmpty());
        assertTrue(project.compileErrors().isEmpty(), "синтаксические ошибки уже показаны отдельно");
    }
}
