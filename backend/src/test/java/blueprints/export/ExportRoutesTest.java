package blueprints.export;

import blueprints.gate.GateService.CompletedCheck;
import blueprints.gate.GateService.ReferenceKind;
import blueprints.gate.GateService.ReferenceRun;
import blueprints.gate.VerdictStatus;
import blueprints.inputs.InputGenerator;
import blueprints.inputs.LabeledInput;
import blueprints.project.Project;
import blueprints.project.ProjectStore;
import blueprints.runner.RunResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Маршруты экспорта через настоящий HTTP. Проверки подставлены готовыми — без прогонов. */
class ExportRoutesTest {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final ProjectStore store = new ProjectStore();
    private Javalin app;
    private Project project;

    @BeforeEach
    void start() throws Exception {
        String matchDemo = Files.readString(Path.of("../corpus/ours/MatchDemo/MatchDemo.java"), StandardCharsets.UTF_8);
        project = store.create("MatchDemo.java", matchDemo);
        Map<String, CompletedCheck> checks = Map.of(
                "done", check(matchDemo, VerdictStatus.MATCHED),
                "incomplete", check(matchDemo, VerdictStatus.INCOMPLETE));
        app = Javalin.create(cfg -> {
            cfg.jsonMapper(new JavalinJackson());
            ExportRoutes.register(cfg, store, id -> Optional.ofNullable(checks.get(id)));
        }).start(0);
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    private static CompletedCheck check(String source, VerdictStatus status) {
        RunResult nullMatch = new RunResult("Совпадений: 1\n", "", 0, null, false, 5, false, false);
        RunResult random = new RunResult("случайно\n", "", 0, null, false, 5, false, false);
        return new CompletedCheck("MatchDemo.java", source, source, status, List.of(
                new ReferenceRun(new LabeledInput(InputGenerator.NULLS, "null\nnull\n"), nullMatch, ReferenceKind.COMPARED),
                new ReferenceRun(new LabeledInput(InputGenerator.NORMAL, "1 2\n3\n"), random, ReferenceKind.NONDETERMINISTIC)));
    }

    @Test
    void downloadReturnsZip() throws Exception {
        HttpResponse<byte[]> response = get("/export/junit?jobId=done", HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        assertEquals("application/zip", response.headers().firstValue("Content-Type").orElse(""));
        assertEquals("attachment; filename=\"MatchDemo-tests.zip\"",
                response.headers().firstValue("Content-Disposition").orElse(""));
        Map<String, String> files = JUnitExporterTest.unzip(response.body());
        String test = files.get("src/test/java/MatchDemoCharacterizationTest.java");
        assertTrue(test.contains("Совпадений: 1"), test);
        assertFalse(test.contains("случайно"), "случайный вывод в тест не попадает");
        assertTrue(files.get("README.md").contains("случайным выводом: 1"), files.get("README.md"));
    }

    @Test
    void previewShowsFirstTest() throws Exception {
        HttpResponse<String> response = get("/export/junit/preview?jobId=done", HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals("MatchDemo-tests.zip", body.get("zipName").asText());
        assertEquals("src/test/java/MatchDemoCharacterizationTest.java", body.get("testFile").asText());
        assertEquals("JUnit Jupiter 6.1.3", body.get("framework").asText());
        assertEquals(1, body.get("tests").asInt());
        assertEquals(1, body.get("skipped").asInt());
        assertTrue(body.get("firstTest").asText().contains("void nullValues01()"));
    }

    @Test
    void unfinishedOrUnknownCheckIs409() throws Exception {
        for (String jobId : List.of("missing", "incomplete")) {
            HttpResponse<String> response = get("/export/junit?jobId=" + jobId, HttpResponse.BodyHandlers.ofString());
            assertEquals(409, response.statusCode(), jobId);
            assertFalse(json.readTree(response.body()).get("error").asText().isBlank());
        }
    }

    @Test
    void missingJobIdIs400AndUnknownProjectIs404() throws Exception {
        assertEquals(400, get("/export/junit", HttpResponse.BodyHandlers.ofString()).statusCode());
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + app.port() + "/api/projects/nope/export/junit?jobId=done")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
    }

    @Test
    void nonLatinFileNameIsEncoded() {
        assertEquals("attachment; filename=\"_____-tests.zip\"; filename*=UTF-8''%D0%A1%D1%87%D1%91%D1%82%D1%8B-tests.zip",
                ExportRoutes.contentDisposition("Счёты-tests.zip"));
    }

    private <T> HttpResponse<T> get(String path, HttpResponse.BodyHandler<T> handler) throws Exception {
        URI uri = URI.create("http://localhost:" + app.port() + "/api/projects/" + project.id() + path);
        return http.send(HttpRequest.newBuilder(uri).build(), handler);
    }
}
