package blueprints.export;

import blueprints.gate.GateService;
import blueprints.gate.GateService.CompletedCheck;
import blueprints.gate.GateService.ReferenceKind;
import blueprints.gate.Intent;
import blueprints.gate.VerdictStatus;
import blueprints.project.ProjectStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Экспорт по настоящей проверке MatchDemo: архив содержит мини-проект Maven, и сгенерированный тест
 * компилируется javac вместе с JUnit — то есть его действительно можно запустить без нашего инструмента.
 */
class JUnitExporterTest {

    private static GateService gate;
    private static String matchDemo;
    private static String matchedJob;
    private static String mismatchJob;

    @BeforeAll
    static void runChecks() throws Exception {
        gate = new GateService();
        matchDemo = Files.readString(Path.of("../corpus/ours/MatchDemo/MatchDemo.java"), StandardCharsets.UTF_8);
        ProjectStore store = new ProjectStore();
        matchedJob = gate.start(store.create("MatchDemo.java", matchDemo), matchDemo, Intent.REFACTOR);
        String broken = matchDemo.replace("Objects.equals(x, y)", "x.equals(y)");
        mismatchJob = gate.start(store.create("MatchDemo.java", matchDemo), broken, Intent.REFACTOR);
    }

    @AfterAll
    static void tearDown() {
        gate.close();
    }

    static CompletedCheck await(String jobId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            var check = GateService.completed(jobId);
            if (check.isPresent()) {
                return check.get();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("проверка " + jobId + " не закончилась за 2 минуты");
    }

    @Test
    void matchedCheckBecomesMavenProject() throws Exception {
        CompletedCheck check = await(matchedJob);
        assertEquals(VerdictStatus.MATCHED, check.status());
        JUnitExport export = JUnitExporter.export(matchedJob, check);

        assertEquals("MatchDemo-tests.zip", export.zipName());
        assertEquals(List.of("README.md", "pom.xml", "src/main/java/MatchDemo.java",
                "src/test/java/MatchDemoCharacterizationTest.java", "src/test/resources/junit-platform.properties",
                "reference/MatchDemo.java"),
                List.copyOf(export.files().keySet()));
        long compared = check.runs().stream().filter(r -> r.kind() == ReferenceKind.COMPARED).count();
        assertEquals(compared, export.tests(), "по тесту на каждый сравненный вход");
        assertEquals(check.runs().size() - compared, export.skipped());

        String pom = export.files().get("pom.xml");
        assertTrue(pom.contains("<junit.version>6.1.3</junit.version>"), pom);
        assertTrue(pom.contains("<version>3.6.0</version>"), pom);
        assertTrue(pom.contains("<maven.compiler.release>25</maven.compiler.release>"), pom);

        String test = export.files().get("src/test/java/MatchDemoCharacterizationTest.java");
        assertEquals(export.tests(), count(test, "    @Test\n"));
        assertTrue(test.contains("Совпадений: "), "кириллица вывода эталона сохраняется");
        assertTrue(test.contains("void nullValues01()"), test);
        assertTrue(test.contains("@DisplayName(\"Значения null №1\")"), test);
        assertTrue(export.firstTest().contains("assertBehaviour("), export.firstTest());
        assertTrue(export.files().get("README.md").contains("mvn test"));
    }

    @Test
    void zipHoldsTheSameFilesInUtf8() throws Exception {
        JUnitExport export = JUnitExporter.export(matchedJob, await(matchedJob));
        Map<String, String> unpacked = unzip(JUnitExporter.zip(export));
        assertEquals(export.files(), unpacked);
    }

    @Test
    void generatedTestCompilesWithJUnit(@TempDir Path dir) throws Exception {
        JUnitExport export = JUnitExporter.export(matchedJob, await(matchedJob));
        List<Path> sources = List.of(write(dir, export, "src/main/java/MatchDemo.java"),
                write(dir, export, export.testFile()));

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Path classes = Files.createDirectories(dir.resolve("classes"));
            List<String> options = List.of("-encoding", "UTF-8", "-d", classes.toString(),
                    "-classpath", System.getProperty("java.class.path"));
            boolean ok = javac.getTask(null, files, diagnostics, options, null,
                    files.getJavaFileObjectsFromPaths(sources)).call();
            assertTrue(ok, diagnostics.getDiagnostics().toString());
            assertTrue(Files.exists(classes.resolve("MatchDemoCharacterizationTest.class")));
        }
    }

    @Test
    void mismatchKeepsReferenceExpectationsAndCandidateCode() throws Exception {
        CompletedCheck check = await(mismatchJob);
        assertEquals(VerdictStatus.MISMATCH, check.status());
        JUnitExport export = JUnitExporter.export(mismatchJob, check);

        assertTrue(export.files().get("src/main/java/MatchDemo.java").contains("x.equals(y)"), "в main — кандидат");
        assertTrue(export.files().get("reference/MatchDemo.java").contains("Objects.equals(x, y)"), "эталон для справки");
        String test = export.files().get(export.testFile());
        assertFalse(test.contains("NullPointerException"), "ожидания — вывод эталона, а он на null не падает");
        assertTrue(export.files().get("README.md").contains("упадут"));
    }

    @Test
    void onlyMatchedOrMismatchIsExportable() throws Exception {
        CompletedCheck matched = await(matchedJob);
        CompletedCheck incomplete = new CompletedCheck(matched.fileName(), matched.originalSource(),
                matched.candidateSource(), VerdictStatus.INCOMPLETE, matched.runs());
        var error = assertThrows(JUnitExporter.NotExportable.class, () -> JUnitExporter.export("j0", incomplete));
        assertTrue(error.getMessage().contains("INCOMPLETE"), error.getMessage());
    }

    private static Path write(Path dir, JUnitExport export, String name) throws Exception {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, export.files().get(name), StandardCharsets.UTF_8);
    }

    static Map<String, String> unzip(byte[] zip) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                files.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
            count++;
        }
        return count;
    }
}
