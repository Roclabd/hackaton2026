package blueprints.gate;

import blueprints.project.Project;
import blueprints.project.ProjectStore;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.util.Map;
import java.util.Optional;

/**
 * Маршруты прогонов и проверки (docs/ПРОТОТИП_КОНТРАКТ.md):
 * POST /api/projects/{id}/run, POST /api/projects/{id}/gate, GET /api/jobs/{jobId}.
 * Подключает их главный агент из ApiRoutes. Контроллер только принимает и отдаёт данные.
 */
public final class GateRoutes {

    /** Как у загрузки проекта: защита от случайно отправленного мусора. */
    private static final int MAX_TEXT_CHARS = 1_000_000;

    private final ProjectStore store;
    private final GateService gate;

    private GateRoutes(ProjectStore store, GateService gate) {
        this.store = store;
        this.gate = gate;
    }

    public static void register(JavalinConfig config, ProjectStore store) {
        register(config, store, new GateService());
    }

    static void register(JavalinConfig config, ProjectStore store, GateService gate) {
        GateRoutes routes = new GateRoutes(store, gate);
        config.routes.post("/api/projects/{id}/run", routes::run);
        config.routes.post("/api/projects/{id}/gate", routes::startGate);
        config.routes.get("/api/jobs/{jobId}", routes::job);
    }

    /** Тело POST /run. */
    record RunRequest(String input) {}

    /** Тело POST /gate. originalSource — эталон; если нет, эталон — текущая версия проекта. */
    record GateRequest(String source, String intent, String originalSource) {}

    private void run(Context ctx) {
        Optional<Project> project = project(ctx);
        RunRequest request = body(ctx, RunRequest.class);
        if (project.isEmpty() || request == null) {
            return;
        }
        String input = request.input() == null ? "" : request.input();
        if (input.length() > MAX_TEXT_CHARS) {
            error(ctx, 400, "Вход слишком большой: больше " + MAX_TEXT_CHARS + " символов");
            return;
        }
        try {
            ctx.json(gate.runOnce(project.get().source(), input));
        } catch (IllegalArgumentException e) {
            error(ctx, 400, e.getMessage());
        } catch (RuntimeException e) {
            error(ctx, 500, "Не удалось выполнить прогон: " + e.getMessage());
        }
    }

    private void startGate(Context ctx) {
        Optional<Project> project = project(ctx);
        GateRequest request = body(ctx, GateRequest.class);
        if (project.isEmpty() || request == null) {
            return;
        }
        if (request.source() == null || request.source().isBlank()) {
            error(ctx, 400, "Пустой код кандидата");
            return;
        }
        if (request.source().length() > MAX_TEXT_CHARS
                || (request.originalSource() != null && request.originalSource().length() > MAX_TEXT_CHARS)) {
            error(ctx, 400, "Код слишком большой: больше " + MAX_TEXT_CHARS + " символов");
            return;
        }
        Intent intent = parseIntent(request.intent());
        if (intent == null) {
            error(ctx, 400, "Неизвестное намерение «" + request.intent() + "»: ожидается REFACTOR или BUGFIX");
            return;
        }
        String original = request.originalSource() == null || request.originalSource().isBlank() ? null : request.originalSource();
        String jobId = gate.start(project.get(), original, request.source(), intent);
        ctx.json(Map.of("jobId", jobId));
    }

    private void job(Context ctx) {
        String jobId = ctx.pathParam("jobId");
        gate.job(jobId).ifPresentOrElse(ctx::json, () -> error(ctx, 404, "Проверка не найдена: " + jobId));
    }

    private Optional<Project> project(Context ctx) {
        String id = ctx.pathParam("id");
        Optional<Project> project = store.find(id);
        if (project.isEmpty()) {
            error(ctx, 404, "Проект не найден: " + id);
        }
        return project;
    }

    /** Неверный JSON — ошибка клиента (400), а не сервера (500). */
    private static <T> T body(Context ctx, Class<T> type) {
        try {
            T value = ctx.bodyAsClass(type);
            if (value == null) {
                error(ctx, 400, "Пустое тело запроса");
            }
            return value;
        } catch (Exception e) {
            error(ctx, 400, "Неверный JSON в теле запроса");
            return null;
        }
    }

    /** null — неизвестное значение; пустое — по умолчанию REFACTOR. */
    private static Intent parseIntent(String value) {
        if (value == null || value.isBlank()) {
            return Intent.REFACTOR;
        }
        try {
            return Intent.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void error(Context ctx, int status, String message) {
        if (ctx.statusCode() < 400) {
            ctx.status(status).json(Map.of("error", message));
        }
    }
}
