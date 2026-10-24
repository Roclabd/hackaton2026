package blueprints.parse;

/** Ошибка разбора кода: где и что. Нумерация строк и колонок — с 1, как в редакторе. */
public record ParseError(int line, int column, String message) {}
