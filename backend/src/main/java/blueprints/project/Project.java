package blueprints.project;

import blueprints.graph.Graph;
import blueprints.parse.ParseError;
import com.github.javaparser.ast.CompilationUnit;

import java.util.List;
import java.util.Optional;

/**
 * Загруженный монолит в конкретной версии. Неизменяемый: любая правка даёт новую версию.
 *
 * @param source        текущий текст — может не разбираться, если пользователь в процессе правки
 * @param errors        ошибки разбора текущего текста (пусто, если код корректен)
 * @param compileErrors ошибки javac для текущего текста; пусто, если компилируется или если он даже не разбирается
 * @param lastGood      дерево разбора последней корректной версии — источник правды для схемы и проверок
 * @param map           схема уровня 0 для lastGood
 */
public record Project(String id, int version, String fileName, String source, List<ParseError> errors,
                      List<CompileError> compileErrors, Optional<CompilationUnit> lastGood, Graph map) {}
