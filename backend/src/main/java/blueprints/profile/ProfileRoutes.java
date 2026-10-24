package blueprints.profile;

import blueprints.project.Project;
import blueprints.project.ProjectStore;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.util.Map;
import java.util.Optional;

/**
 * Маршрут замера роста: POST /api/projects/{id}/profile с телом {@code { "findingId": "R1-1", "patchId": "R1-1-index" }}
 * (patchId необязателен). Логики здесь нет — она в {@link GrowthProfiler}.
 */
public final class ProfileRoutes {

    private ProfileRoutes() {}

    /** Тело запроса. */
    record ProfileRequest(String findingId, String patchId) {}

    public static void register(JavalinConfig config, ProjectStore store) {
        register(config, store, new GrowthProfiler());
    }

    static void register(JavalinConfig config, ProjectStore store, GrowthProfiler profiler) {
        config.routes.post("/api/projects/{id}/profile", ctx -> profile(ctx, store, profiler));
    }

    private static void profile(Context ctx, ProjectStore store, GrowthProfiler profiler) {
        String id = ctx.pathParam("id");
        Optional<Project> project = store.find(id);
        if (project.isEmpty()) {
            error(ctx, 404, "Проект не найден: " + id);
            return;
        }
        ProfileRequest request;
        try {
            request = ctx.bodyAsClass(ProfileRequest.class);
        } catch (Exception e) {
            error(ctx, 400, "Неверный JSON в теле запроса");
            return;
        }
        if (request == null || request.findingId() == null || request.findingId().isBlank()) {
            error(ctx, 400, "Не указана находка (findingId)");
            return;
        }
        String patchId = request.patchId() == null || request.patchId().isBlank() ? null : request.patchId();
        try {
            ctx.json(profiler.profile(project.get(), request.findingId(), patchId));
        } catch (GrowthProfiler.RequestException e) {
            error(ctx, e.status(), e.getMessage());
        } catch (RuntimeException e) {
            error(ctx, 500, "Не удалось выполнить замер роста: " + e.getMessage());
        }
    }

    private static void error(Context ctx, int status, String message) {
        ctx.status(status).json(Map.of("error", message));
    }
}
