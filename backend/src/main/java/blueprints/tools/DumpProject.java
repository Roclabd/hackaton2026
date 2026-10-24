package blueprints.tools;

import blueprints.App;
import blueprints.project.ProjectStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Выгружает настоящий ответ POST /api/projects для файла — фикстура, с которой фронт работает без бэкенда.
 * Идёт через настоящий HTTP, чтобы JSON был ровно таким, как отдаёт сервер.
 *
 * Запуск из backend/ (через exec:java не запускать — Maven ждёт потоки HTTP-клиента и зависает):
 *   ./mvnw -q -DskipTests package
 *   java -Dstdout.encoding=UTF-8 -cp target/blueprints.jar blueprints.tools.DumpProject \
 *       ../corpus/ours/MiniLang/MiniLang.java ../frontend/src/fixtures/minilang.project.json
 */
public final class DumpProject {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Использование: DumpProject <файл.java> <куда-сохранить.json>");
            System.exit(2);
        }
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        String source = Files.readString(input, StandardCharsets.UTF_8);
        String body = new ObjectMapper().writeValueAsString(
                Map.of("fileName", input.getFileName().toString(), "source", source));

        Javalin app = App.create(new ProjectStore()).start(0);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/api/projects"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, response.body(), StandardCharsets.UTF_8);
            System.out.println("Статус " + response.statusCode() + ", записано " + response.body().length() + " символов в " + output);
        } finally {
            app.stop();
        }
    }
}
