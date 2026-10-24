package blueprints.instrument;

import blueprints.parse.ParseOutcome;
import blueprints.parse.SourceParser;
import blueprints.runner.CompileResult;
import blueprints.runner.ExitTrap;
import blueprints.runner.InMemoryCompiler;
import blueprints.runner.MainClassFinder;
import blueprints.runner.ProgramRunner;
import blueprints.runner.StackSensitive;
import blueprints.runner.WorkerProtocol;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;

/**
 * Режим «замер» для проверки: выполнялся ли изменённый код на входах проверки.
 *
 * Шаги: построчный diff эталона и кандидата ({@link ChangedLines}) → копия со счётчиками перед изменёнными
 * операторами ({@link CoverageInstrumenter}) → компиляция → по одному прогону копии на вход, тем же
 * {@link ProgramRunner} и с тем же лимитом времени → счётчики каждого прогона ({@link CoverageRuns}).
 *
 * Кандидат и удалённые/заменённые строки эталона измеряются отдельно: посещаемая правка
 * не должна скрыть удаление ветки, до которой входы не дошли.
 */
public final class ChangedCoverageMeter {

    private static final String PROGRAM = "measured";

    private final InMemoryCompiler compiler;
    private final ProgramRunner runner;
    private final int workers;

    public ChangedCoverageMeter(InMemoryCompiler compiler, ProgramRunner runner, int workers) {
        this.compiler = compiler;
        this.runner = runner;
        this.workers = workers;
    }

    /**
     * @param mainClass какой класс запускать — тот же, что и в проверке
     * @param inputs    входы проверки в том же порядке (номер входа в {@link CoverageRuns} — индекс в этом списке)
     * @return счётчики обеих сторон и причина неполноты, если реальную правку измерить не удалось
     */
    public CoverageMeasurement measure(String original, String candidate, String mainClass,
                                          List<String> inputs, Duration timeout) {
        if (original.equals(candidate) || DeclarationChanges.sameCode(original, candidate)) {
            return CoverageMeasurement.unchanged();
        }
        if (StackSensitive.needsPlainJvm(SourceParser.parseOrThrow(original))
                || StackSensitive.needsPlainJvm(SourceParser.parseOrThrow(candidate))) {
            return CoverageMeasurement.failed("Покрытие изменённого кода не измерено: программа читает или печатает стек "
                    + "и запускается отдельной JVM без счётчиков");
        }
        ChangedLines changed = ChangedLines.between(original, candidate);
        if (changed.none()) {
            // Отступ внутри text block может менять значение: strip-diff не доказывает отсутствие правки.
            return CoverageMeasurement.failed("Покрытие изменённого кода не измерено: изменён значимый пробельный текст");
        }
        List<CoverageRuns> runs = new ArrayList<>();
        String issue = DeclarationChanges.between(original, candidate)
                ? "Покрытие части изменений не измерено: изменены поля, сигнатуры или другие объявления" : null;
        try {
            measureSide(candidate, changed.inCandidate(), mainClass, inputs, timeout, false).ifPresent(runs::add);
            measureSide(original, changed.removed(), mainClass, inputs, timeout, true).ifPresent(runs::add);
        } catch (RuntimeException error) {
            issue = "Не удалось измерить покрытие изменённого кода: " + error.getMessage();
        }
        if (runs.isEmpty() && issue == null) {
            issue = "Покрытие изменённого кода не измерено: изменённые строки не содержат измеримых операторов";
        }
        return new CoverageMeasurement(runs, issue);
    }

    private Optional<CoverageRuns> measureSide(String measured, SortedSet<Integer> lines, String mainClass,
                                               List<String> inputs, Duration timeout, boolean onOriginal) {
        if (lines.isEmpty()) return Optional.empty();
        Optional<InstrumentedSource> instrumented = CoverageInstrumenter.instrument(measured, lines);
        if (instrumented.isEmpty()) {
            return Optional.empty();
        }
        Optional<WorkerProtocol.Program> program = compile(instrumented.get().source(), mainClass);
        if (program.isEmpty()) {
            throw new IllegalStateException("копия " + (onOriginal ? "эталона" : "кандидата") + " со счётчиками не собирается");
        }
        int[][] hits = new int[inputs.size()][];
        List<WorkerProtocol.Job> jobs = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            jobs.add(new WorkerProtocol.Job(i, PROGRAM, inputs.get(i)));
        }
        runner.runAll(List.of(program.get()), jobs, timeout, workers,
                (job, result) -> hits[job.id()] = result.unknown() ? null : result.coverageHits());
        return Optional.of(new CoverageRuns(instrumented.get(), onOriginal, hits));
    }

    /** Копия собирается тем же компилятором, что и проверяемые версии; ловушка System.exit — по тем же правилам. */
    private Optional<WorkerProtocol.Program> compile(String source, String mainClass) {
        ParseOutcome parsed = new SourceParser().parse(source);
        if (parsed.unit().isEmpty()) {
            return Optional.empty();
        }
        CompileResult compiled = compiler.compile(MainClassFinder.find(parsed.unit().get()), source);
        if (!compiled.ok()) {
            return Optional.empty();
        }
        return Optional.of(new WorkerProtocol.Program(PROGRAM, mainClass, compiled.classes(), ExitTrap.safeFor(source)));
    }
}
