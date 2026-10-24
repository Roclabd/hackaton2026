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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Чертёж метода, правка узла и строгий разбор тела запроса — через настоящий HTTP. */
class BlueprintApiTest {

    private static final String COUNT_MATCHES = "M:MatchDemo#countMatches(List<Integer>,List<Integer>)";

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private Javalin app;
    private String source;
    private String projectId;

    @BeforeEach
    void start() throws Exception {
        app = App.create(new ProjectStore()).start(0);
        source = Files.readString(Path.of("../corpus/ours/MatchDemo/MatchDemo.java"), StandardCharsets.UTF_8);
        projectId = json.readTree(send("POST", "/api/projects", Map.of("fileName", "MatchDemo.java", "source", source)).body())
                .get("id").asText();
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    @Test
    void methodBlueprintHasNestedLoopsAndEditableCondition() throws Exception {
        JsonNode blueprint = blueprint();
        JsonNode outer = nodeByLabel(blueprint, "for (Integer x : a)");
        JsonNode inner = nodeByLabel(blueprint, "for (Integer y : b)");
        JsonNode condition = nodeByLabel(blueprint, "if (Objects.equals(x, y))");
        JsonNode increment = nodeByLabel(blueprint, "total++;");

        assertEquals("loop", outer.get("kind").asText());
        assertEquals(COUNT_MATCHES, outer.get("parent").asText(), "верхний уровень — прямо в методе");
        assertEquals(outer.get("id").asText(), inner.get("parent").asText());
        assertEquals(inner.get("id").asText(), condition.get("parent").asText());
        assertEquals(condition.get("id").asText(), increment.get("parent").asText());
        assertEquals("then", increment.get("branch").asText());
        assertTrue(condition.get("editable").asBoolean());
        assertEquals("Objects.equals(x, y)", condition.get("code").asText(), "в поле правки — ровно условие");
        assertFalse(outer.get("editable").asBoolean(), "заголовок for на схеме не правится");
        assertEquals("тело", outer.at("/branches/0/label").asText());
        assertEquals(16, condition.at("/lines/0").asInt());
    }

    @Test
    void editingConditionChangesOnlyThatTextAndCompiles() throws Exception {
        JsonNode condition = nodeByLabel(blueprint(), "if (Objects.equals(x, y))");
        String oldId = condition.get("id").asText();

        JsonNode result = command(oldId, "x.equals(y)", 1);
        assertTrue(result.get("ok").asBoolean(), result.toString());
        assertEquals(2, result.get("version").asInt());
        assertEquals(source.replace("if (Objects.equals(x, y))", "if (x.equals(y))"), result.get("source").asText(),
                "изменилось ровно условие, остальной файл байт в байт");
        String newId = result.at("/idMap/" + jsonPointerEscape(oldId)).asText();
        assertFalse(newId.isEmpty(), "старый id сопоставлен с новым: " + result.get("idMap"));
        assertEquals("if (x.equals(y))", nodeById(result.get("blueprint"), newId).get("label").asText());
    }

    @Test
    void invalidOrNonCompilingEditIsRejectedAndVersionStays() throws Exception {
        String conditionId = nodeByLabel(blueprint(), "if (Objects.equals(x, y))").get("id").asText();

        JsonNode broken = command(conditionId, "x.equals(", 1);
        assertFalse(broken.get("ok").asBoolean());
        assertTrue(broken.at("/compileErrors/0/text").asText().startsWith("Ожидалось одно Java-выражение"));

        JsonNode wrongType = command(conditionId, "x.missingMethod(y)", 1);
        assertFalse(wrongType.get("ok").asBoolean());
        assertTrue(wrongType.at("/compileErrors/0/text").asText().startsWith("Не компилируется"), wrongType.toString());
        assertEquals(1, wrongType.get("version").asInt(), "версия не изменилась");
    }

    @Test
    void staleVersionIsConflict() throws Exception {
        String conditionId = nodeByLabel(blueprint(), "if (Objects.equals(x, y))").get("id").asText();
        assertEquals(409, sendCommand(conditionId, "x.equals(y)", 5).statusCode());
    }

    @Test
    void insertingStatementAboveKeepsNodeIds() throws Exception {
        Set<String> before = ids(blueprint());
        String moved = source.replace("long total = 0;", "System.out.print(\"\");\n        long total = 0;");
        assertEquals(200, send("PUT", "/api/projects/" + projectId + "/source", Map.of("source", moved, "baseVersion", 1)).statusCode());
        Set<String> after = ids(blueprint());
        assertTrue(after.containsAll(before), "id существующих узлов не изменились");
    }

    @Test
    void malformedBodiesAreBadRequestNotServerError() throws Exception {
        assertEquals(400, sendRaw("POST", "/api/projects", "{").statusCode());
        assertEquals(400, sendRaw("POST", "/api/projects", "[]").statusCode());
        assertEquals(400, sendRaw("POST", "/api/projects", "null").statusCode());
        String path = "/api/projects/" + projectId + "/source";
        assertEquals(400, sendRaw("PUT", path, "{\"source\":\"class A {}\",\"baseVersion\":1.9}").statusCode());
        assertEquals(400, sendRaw("PUT", path, "{\"source\":\"class A {}\",\"baseVersion\":\"1\"}").statusCode());
        assertEquals(400, sendRaw("PUT", path, "{\"source\":\"class A {}\"}").statusCode());
        HttpResponse<String> response = sendRaw("POST", "/api/projects", "{");
        assertTrue(json.readTree(response.body()).has("error"), "ответ — JSON с error");
    }

    @Test
    void examplesComeFromCorpus() throws Exception {
        JsonNode list = json.readTree(send("GET", "/api/examples", null).body());
        assertTrue(list.findValuesAsText("fileName").contains("MatchDemo.java"));
        JsonNode example = json.readTree(send("GET", "/api/examples/MatchDemo.java", null).body());
        assertEquals(source, example.get("source").asText());
    }

    // ---- помощники ----

    private JsonNode blueprint() throws Exception {
        HttpResponse<String> response = send("GET", "/api/projects/" + projectId + "/methods/"
                + URLEncoder.encode(COUNT_MATCHES, StandardCharsets.UTF_8).replace("+", "%20"), null);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private JsonNode command(String nodeId, String text, int baseVersion) throws Exception {
        HttpResponse<String> response = sendCommand(nodeId, text, baseVersion);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private HttpResponse<String> sendCommand(String nodeId, String text, int baseVersion) throws Exception {
        return send("POST", "/api/projects/" + projectId + "/commands",
                Map.of("type", "editStatement", "nodeId", nodeId, "text", text, "baseVersion", baseVersion));
    }

    private static JsonNode nodeByLabel(JsonNode blueprint, String label) {
        for (JsonNode node : blueprint.get("nodes")) {
            if (node.get("label").asText().equals(label)) {
                return node;
            }
        }
        fail("нет узла «" + label + "»");
        return null;
    }

    private static JsonNode nodeById(JsonNode blueprint, String id) {
        for (JsonNode node : blueprint.get("nodes")) {
            if (node.get("id").asText().equals(id)) {
                return node;
            }
        }
        fail("нет узла " + id);
        return null;
    }

    private static Set<String> ids(JsonNode blueprint) {
        Set<String> ids = new HashSet<>();
        blueprint.get("nodes").forEach(n -> ids.add(n.get("id").asText()));
        return ids;
    }

    private static String jsonPointerEscape(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    private HttpResponse<String> send(String method, String path, Object body) throws Exception {
        return sendRaw(method, path, body == null ? null : json.writeValueAsString(body));
    }

    private HttpResponse<String> sendRaw(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path));
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
