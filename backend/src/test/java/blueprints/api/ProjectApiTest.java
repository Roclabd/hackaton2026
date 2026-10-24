package blueprints.api;

import blueprints.App;
import blueprints.project.ProjectStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Проверка API через настоящий HTTP — ровно так, как его вызывает фронт. */
class ProjectApiTest {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private Javalin app;

    @BeforeEach
    void start() {
        app = App.create(new ProjectStore()).start(0);
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    @Test
    void uploadReturnsMap() throws Exception {
        HttpResponse<String> response = send("POST", "/api/projects",
                Map.of("fileName", "Shop.java", "source", "class Shop { void sell() { audit(); } void audit() {} }"));
        assertEquals(201, response.statusCode(), response.body());

        JsonNode body = json.readTree(response.body());
        assertEquals(1, body.get("version").asInt());
        assertEquals(0, body.get("errors").size());
        assertEquals("class", body.at("/map/nodes/0/kind").asText(), "kind пишется строчными, как в контракте");
        assertEquals("M:Shop#sell()", body.at("/map/edges/0/from").asText());
        assertEquals("M:Shop#audit()", body.at("/map/edges/0/to").asText());
    }

    @Test
    void brokenCodeKeepsLastWorkingMap() throws Exception {
        JsonNode created = json.readTree(send("POST", "/api/projects",
                Map.of("fileName", "A.java", "source", "class A { void f() {} }")).body());
        String id = created.get("id").asText();

        HttpResponse<String> response = send("PUT", "/api/projects/" + id + "/source",
                Map.of("source", "class A { void f() { ", "baseVersion", 1));
        assertEquals(200, response.statusCode(), response.body());

        JsonNode body = json.readTree(response.body());
        assertEquals(2, body.get("version").asInt());
        assertTrue(body.get("errors").size() > 0, "ошибка разбора отдана");
        assertTrue(body.at("/map/nodes").size() > 0, "схема — от последней рабочей версии");
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        JsonNode created = json.readTree(send("POST", "/api/projects",
                Map.of("fileName", "A.java", "source", "class A {}")).body());
        HttpResponse<String> response = send("PUT", "/api/projects/" + created.get("id").asText() + "/source",
                Map.of("source", "class A { int x; }", "baseVersion", 7));
        assertEquals(409, response.statusCode());
    }

    @Test
    void servesFrontendWhenBuilt() throws Exception {
        assumeTrue(App.class.getResource("/web/index.html") != null, "фронт не собран — проверять нечего");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/")).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("<div id=\"root\">"), "отдаётся index.html фронта");

        // Скрипт из index.html должен прийти как JavaScript, а не как HTML (иначе белый экран).
        java.util.regex.Matcher script = java.util.regex.Pattern.compile("src=\"(/assets/[^\"]+\\.js)\"").matcher(response.body());
        assertTrue(script.find(), "в index.html есть скрипт");
        HttpResponse<String> asset = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + script.group(1))).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, asset.statusCode());
        assertTrue(asset.headers().firstValue("Content-Type").orElse("").startsWith("text/javascript"), "тип — JavaScript");

        HttpResponse<String> deepLink = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/some/app/path")).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertTrue(deepLink.body().contains("<div id=\"root\">"), "любой путь приложения → index.html");
        assertEquals(404, http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/api/nope")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode(), "неизвестный /api — 404, а не страница");
    }

    @Test
    void emptySourceIsBadRequest() throws Exception {
        assertEquals(400, send("POST", "/api/projects", Map.of("fileName", "A.java", "source", "  ")).statusCode());
    }

    private HttpResponse<String> send(String method, String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
