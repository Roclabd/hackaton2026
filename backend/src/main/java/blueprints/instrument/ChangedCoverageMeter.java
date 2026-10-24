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
            List<Side> sides = new ArrayList<>();
            prepareSide(candidate, changed.inCandidate(), mainClass, false).ifPresent(sides::add);
            prepareSide(original, changed.removed(), mainClass, true).ifPresent(sides::add);
            runs.addAll(runSides(sides, inputs, timeout));
        } catch (RuntimeException error) {
            issue = "Не удалось измерить покрытие изменённого кода: " + error.getMessage();
        }
        if (runs.isEmpty() && issue == null) {
            issue = "Покрытие изменённого кода не измерено: изменённые строки не содержат измеримых операторов";
        }
        return new CoverageMeasurement(runs, issue);
    }

    /** Одна сторона замера: копия со счётчиками, собранная и готовая к запуску. */
    private record Side(InstrumentedSource instrumented, WorkerProtocol.Program program, boolean onOriginal) {}

    private Optional<Side> prepareSide(String measured, SortedSet<Integer> lines, String mainClass, boolean onOriginal) {
        if (lines.isEmpty()) return Optional.empty();
        Optional<InstrumentedSource> instrumented = CoverageInstrumenter.instrument(measured, lines);
        if (instrumented.isEmpty()) {
            return Optional.empty();
        }
        String name = onOriginal ? PROGRAM + "-original" : PROGRAM + "-candidate";
        Optional<WorkerProtocol.Program> program = compile(name, instrumented.get().source(), mainClass);
        if (program.isEmpty()) {
            throw new IllegalStateException("копия " + (onOriginal ? "эталона" : "кандидата") + " со счётчиками не собирается");
        }
        return Optional.of(new Side(instrumented.get(), program.get(), onOriginal));
    }

    /**
     * Обе стороны — одним пулом рабочих процессов: запуск JVM на Windows стоит до секунды, а раньше замер
     * поднимал свой пул дважды подряд. id задания = номер стороны × число входов + номер входа.
     */
    private List<CoverageRuns> runSides(List<Side> sides, List<String> inputs, Duration timeout) {
        if (sides.isEmpty()) {
            return List.of();
        }
        int[][][] hits = new int[sides.size()][inputs.size()][];
        List<WorkerProtocol.Job> jobs = new ArrayList<>();
        for (int s = 0; s < sides.size(); s++) {
            for (int i = 0; i < inputs.size(); i++) {
                jobs.add(new WorkerProtocol.Job(s * inputs.size() + i, sides.get(s).program().name(), inputs.get(i)));
            }
        }
        runner.runAll(sides.stream().map(Side::program).toList(), jobs, timeout, workers, (job, result) -> {
            int side = job.id() / inputs.size();
            hits[side][job.id() % inputs.size()] = result.unknown() ? null : result.coverageHits();
        });
        List<CoverageRuns> runs = new ArrayList<>();
        for (int s = 0; s < sides.size(); s++) {
            runs.add(new CoverageRuns(sides.get(s).instrumented(), sides.get(s).onOriginal(), hits[s]));
        }
        return runs;
    }

    /** Копия собирается тем же компилятором, что и проверяемые версии; ловушка System.exit — по тем же правилам. */
    private Optional<WorkerProtocol.Program> compile(String name, String source, String mainClass) {
        ParseOutcome parsed = new SourceParser().parse(source);
        if (parsed.unit().isEmpty()) {
            return Optional.empty();
        }
        CompileResult compiled = compiler.compile(MainClassFinder.find(parsed.unit().get()), source);
        if (!compiled.ok()) {
            return Optional.empty();
        }
        return Optional.of(new WorkerProtocol.Program(name, mainClass, compiled.classes(), ExitTrap.safeFor(source)));
    }
}
