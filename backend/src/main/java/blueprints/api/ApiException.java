package blueprints.api;

/** Ошибка, которую API отдаёт клиенту как {"error": "..."} с нужным HTTP-статусом. */
public final class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }
}
