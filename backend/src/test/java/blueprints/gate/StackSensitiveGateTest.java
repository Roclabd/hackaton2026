package blueprints.gate;

import blueprints.project.ProjectStore;
import blueprints.runner.InMemoryCompiler;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StackSensitiveGateTest {

    @Test
    void builderChoosesPlainForStackApisAndKeepsMatchDemoInWorker() throws Exception {
        ProgramBuilder builder = new ProgramBuilder(new InMemoryCompiler());
        ProgramBuilder.Built stack = builder.build("p", "class P { public static void main(String[] a) { Thread.dumpStack(); } }", null, true);
        assertTrue(stack.ok());
        assertTrue(stack.program().plainJvm());
        String matchDemo = Files.readString(Path.of("../corpus/ours/MatchDemo/MatchDemo.java"), StandardCharsets.UTF_8);
        assertFalse(builder.build("p", matchDemo, null, true).program().plainJvm());
    }

    @Test
    void introducingStackApiRunsBothVersionsInPlainJavaAndReportsCoverageIncomplete() throws Exception {
        String original = """
                public class Pair {
                    public static void main(String[] args) {
                        System.out.println(System.getProperty("sun.java.command"));
                    }
                }
                """;
        String candidate = original.replace("System.out.println", "new Throwable().getStackTrace(); System.out.println");
        try (GateService gate = new GateService()) {
            Verdict verdict = check(gate, original, candidate, Intent.REFACTOR);
            assertEquals(VerdictStatus.INCOMPLETE, verdict.status());
            assertEquals(0, verdict.mismatches(), "обе стороны должны видеть java Pair, а не только кандидат");
            assertNotNull(verdict.example(), "ни один вход не сравнён: " + verdict.incompleteReason());
            assertEquals("Pair" + System.lineSeparator(), verdict.example().original().stdout());
            assertEquals(verdict.example().original().stdout(), verdict.example().candidate().stdout());
            assertTrue(verdict.incompleteReason().contains("без счётчиков"), verdict.incompleteReason());
        }
    }

    @Test
    void sameStackSensitiveCodeMatchesAndCleanDifferencesKeepTheirPriority() throws Exception {
        String original = """
                public class Pair {
                    public static void main(String[] args) {
                        new Throwable().getStackTrace();
                        System.out.print("same");
                    }
                }
                """;
        try (GateService gate = new GateService()) {
            assertEquals(VerdictStatus.MATCHED, check(gate, original, original, Intent.REFACTOR).status());
            String different = original.replace("\"same\"", "\"different\"");
            assertEquals(VerdictStatus.MISMATCH, check(gate, original, different, Intent.REFACTOR).status());
            assertEquals(VerdictStatus.BEHAVIOR_CHANGE, check(gate, original, different, Intent.BUGFIX).status());
            assertEquals(VerdictStatus.BUILD_ERROR, check(gate, original, original.replace("\"same\"", "missing"), Intent.REFACTOR).status());
        }
    }

    private Verdict check(GateService gate, String original, String candidate, Intent intent) throws Exception {
        return GateServiceTest.await(gate, gate.start(new ProjectStore().create("Pair.java", original), original, candidate, intent));
    }
}
