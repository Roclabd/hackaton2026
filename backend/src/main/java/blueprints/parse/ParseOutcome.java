package blueprints.parse;

import com.github.javaparser.ast.CompilationUnit;

import java.util.List;
import java.util.Optional;

/**
 * Результат разбора: дерево разбора, если код корректен, иначе — список ошибок.
 * Частично разобранное дерево наружу не отдаём: строить схему по нему опаснее, чем показать последнюю рабочую.
 */
public record ParseOutcome(Optional<CompilationUnit> unit, List<ParseError> errors) {

    public boolean ok() {
        return unit.isPresent();
    }

    static ParseOutcome success(CompilationUnit unit) {
        return new ParseOutcome(Optional.of(unit), List.of());
    }

    static ParseOutcome failure(List<ParseError> errors) {
        return new ParseOutcome(Optional.empty(), List.copyOf(errors));
    }
}
