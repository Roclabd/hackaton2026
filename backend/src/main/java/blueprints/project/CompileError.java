package blueprints.project;

/** Ошибка компилятора javac: строка и колонка с 1, текст как у javac. */
public record CompileError(int line, int column, String text) {}
