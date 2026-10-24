package blueprints.api;

import blueprints.project.ProjectNotFoundException;
import blueprints.project.ProjectStore;
import blueprints.project.VersionConflictException;
import io.javalin.config.JavalinConfig;

import java.util.Map;

/**
 * Все маршруты API в одном месте. Контракт описан в docs/API.md и docs/ПРОТОТИП_КОНТРАКТ.md —
 * любое изменение здесь сначала согласуется там.
 */
public final class ApiRoutes {

    private ApiRoutes() {}

    public static void register(JavalinConfig config, ProjectStore store) {
        ProjectController projects = new ProjectController(store);
        BlueprintController blueprints = new BlueprintController(store);
        ExampleController examples = new ExampleController();

        config.routes.get("/api/health", ctx -> ctx.json(Map.of("status", "ok")));

        config.routes.post("/api/projects", projects::upload);
        config.routes.get("/api/projects/{id}", projects::get);
        config.routes.put("/api/projects/{id}/source", projects::updateSource);
        config.routes.get("/api/projects/{id}/methods/{methodId}", blueprints::method);
        config.routes.post("/api/projects/{id}/commands", blueprints::command);

        config.routes.get("/api/examples", examples::list);
        config.routes.get("/api/examples/{fileName}", examples::get);

        ExtraRoutes.register(config, store);
        FrontendRoutes.register(config);

        config.routes.exception(ApiException.class, (e, ctx) ->
                ctx.status(e.status()).json(Map.of("error", e.getMessage())));
        config.routes.exception(ProjectNotFoundException.class, (e, ctx) ->
                ctx.status(404).json(Map.of("error", e.getMessage())));
        config.routes.exception(VersionConflictException.class, (e, ctx) ->
                ctx.status(409).json(Map.of("error", e.getMessage())));
    }
}
