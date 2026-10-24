package blueprints.gate;

import blueprints.project.Project;
import blueprints.project.ProjectStore;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Маршруты прогона и проверки через настоящий HTTP — как их вызывает фронт. */
class GateRoutesTest {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final ProjectStore store = new ProjectStore();
    private GateService gate;
    private Javalin app;
    private String matchDemo;
    private Project project;

    @BeforeEach
    void start() throws Exception {
        gate = new GateService();
        app = Javalin.create(cfg -> {
            cfg.jsonMapper(new JavalinJackson());
            GateRoutes.register(cfg, store, gate);
        }).start(0);
        matchDemo = Files.readString(Path.of("../corpus/ours/MatchDemo/MatchDemo.java"), StandardCharsets.UTF_8);
        project = store.create("MatchDemo.java", matchDemo);
    }

    @AfterEach
    void stop() {
        app.stop();
        gate.close();
    }

    @Test
    void runReturnsRunResult() throws Exception {
        HttpResponse<String> response = post("/api/projects/" + project.id() + "/run", Map.of("input", "1 2\n2 3"));
        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals("Совпадений: 1", body.get("stdout").asText().strip());
        assertEquals(0, body.get("exitCode").asInt());
        assertTrue(body.get("exception").isNull());
        assertFalse(body.get("timedOut").asBoolean());
        assertTrue(body.has("durationMs"));
    }

    @Test
    void gateJobReachesMismatchWithContractFields() throws Exception {
        String candidate = matchDemo.replace("Objects.equals(x, y)", "x.equals(y)");
        HttpResponse<String> started = post("/api/projects/" + project.id() + "/gate",
                Map.of("source", candidate, "intent", "REFACTOR", "originalSource", matchDemo));
        assertEquals(200, started.statusCode(), started.body());
        String jobId = json.readTree(started.body()).get("jobId").asText();

        JsonNode verdict = poll(jobId);
        assertEquals("MISMATCH", verdict.get("status").asText());
        assertEquals("done", verdict.get("phase").asText());
        assertEquals(1.0, verdict.get("progress").asDouble());
        assertTrue(verdict.at("/smallestMismatch/input").asText().contains("null"));
        assertEquals("java.lang.NullPointerException", verdict.at("/smallestMismatch/candidate/exception/type").asText());
        for (String field : new String[]{"inputs", "checked", "mismatches", "nondeterministic", "incompleteReason",
                "buildErrors", "example", "groups", "changedCovered", "speedup", "durationMs"}) {
            assertTrue(verdict.has(field), "есть поле " + field);
        }
    }

    @Test
    void errorsAreJsonWithStatus() throws Exception {
        assertEquals(404, post("/api/projects/nope/run", Map.of("input", "")).statusCode());
        assertEquals(404, get("/api/jobs/j999").statusCode());

        HttpResponse<String> badJson = send("POST", "/api/projects/" + project.id() + "/gate", "{не json");
        assertEquals(400, badJson.statusCode());
        assertTrue(json.readTree(badJson.body()).has("error"));

        assertEquals(400, post("/api/projects/" + project.id() + "/gate", Map.of("source", "")).statusCode());
        assertEquals(400, post("/api/projects/" + project.id() + "/gate",
                Map.of("source", matchDemo, "intent", "WHATEVER")).statusCode());
    }

    private JsonNode poll(String jobId) throws Exception {
        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> response = get("/api/jobs/" + jobId);
            assertEquals(200, response.statusCode(), response.body());
            JsonNode body = json.readTree(response.body());
            if (!body.get("status").asText().equals("RUNNING")) {
                return body;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("проверка не закончилась");
    }

    private HttpResponse<String> post(String path, Object body) throws Exception {
        return send("POST", path, json.writeValueAsString(body));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
