package blueprints.profile;

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

import static org.junit.jupiter.api.Assertions.*;

/** POST /api/projects/{id}/profile через настоящий HTTP на свободном порту. */
class ProfileRoutesTest {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final ProjectStore store = new ProjectStore();
    private Javalin app;

    @BeforeEach
    void start() {
        app = Javalin.create(cfg -> {
            cfg.jsonMapper(new JavalinJackson());
            ProfileRoutes.register(cfg, store);
        }).start(0);
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    @Test
    void measuresMatchDemoInContractShape() throws Exception {
        String source = Files.readString(Path.of("../corpus/ours/MatchDemo/MatchDemo.java"), StandardCharsets.UTF_8);
        Project project = store.create("MatchDemo.java", source);

        long start = System.currentTimeMillis();
        HttpResponse<String> response = post("/api/projects/" + project.id() + "/profile",
                "{\"findingId\":\"R1-1\",\"patchId\":\"R1-1-index\"}");
        long tookMs = System.currentTimeMillis() - start;

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertTrue(body.get("supported").asBoolean(), response.body());
        assertEquals(5, body.get("sizes").size());
        assertEquals("n²", body.at("/before/model").asText());
        assertEquals("n", body.at("/after/model").asText());
        assertEquals(5, body.at("/before/counts").size());
        assertTrue(body.get("factor").asDouble() > 1);
        assertFalse(body.get("note").asText().isBlank());
        assertTrue(tookMs < 15_000, "запрос длился " + tookMs + " мс");
        System.out.println("POST /profile за " + tookMs + " мс: " + response.body());
    }

    @Test
    void errorsAre404And400() throws Exception {
        Project project = store.create("MatchDemo.java",
                Files.readString(Path.of("../corpus/ours/MatchDemo/MatchDemo.java"), StandardCharsets.UTF_8));
        String path = "/api/projects/" + project.id() + "/profile";

        assertEquals(404, post("/api/projects/nope/profile", "{\"findingId\":\"R1-1\"}").statusCode());
        HttpResponse<String> unknownFinding = post(path, "{\"findingId\":\"R7-7\"}");
        assertEquals(404, unknownFinding.statusCode());
        assertTrue(json.readTree(unknownFinding.body()).get("error").asText().contains("R7-7"));
        assertEquals(404, post(path, "{\"findingId\":\"R1-1\",\"patchId\":\"nope\"}").statusCode());
        assertEquals(400, post(path, "не json").statusCode());
        assertEquals(400, post(path, "{}").statusCode());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
