package blueprints.api;

import io.javalin.http.Context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Примеры для диалога «Открыть пример»: Java-файлы из корпуса.
 *
 * Корпус ищется так: переменная окружения CORPUS_DIR, иначе ./corpus, иначе ../corpus
 * (из backend/ при разработке, из корня в Docker-образе).
 */
final class ExampleController {

    /** Подписи для известных примеров; остальные показываются по имени файла. */
    private static final Map<String, String[]> KNOWN = Map.of(
            "MatchDemo.java", new String[]{"Подсчёт совпадающих пар", "Вложенный цикл · сценарий демо"},
            "MiniLang.java", new String[]{"Интерпретатор MiniLang", "1100 строк · 36 классов"},
            "Slide5Trap.java", new String[]{"Ловушка со слайда 5", "Три версии слияния по id"});

    void list(Context ctx) {
        ctx.json(examples().stream().map(path -> {
            String fileName = path.getFileName().toString();
            String[] info = KNOWN.getOrDefault(fileName, new String[]{fileName, relative(path)});
            return Map.of("fileName", fileName, "title", info[0], "description", info[1]);
        }).toList());
    }

    void get(Context ctx) throws IOException {
        String fileName = ctx.pathParam("fileName");
        Path path = examples().stream()
                .filter(p -> p.getFileName().toString().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Пример не найден: " + fileName));
        ctx.json(Map.of("fileName", fileName, "source", Files.readString(path, StandardCharsets.UTF_8)));
    }

    private static List<Path> examples() {
        Optional<Path> corpus = corpusDir();
        if (corpus.isEmpty()) {
            return List.of();
        }
        List<Path> result = new ArrayList<>();
        for (String folder : List.of("ours", "traps", "organizers")) {
            Path dir = corpus.get().resolve(folder);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dir, 3)) {
                files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(result::add);
            } catch (IOException e) {
                // папку не удалось прочитать — просто не показываем её примеры
            }
        }
        return result;
    }

    private static Optional<Path> corpusDir() {
        String fromEnv = System.getenv("CORPUS_DIR");
        List<Path> candidates = fromEnv == null ? List.of(Path.of("corpus"), Path.of("../corpus"))
                : List.of(Path.of(fromEnv));
        return candidates.stream().filter(Files::isDirectory).findFirst();
    }

    private static String relative(Path path) {
        return corpusDir().map(c -> c.relativize(path).toString().replace('\\', '/')).orElse(path.toString());
    }
}
