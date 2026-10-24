package blueprints.gate;

import blueprints.parse.ParseOutcome;
import blueprints.parse.SourceParser;
import blueprints.runner.CompileResult;
import blueprints.runner.InMemoryCompiler;
import blueprints.runner.MainClassFinder;
import blueprints.runner.StackSensitive;
import blueprints.runner.WorkerProtocol;

import java.util.List;

/**
 * Исходник → готовая к запуску версия: разбор, поиск главного класса по дереву, компиляция в памяти.
 * Компилирует сервер (javac у него уже прогрет), рабочему процессу передаётся готовый байткод.
 */
final class ProgramBuilder {

    /**
     * Либо программа, либо ошибки — что-то одно.
     *
     * @param launchable есть ли у запускаемого класса main (без него обе версии одинаково не запустятся)
     */
    record Built(WorkerProtocol.Program program, String mainClass, boolean launchable, List<BuildError> errors) {
        boolean ok() {
            return program != null;
        }
    }

    private final SourceParser parser = new SourceParser();
    private final InMemoryCompiler compiler;

    ProgramBuilder(InMemoryCompiler compiler) {
        this.compiler = compiler;
    }

    /**
     * @param runClass какой класс запускать; null — главный класс по дереву разбора этого же исходника
     * @param trapExit ловить System.exit без перезапуска процесса (см. ExitTrap)
     */
    Built build(String name, String source, String runClass, boolean trapExit) {
        ParseOutcome parsed = parser.parse(source);
        if (parsed.unit().isEmpty()) {
            return failed(parsed.errors().stream().map(e -> new BuildError(e.line(), e.column(), e.message())).toList());
        }
        String fileClass;
        try {
            fileClass = MainClassFinder.find(parsed.unit().get());
        } catch (IllegalArgumentException e) {
            return failed(List.of(new BuildError(1, 1, e.getMessage())));
        }
        CompileResult compiled = compiler.compile(fileClass, source);
        if (!compiled.ok()) {
            return failed(compiled.messages().stream().filter(m -> m.kind().equals("ERROR"))
                    .map(m -> new BuildError(Math.max(1, m.line()), Math.max(1, m.column()), m.text())).toList());
        }
        String mainClass = runClass != null ? runClass : fileClass;
        boolean launchable = MainClassFinder.hasLaunchableMain(parsed.unit().get(), mainClass);
        return new Built(new WorkerProtocol.Program(name, mainClass, compiled.classes(), trapExit,
                StackSensitive.needsPlainJvm(parsed.unit().get())), mainClass,
                launchable, List.of());
    }

    private static Built failed(List<BuildError> errors) {
        List<BuildError> nonEmpty = errors.isEmpty() ? List.of(new BuildError(1, 1, "Код не собирается")) : errors;
        return new Built(null, null, false, nonEmpty);
    }

    /** Ошибки одной строкой — для сообщения об ошибке эталона или кнопки «Запустить». */
    static String describe(List<BuildError> errors) {
        BuildError first = errors.get(0);
        String more = errors.size() > 1 ? " (и ещё " + (errors.size() - 1) + ")" : "";
        return "строка " + first.line() + ", колонка " + first.column() + ": " + first.text() + more;
    }
}
