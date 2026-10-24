package blueprints.robustness;

import blueprints.App;
import blueprints.project.ProjectStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * «Незнакомый код»: монолиты из corpus/robustness/, написанные в стиле учебных программ, которые генерирует ИИ.
 * Каждый проходит весь путь через настоящий HTTP-сервер, как на защите: загрузка → карта → чертёж каждого
 * метода → находки → правка узлов эквивалентным текстом → проверка оригинала против самого себя.
 *
 * Тест не знает заранее, что в файлах: он только требует, чтобы инструмент нигде не падал (нет 500, нет
 * исключений), отвечал по контракту и укладывался во время. Все проблемы файла собираются в один список,
 * чтобы видеть их сразу, а не по одной.
 *
 * Итоговая таблица: -Drobustness.report=../docs/robustness-run.txt
 */
class RobustnessCorpusTest {

    private static final Path DIR = Path.of("../corpus/robustness");
    /** Лимит на одну проверку: дольше — на защите это уже «зависло». */
    private static final Duration GATE_LIMIT = Duration.ofSeconds(60);
    /** Сколько заплаток из находок проверять на файл: каждая — полноценная проверка. */
    private static final int PATCHES_PER_FILE = 2;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final List<String> REPORT = Collections.synchronizedList(new ArrayList<>());

    private static Javalin app;

    @BeforeAll
    static void startServer() {
        app = App.create(new ProjectStore()).start(0);
    }

    @AfterAll
    static void stopServer() throws Exception {
        app.stop();
        List<String> lines = new ArrayList<>();
        lines.add(String.format("%-12s | %7s | %7s | %-32s | %s", "Файл", "методов", "находок", "проверка с собой", "время"));
        lines.addAll(REPORT.stream().sorted().toList());
        lines.forEach(System.out::println);
        String file = System.getProperty("robustness.report");
        if (file != null && !file.isBlank()) {
            Files.write(Path.of(file), lines, StandardCharsets.UTF_8);
        }
    }

    static Stream<String> programs() throws Exception {
        try (Stream<Path> dirs = Files.list(DIR)) {
            return dirs.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList().stream();
        }
    }

    @ParameterizedTest
    @MethodSource("programs")
    void wholePathWorksOnUnfamiliarCode(String name) throws Exception {
        List<String> problems = new ArrayList<>();
        String source = Files.readString(DIR.resolve(name).resolve(name + ".java"), StandardCharsets.UTF_8);

        // 1. Загрузка
        HttpResponse<String> uploaded = send("POST", "/api/projects", Map.of("fileName", name + ".java", "source", source));
        if (uploaded.statusCode() != 201) {
            fail(name + ": загрузка вернула " + uploaded.statusCode() + ": " + uploaded.body());
        }
        JsonNode project = JSON.readTree(uploaded.body());
        String id = project.get("id").asText();
        expect(problems, project.get("errors").isEmpty(), "разбор с ошибками: " + project.get("errors"));
        expect(problems, project.get("compileErrors").isEmpty(), "javac не принял файл: " + project.get("compileErrors"));

        // 2. Карта и чертёж каждого метода
        List<String> methodIds = new ArrayList<>();
        for (JsonNode node : project.get("map").get("nodes")) {
            if (node.get("kind").asText().equals("method")) {
                methodIds.add(node.get("id").asText());
            }
        }
        expect(problems, !methodIds.isEmpty(), "на карте нет методов");
        Map<String, JsonNode> blueprints = new LinkedHashMap<>();
        for (String methodId : methodIds) {
            HttpResponse<String> response = send("GET", "/api/projects/" + id + "/methods/" + encode(methodId), null);
            if (response.statusCode() != 200) {
                problems.add("чертёж " + methodId + " → " + response.statusCode() + ": " + shorten(response.body()));
                continue;
            }
            JsonNode blueprint = JSON.readTree(response.body());
            blueprints.put(methodId, blueprint);
            checkBlueprint(problems, methodId, blueprint, source);
        }

        // 3. Находки
        HttpResponse<String> findingsResponse = send("GET", "/api/projects/" + id + "/findings", null);
        JsonNode findings = JSON.createArrayNode();
        if (findingsResponse.statusCode() != 200) {
            problems.add("находки → " + findingsResponse.statusCode() + ": " + shorten(findingsResponse.body()));
        } else {
            findings = JSON.readTree(findingsResponse.body());
        }

        // 4. Правка узлов эквивалентным текстом — каждый вид по разу, на свежем проекте
        for (EditCase edit : EDIT_CASES) {
            JsonNode node = firstNode(blueprints, edit.picks());
            if (node != null) {
                tryEdit(problems, name, source, node, edit);
            }
        }

        // 5. Прогон на пустом входе — кнопка «Запустить»
        HttpResponse<String> run = send("POST", "/api/projects/" + id + "/run", Map.of("input", ""));
        expect(problems, run.statusCode() == 200, "прогон → " + run.statusCode() + ": " + shorten(run.body()));

        // 6. Проверка оригинала против самого себя
        GateOutcome self = gate(problems, id, source, source, "REFACTOR", "с собой");
        if (self != null) {
            String status = self.status();
            expect(problems, status.equals("MATCHED") || status.equals("INCOMPLETE"),
                    "проверка с собой: " + status + " (" + self.reason() + ")");
            if (status.equals("INCOMPLETE")) {
                expect(problems, self.reason() != null && !self.reason().isBlank(), "INCOMPLETE без причины");
            }
        }

        // 7. Заплатки из находок: должны собираться и проверяться без сбоя инструмента
        int patches = 0;
        for (JsonNode finding : findings) {
            for (JsonNode patch : finding.path("patches")) {
                if (patches++ >= PATCHES_PER_FILE) {
                    break;
                }
                GateOutcome outcome = gate(problems, id, source, patch.get("source").asText(),
                        patch.get("intent").asText(), "заплатка " + patch.get("id").asText());
                if (outcome != null) {
                    expect(problems, !outcome.status().equals("BUILD_ERROR"),
                            "заплатка " + patch.get("id").asText() + " не собирается");
                    expect(problems, outcome.reason() == null || !outcome.reason().startsWith("Внутренняя ошибка"),
                            "заплатка " + patch.get("id").asText() + ": " + outcome.reason());
                }
            }
        }

        REPORT.add(String.format("%-12s | %7d | %7d | %-32s | %s", name, methodIds.size(), findings.size(),
                self == null ? "сбой" : self.status() + ", входов " + self.inputs(),
                self == null ? "-" : String.format("%.1f с", self.millis() / 1000.0))
                + (self != null && self.reason() != null ? "\n             причина: " + self.reason() : ""));
        if (!problems.isEmpty()) {
            fail(name + ": " + problems.size() + " проблем(ы):\n  - " + String.join("\n  - ", problems));
        }
    }

    // ---------- чертёж ----------

    /** Контракт чертежа: id уникальны, у каждого узла есть родитель, строки — внутри файла. */
    private static void checkBlueprint(List<String> problems, String methodId, JsonNode blueprint, String source) {
        int fileLines = source.split("\n", -1).length;
        List<String> ids = new ArrayList<>();
        for (JsonNode node : blueprint.get("nodes")) {
            ids.add(node.get("id").asText());
        }
        expect(problems, ids.size() == ids.stream().distinct().count(), "повтор id узлов в чертеже " + methodId);
        for (JsonNode node : blueprint.get("nodes")) {
            String parent = node.get("parent").asText();
            expect(problems, parent.equals(methodId) || ids.contains(parent), "нет родителя у узла " + node.get("id"));
            JsonNode lines = node.get("lines");
            expect(problems, lines != null && lines.get(0).asInt() >= 1 && lines.get(1).asInt() <= fileLines
                    && lines.get(0).asInt() <= lines.get(1).asInt(), "строки узла вне файла: " + node.get("id"));
        }
    }

    // ---------- правка ----------

    /** Эквивалентная правка: текст меняется, смысл — нет. */
    private record EditCase(String label, Predicate<JsonNode> picks, Function<JsonNode, String> newText) {}

    private static final List<EditCase> EDIT_CASES = List.of(
            new EditCase("условие if → (условие)", n -> kind(n, "if") && editable(n), n -> "(" + code(n) + ")"),
            new EditCase("условие while/do → (условие)", n -> kind(n, "loop") && editable(n), n -> "(" + code(n) + ")"),
            new EditCase("оператор как есть", n -> kind(n, "stmt") && editable(n), RobustnessCorpusTest::code),
            new EditCase("return как есть", n -> kind(n, "return") && editable(n), RobustnessCorpusTest::code),
            new EditCase("throw как есть", n -> kind(n, "throw") && editable(n), RobustnessCorpusTest::code));

    private static void tryEdit(List<String> problems, String name, String source, JsonNode node, EditCase edit)
            throws Exception {
        HttpResponse<String> fresh = send("POST", "/api/projects", Map.of("fileName", name + ".java", "source", source));
        String id = JSON.readTree(fresh.body()).get("id").asText();
        HttpResponse<String> response = send("POST", "/api/projects/" + id + "/commands", Map.of("type", "editStatement",
                "nodeId", node.get("id").asText(), "text", edit.newText().apply(node), "baseVersion", 1));
        if (response.statusCode() != 200) {
            problems.add("правка «" + edit.label() + "» → " + response.statusCode() + ": " + shorten(response.body()));
            return;
        }
        JsonNode result = JSON.readTree(response.body());
        expect(problems, result.get("ok").asBoolean(), "правка «" + edit.label() + "» узла «" + shorten(code(node))
                + "» отклонена: " + result.get("compileErrors"));
    }

    private static JsonNode firstNode(Map<String, JsonNode> blueprints, Predicate<JsonNode> picks) {
        for (JsonNode blueprint : blueprints.values()) {
            for (JsonNode node : blueprint.get("nodes")) {
                if (picks.test(node)) {
                    return node;
                }
            }
        }
        return null;
    }

    private static boolean kind(JsonNode node, String kind) {
        return node.get("kind").asText().equals(kind);
    }

    private static boolean editable(JsonNode node) {
        return node.path("editable").asBoolean(false) && !node.path("code").isNull();
    }

    private static String code(JsonNode node) {
        return node.get("code").asText();
    }

    // ---------- проверка ----------

    private record GateOutcome(String status, String reason, int inputs, long millis) {}

    /** Запускает проверку и ждёт итога не дольше {@link #GATE_LIMIT}. null — проверка сломалась (записано в problems). */
    private static GateOutcome gate(List<String> problems, String id, String original, String candidate, String intent,
                                    String label) throws Exception {
        long start = System.nanoTime();
        HttpResponse<String> started = send("POST", "/api/projects/" + id + "/gate",
                Map.of("source", candidate, "intent", intent, "originalSource", original));
        if (started.statusCode() != 200) {
            problems.add("проверка " + label + " → " + started.statusCode() + ": " + shorten(started.body()));
            return null;
        }
        String jobId = JSON.readTree(started.body()).get("jobId").asText();
        long deadline = start + GATE_LIMIT.toNanos();
        while (System.nanoTime() < deadline) {
            HttpResponse<String> polled = send("GET", "/api/jobs/" + jobId, null);
            if (polled.statusCode() != 200) {
                problems.add("опрос проверки " + label + " → " + polled.statusCode());
                return null;
            }
            JsonNode verdict = JSON.readTree(polled.body());
            String status = verdict.get("status").asText();
            if (!status.equals("RUNNING")) {
                String reason = verdict.get("incompleteReason").isNull() ? null : verdict.get("incompleteReason").asText();
                return new GateOutcome(status, reason, verdict.get("inputs").asInt(), (System.nanoTime() - start) / 1_000_000);
            }
            Thread.sleep(200);
        }
        problems.add("проверка " + label + " не закончилась за " + GATE_LIMIT.toSeconds() + " с");
        return null;
    }

    // ---------- HTTP и мелочи ----------

    private static HttpResponse<String> send(String method, String path, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path))
                .timeout(Duration.ofSeconds(90));
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8));
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void expect(List<String> problems, boolean condition, String problem) {
        if (!condition) {
            problems.add(problem);
        }
    }

    private static String shorten(String text) {
        String one = text.replaceAll("\\s+", " ").strip();
        return one.length() <= 160 ? one : one.substring(0, 159) + "…";
    }
}
