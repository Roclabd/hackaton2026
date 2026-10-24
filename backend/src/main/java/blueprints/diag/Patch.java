package blueprints.diag;

/**
 * Готовая заплатка к находке.
 *
 * @param id           id находки + суффикс, например R1-1-index
 * @param intent       REFACTOR — поведение не меняется; BUGFIX — меняется намеренно
 * @param source       полный текст файла с заплаткой — его фронт отправляет на проверку (POST /gate)
 * @param changedLines [первая, последняя] строка новой версии, занятые заменой
 */
public record Patch(String id, String intent, String title, String description, String source, int[] changedLines) {

    public static final String REFACTOR = "REFACTOR";
    public static final String BUGFIX = "BUGFIX";
}
