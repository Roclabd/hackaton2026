package blueprints.api;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Раздача собранного фронта (Vite кладёт его в ресурсы /web): /assets/* — файлы, всё остальное — index.html.
 *
 * Свой обработчик вместо staticFiles Javalin: из shaded-jar штатная раздача отдавала index.html на любой путь,
 * и браузер получал HTML вместо JavaScript. Здесь всё явно и проверяется тестом.
 */
final class FrontendRoutes {

    static final String INDEX = "/web/index.html";

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "html", "text/html; charset=utf-8",
            "json", "application/json",
            "svg", "image/svg+xml",
            "png", "image/png",
            "ttf", "font/ttf",
            "woff", "font/woff",
            "woff2", "font/woff2",
            "map", "application/json");

    private FrontendRoutes() {}

    static boolean frontendBuilt() {
        return FrontendRoutes.class.getResource(INDEX) != null;
    }

    static void register(JavalinConfig config) {
        if (!frontendBuilt()) {
            return;
        }
        config.routes.get("/assets/{file}", ctx -> serve(ctx, "/web/assets/" + ctx.pathParam("file")));
        config.routes.get("/", ctx -> serve(ctx, INDEX));
        // Любой другой путь, кроме /api, — приложение само разберёт маршрут (хэш-маршруты, перезагрузка страницы).
        config.routes.error(404, ctx -> {
            boolean appPath = !ctx.path().startsWith("/api") && !ctx.path().startsWith("/assets");
            if (appPath && ctx.method().name().equals("GET")) {
                serve(ctx, INDEX);
            }
        });
    }

    private static void serve(Context ctx, String resource) throws IOException {
        if (resource.contains("..")) {
            ctx.status(400).result("Недопустимый путь");
            return;
        }
        try (InputStream in = FrontendRoutes.class.getResourceAsStream(resource)) {
            if (in == null) {
                ctx.status(404).result("Нет файла");
                return;
            }
            String extension = resource.substring(resource.lastIndexOf('.') + 1);
            ctx.status(200)
                    .contentType(CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"))
                    .header("Cache-Control", resource.startsWith("/web/assets/") ? "public, max-age=31536000, immutable" : "no-cache")
                    .result(in.readAllBytes());
        }
    }
}
