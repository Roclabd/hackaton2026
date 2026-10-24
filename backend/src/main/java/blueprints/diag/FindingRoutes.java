package blueprints.diag;

import blueprints.project.Project;
import blueprints.project.ProjectStore;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.util.Map;
import java.util.Optional;

/** Маршрут находок: GET /api/projects/{id}/findings. Логики здесь нет — она в FindingsService. */
public final class FindingRoutes {

    private FindingRoutes() {}

    public static void register(JavalinConfig config, ProjectStore store) {
        FindingsService service = new FindingsService();
        config.routes.get("/api/projects/{id}/findings", ctx -> findings(ctx, store, service));
    }

    private static void findings(Context ctx, ProjectStore store, FindingsService service) {
        String id = ctx.pathParam("id");
        Optional<Project> project = store.find(id);
        if (project.isEmpty()) {
            // Отвечаем сами, без общего обработчика из ApiRoutes: маршрут должен работать и подключённым отдельно.
            ctx.status(404).json(Map.of("error", "Проект не найден: " + id));
            return;
        }
        ctx.json(service.find(project.get()));
    }
}
