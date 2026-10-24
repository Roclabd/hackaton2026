package blueprints.project;

import blueprints.runner.CompileResult;
import blueprints.runner.InMemoryCompiler;
import blueprints.runner.MainClassFinder;
import com.github.javaparser.ast.CompilationUnit;

import java.util.List;

/**
 * Компилируется ли код. JavaParser проверяет только синтаксис: {@code if (percent = 100)} для него корректен,
 * а javac отвергает («int cannot be converted to boolean»). Поэтому при загрузке и каждой правке код
 * дополнительно компилируется в памяти, и человек видит ошибку сразу, а не только при запуске проверки.
 */
final class CompileCheck {

    private final InMemoryCompiler compiler = new InMemoryCompiler();

    /** Ошибки javac; пусто — компилируется. Вызывать только для синтаксически корректного кода. */
    List<CompileError> errors(CompilationUnit unit, String source) {
        String mainClass;
        try {
            mainClass = MainClassFinder.find(unit);
        } catch (IllegalArgumentException noTypes) {
            return List.of(new CompileError(1, 1, noTypes.getMessage()));
        }
        CompileResult result = compiler.compile(mainClass, source);
        if (result.ok()) {
            return List.of();
        }
        return result.messages().stream()
                .filter(m -> m.kind().equals("ERROR"))
                .map(m -> new CompileError(Math.max(1, m.line()), Math.max(1, m.column()), m.text()))
                .toList();
    }
}
