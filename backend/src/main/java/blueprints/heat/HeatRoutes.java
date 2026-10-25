package blueprints.heat;

import blueprints.project.Project;
import blueprints.project.ProjectStore;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.util.Map;

/** POST /api/projects/{id}/heat; отсутствие input включает обычный генератор входов. */
public final class HeatRoutes {
    private HeatRoutes() {}

    public static void register(JavalinConfig config, ProjectStore store) {
        register(config, store, new HeatService());
    }

    static void register(JavalinConfig config, ProjectStore store, HeatService service) {
        config.routes.post("/api/projects/{id}/heat", ctx -> heat(ctx, store, service));
    }

    private static void heat(Context ctx, ProjectStore store, HeatService service) {
        Project project = store.find(ctx.pathParam("id")).orElse(null);
        if (project == null) {
            error(ctx, 404, "Проект не найден: " + ctx.pathParam("id"));
            return;
        }
        JsonNode request;
        try {
            request = ctx.bodyAsClass(JsonNode.class);
        } catch (Exception malformed) {
            error(ctx, 400, "Неверный JSON в теле запроса");
            return;
        }
        if (request == null || !request.isObject() || !request.path("methodId").isTextual()
                || request.path("methodId").asText().isBlank()) {
            error(ctx, 400, "Не указан метод (methodId)");
            return;
        }
        JsonNode input = request.get("input");
        if (input != null && !input.isNull() && !input.isTextual()) {
            error(ctx, 400, "Поле input должно быть строкой");
            return;
        }
        try {
            ctx.json(service.measure(project, request.get("methodId").asText(),
                    input == null || input.isNull() ? null : input.asText()));
        } catch (HeatService.RequestException expected) {
            error(ctx, expected.status(), expected.getMessage());
        } catch (RuntimeException unexpected) {
            error(ctx, 500, "Не удалось выполнить замер тепловой карты");
        }
    }

    private static void error(Context ctx, int status, String message) {
        ctx.status(status).json(Map.of("error", message));
    }
}
