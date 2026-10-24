package blueprints.gate;

/** Ошибка разбора или компиляции кандидата. Строки и колонки — с 1, как в редакторе. */
public record BuildError(int line, int column, String text) {}
