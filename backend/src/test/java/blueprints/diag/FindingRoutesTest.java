package blueprints.diag;

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

/** GET /api/projects/{id}/findings через настоящий HTTP на свободном порту. */
class FindingRoutesTest {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final ProjectStore store = new ProjectStore();
    private Javalin app;

    @BeforeEach
    void start() {
        app = Javalin.create(cfg -> {
            cfg.jsonMapper(new JavalinJackson());
            FindingRoutes.register(cfg, store);
        }).start(0);
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    @Test
    void returnsFindingsInContractShape() throws Exception {
        String source = Files.readString(Path.of("../corpus/ours/MatchDemo/MatchDemo.java"), StandardCharsets.UTF_8);
        Project project = store.create("MatchDemo.java", source);

        HttpResponse<String> response = get("/api/projects/" + project.id() + "/findings");
        assertEquals(200, response.statusCode(), response.body());

        JsonNode findings = json.readTree(response.body());
        assertEquals(1, findings.size());
        assertEquals("R1-1", findings.at("/0/id").asText());
        assertEquals(Finding.INEFFICIENCY, findings.at("/0/category").asText());
        assertEquals("M:MatchDemo#countMatches(List<Integer>,List<Integer>)", findings.at("/0/methodId").asText());
        assertEquals(14, findings.at("/0/lines/0").asInt());
        assertEquals(20, findings.at("/0/lines/1").asInt());
        assertEquals("n²", findings.at("/0/growthBefore").asText());
        assertEquals("R1-1-index", findings.at("/0/patches/0/id").asText());
        assertEquals("REFACTOR", findings.at("/0/patches/0/intent").asText());
        assertTrue(findings.at("/0/patches/0/source").asText().contains("public class MatchDemo"), "полный текст файла");
        assertEquals(2, findings.at("/0/patches/0/changedLines").size());
    }

    @Test
    void unknownProjectIs404WithError() throws Exception {
        HttpResponse<String> response = get("/api/projects/nope/findings");
        assertEquals(404, response.statusCode());
        assertTrue(json.readTree(response.body()).get("error").asText().contains("nope"));
    }

    @Test
    void defectCategoryAndAbsentGrowthAreSerialized() throws Exception {
        Project project = store.create("Sample.java", "class Sample { double run(int a,int b){ return a/b; } }");
        HttpResponse<String> response = get("/api/projects/" + project.id() + "/findings");
        assertEquals(200, response.statusCode());
        JsonNode finding = json.readTree(response.body()).get(0);
        assertEquals("R8", finding.get("rule").asText());
        assertEquals(Finding.DEFECT, finding.get("category").asText());
        assertTrue(finding.get("growthBefore").isNull());
        assertTrue(finding.get("growthAfter").isNull());
        assertEquals(Patch.BUGFIX, finding.at("/patches/0/intent").asText());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}


