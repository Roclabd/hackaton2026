package blueprints.api;

import io.javalin.http.Context;

/**
 * Чтение тела запроса: кривой JSON, null, неверные типы или дробные числа там, где ждём целое, — это 400
 * с понятным текстом, а не 500 (аудит v3, A1/A2). Ошибки самого сервера сюда не попадают.
 */
final class Bodies {

    private Bodies() {}

    static <T> T read(Context ctx, Class<T> type) {
        T body;
        try {
            body = ctx.bodyAsClass(type);
        } catch (Exception e) {
            // Javalin пробрасывает ошибки Jackson (IOException) без объявления — ловим любые ошибки чтения тела
            throw ApiException.badRequest("Неверное тело запроса: ожидался JSON " + type.getSimpleName()
                    + " (" + rootMessage(e) + ")");
        }
        if (body == null) {
            throw ApiException.badRequest("Пустое тело запроса");
        }
        return body;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
