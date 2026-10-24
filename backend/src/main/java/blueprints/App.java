package blueprints;

import blueprints.api.ApiRoutes;
import blueprints.project.ProjectStore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

/**
 * Точка входа: поднимает веб-сервер, который отдаёт API и собранный фронт.
 *
 * Порт берётся из переменной окружения PORT, по умолчанию 8080.
 * Собранный фронт лежит в ресурсах по пути /web (его кладёт туда `npm run build`).
 */
public final class App {

    private App() {}

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        Javalin app = create(new ProjectStore()).start(port);
        System.out.println("Сервер запущен: http://localhost:" + app.port());
    }

    /**
     * JSON без «догадок»: 1.9 не превращается в 1, "2" — в 2, null — в 0 (аудит v3, A2).
     * Неизвестные поля запроса игнорируем — фронт может слать лишнее.
     */
    static JavalinJackson strictJson() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, false)
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(MapperFeature.ALLOW_COERCION_OF_SCALARS, false);
        return new JavalinJackson(mapper, false);
    }

    /** Собирает приложение, не запуская его. Отдельно — чтобы тесты могли поднять сервер на свободном порту. */
    public static Javalin create(ProjectStore store) {
        return Javalin.create(config -> {
            config.jsonMapper(strictJson());
            ApiRoutes.register(config, store);
        });
    }
}
