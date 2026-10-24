package blueprints.project;

/** Правку сделали к устаревшей версии: кто-то успел сохранить раньше. API отвечает 409. */
public final class VersionConflictException extends RuntimeException {

    public VersionConflictException(int serverVersion, int requestedVersion) {
        super("Код уже изменился: версия на сервере " + serverVersion
                + ", а правка сделана к версии " + requestedVersion);
    }
}
