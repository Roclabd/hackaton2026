package blueprints.heat;

import blueprints.api.ApiRoutes;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/** Маршрут подключён через настоящий ApiRoutes; контракт проверяется через HTTP. */
class HeatRoutesTest {
    private final ProjectStore store = new ProjectStore();
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private Javalin app;

    @BeforeEach
    void start() {
        app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson());
            ApiRoutes.register(config, store);
        }).start(0);
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    @Test
    void shapeAndUnknownMethodAreExplicit() throws Exception {
        Project project = project("class Probe { public static void main(String[] a) { int x=1; } }");
        HttpResponse<String> response = post(project.id(), json.writeValueAsString(Map.of("methodId", "M:Probe#main(String[])", "input", "")));
        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals(Set.of("methodId", "runs", "counts", "maxCount", "note"),
                StreamSupport.stream(((Iterable<String>) () -> body.fieldNames()).spliterator(), false).collect(Collectors.toSet()));
        assertEquals("M:Probe#main(String[])", body.get("methodId").asText());
        assertEquals(1, body.get("runs").asInt());
        assertEquals(1, body.get("maxCount").asInt());
        assertEquals(1, body.get("counts").size());
        assertFalse(body.get("note").asText().isBlank());
        assertError(post(project.id(), "{\"methodId\":\"missing\"}"), 404, "Метод");
        assertError(post("missing", "{}"), 404, "Проект");
    }

    @Test
    void malformedAndMissingFieldsAreRussianJsonErrors() throws Exception {
        Project project = project("class Probe { public static void main(String[] a) {} }");
        for (String body : new String[]{"не json", "{}", "[]", "null", "{\"methodId\":42}",
                "{\"methodId\":\" \"}", "{\"methodId\":\"M:Probe#main(String[])\",\"input\":42}"}) {
            assertError(post(project.id(), body), 400, null);
        }
    }

    @Test
    void unsupportedProgramsReturn422InsteadOf500() throws Exception {
        Project noMain = project("class Probe { int f(){ return 1; } }");
        assertError(post(noMain.id(), "{\"methodId\":\"M:Probe#f()\"}"), 422, "main");
        Project stack = project("class Probe { public static void main(String[] a){ Thread.dumpStack(); } }");
        assertError(post(stack.id(), "{\"methodId\":\"M:Probe#main(String[])\"}"), 422, "стек");
        Project broken = project("class Probe { public static void main(String[] a){ int x=\"bad\"; } }");
        assertError(post(broken.id(), "{\"methodId\":\"M:Probe#main(String[])\"}"), 422, "компилируется");
        Project parse = project("class Probe {");
        assertError(post(parse.id(), "{\"methodId\":\"missing\"}"), 422, "разбирается");
    }

    private Project project(String source) {
        return store.create("Probe.java", source);
    }

    private void assertError(HttpResponse<String> response, int status, String contains) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals(1, body.size(), response.body());
        assertTrue(body.path("error").isTextual(), response.body());
        if (contains != null) assertTrue(body.get("error").asText().contains(contains), response.body());
    }

    private HttpResponse<String> post(String projectId, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/api/projects/" + projectId + "/heat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
