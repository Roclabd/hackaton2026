package blueprints.export;

import blueprints.gate.GateService;
import blueprints.gate.GateService.CompletedCheck;
import blueprints.project.ProjectStore;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Маршруты экспорта тестов по завершённой проверке:
 * GET /api/projects/{id}/export/junit?jobId=… — ZIP с мини-проектом Maven;
 * GET /api/projects/{id}/export/junit/preview?jobId=… — JSON для окна экспорта (имя файла, число тестов, первый тест).
 * Контроллер только принимает и отдаёт данные; содержимое собирает {@link JUnitExporter}.
 */
public final class ExportRoutes {

    private final ProjectStore store;
    private final Function<String, Optional<CompletedCheck>> checks;

    private ExportRoutes(ProjectStore store, Function<String, Optional<CompletedCheck>> checks) {
        this.store = store;
        this.checks = checks;
    }

    /** Проверки ищутся среди всех, запущенных в этом процессе (маршрутами gate). */
    public static void register(JavalinConfig config, ProjectStore store) {
        register(config, store, GateService::completed);
    }

    static void register(JavalinConfig config, ProjectStore store, Function<String, Optional<CompletedCheck>> checks) {
        ExportRoutes routes = new ExportRoutes(store, checks);
        config.routes.get("/api/projects/{id}/export/junit", routes::download);
        config.routes.get("/api/projects/{id}/export/junit/preview", routes::preview);
    }

    /** Ответ предпросмотра. */
    record Preview(String zipName, String testFile, String framework, int tests, int skipped, String firstTest) {}

    private void download(Context ctx) {
        build(ctx).ifPresent(export -> {
            ctx.contentType("application/zip");
            ctx.header("Content-Disposition", contentDisposition(export.zipName()));
            ctx.result(JUnitExporter.zip(export));
        });
    }

    private void preview(Context ctx) {
        build(ctx).ifPresent(export -> ctx.json(new Preview(export.zipName(), export.testFile(), JUnitExporter.FRAMEWORK,
                export.tests(), export.skipped(), export.firstTest())));
    }

    /** Общая часть: проект, проверка, сборка экспорта. Пусто — ответ с ошибкой уже записан. */
    private Optional<JUnitExport> build(Context ctx) {
        String projectId = ctx.pathParam("id");
        if (store.find(projectId).isEmpty()) {
            error(ctx, 404, "Проект не найден: " + projectId);
            return Optional.empty();
        }
        String jobId = ctx.queryParam("jobId");
        if (jobId == null || jobId.isBlank()) {
            error(ctx, 400, "Не указан jobId — экспорт делается по завершённой проверке");
            return Optional.empty();
        }
        Optional<CompletedCheck> check = checks.apply(jobId);
        if (check.isEmpty()) {
            error(ctx, 409, "Нет завершённой проверки " + jobId + ": дождитесь итога проверки и повторите экспорт");
            return Optional.empty();
        }
        try {
            return Optional.of(JUnitExporter.export(jobId, check.get()));
        } catch (JUnitExporter.NotExportable e) {
            error(ctx, 409, e.getMessage());
            return Optional.empty();
        }
    }

    /** Имя класса может быть не латиницей: тогда в заголовке ещё и filename* в UTF-8 (RFC 6266). */
    static String contentDisposition(String fileName) {
        String ascii = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (ascii.equals(fileName)) {
            return "attachment; filename=\"" + fileName + "\"";
        }
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    private static void error(Context ctx, int status, String message) {
        ctx.status(status).json(Map.of("error", message));
    }
}
