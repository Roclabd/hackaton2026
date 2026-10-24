package blueprints.project;

/** Проекта с таким id нет — например, сервер перезапускали. API отвечает 404. */
public final class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(String id) {
        super("Проект не найден: " + id);
    }
}
