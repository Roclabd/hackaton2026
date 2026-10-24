package blueprints.api;

import blueprints.project.Project;
import blueprints.project.ProjectNotFoundException;
import blueprints.project.ProjectStore;
import io.javalin.http.Context;

/**
 * Загрузка монолита и правка его кода целиком.
 * Контроллер только принимает и отдаёт данные; вся логика — в ProjectStore и пакетах parse/graph.
 */
final class ProjectController {

    /** Монолит по брифу ~1000 строк; 1 МБ — с большим запасом и защитой от случайно загруженного мусора. */
    private static final int MAX_SOURCE_CHARS = 1_000_000;

    private final ProjectStore store;

    ProjectController(ProjectStore store) {
        this.store = store;
    }

    /** POST /api/projects — тело {fileName, source}. */
    void upload(Context ctx) {
        UploadRequest request = Bodies.read(ctx, UploadRequest.class);
        String source = requireSource(request.source());
        String fileName = request.fileName() == null || request.fileName().isBlank() ? "Main.java" : request.fileName();
        Project project = store.create(fileName, source);
        ctx.status(201).json(ProjectResponse.of(project));
    }

    /** GET /api/projects/{id} */
    void get(Context ctx) {
        ctx.json(ProjectResponse.of(find(ctx)));
    }

    /** PUT /api/projects/{id}/source — тело {source, baseVersion}. Конфликт версий — 409 (см. ApiRoutes). */
    void updateSource(Context ctx) {
        SourceUpdateRequest request = Bodies.read(ctx, SourceUpdateRequest.class);
        if (request.baseVersion() == null) {
            throw ApiException.badRequest("Нужно целое поле baseVersion — версия, к которой сделана правка");
        }
        Project updated = store.updateSource(ctx.pathParam("id"), request.baseVersion(), requireSource(request.source()));
        ctx.json(ProjectResponse.of(updated));
    }

    private Project find(Context ctx) {
        String id = ctx.pathParam("id");
        return store.find(id).orElseThrow(() -> new ProjectNotFoundException(id));
    }

    private static String requireSource(String source) {
        if (source == null || source.isBlank()) {
            throw ApiException.badRequest("Пустой исходный код");
        }
        if (source.length() > MAX_SOURCE_CHARS) {
            throw ApiException.badRequest("Файл слишком большой: больше " + MAX_SOURCE_CHARS + " символов");
        }
        return source;
    }

    record UploadRequest(String fileName, String source) {}

    record SourceUpdateRequest(String source, Integer baseVersion) {}
}
