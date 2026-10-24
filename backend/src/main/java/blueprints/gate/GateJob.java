package blueprints.gate;

import blueprints.inputs.InputGenerator;
import blueprints.inputs.LabeledInput;
import blueprints.instrument.ChangedCoverage;
import blueprints.instrument.ChangedCoverageMeter;
import blueprints.instrument.CoverageMeasurement;
import blueprints.runner.ExitTrap;
import blueprints.runner.ProgramRunner;
import blueprints.runner.RunResult;
import blueprints.runner.WorkerProtocol;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Одна проверка кандидата против эталона. Выполняется в фоне; текущее состояние — {@link #verdict()}.
 *
 * Любой сбой самого инструмента превращается в INCOMPLETE с причиной, а не в падение:
 * человек должен видеть «проверить не удалось, потому что…», а не ошибку 500.
 */
final class GateJob implements Runnable {

    static final String ORIGINAL = "original";
    static final String CANDIDATE = "candidate";
    /** Прогонов на вход: оригинал дважды (детектор случайности) и кандидат. */
    private static final int RUNS_PER_INPUT = 3;

    /** Что проверить: эталон, кандидат и намерение. */
    record Request(String fileName, String originalSource, String candidateSource, Intent intent) {}

    private final Request request;
    private final ProgramBuilder builder;
    private final ProgramRunner runner;
    private final InputGenerator inputs;
    private final ChangedCoverageMeter coverage;
    private final Duration timeout;
    private final int workers;
    private final long startNanos = System.nanoTime();

    private List<InputCheck> checks = List.of();
    private volatile Verdict verdict = Verdict.starting();

    GateJob(Request request, ProgramBuilder builder, ProgramRunner runner, InputGenerator inputs,
            ChangedCoverageMeter coverage, Duration timeout, int workers) {
        this.request = request;
        this.builder = builder;
        this.runner = runner;
        this.inputs = inputs;
        this.coverage = coverage;
        this.timeout = timeout;
        this.workers = workers;
    }

    Verdict verdict() {
        return verdict;
    }

    @Override
    public void run() {
        try {
            verdict = check();
        } catch (Throwable t) {
            verdict = Verdict.incomplete("Внутренняя ошибка проверки: " + t, elapsedMs());
        }
    }

    private Verdict check() {
        // Ловушка System.exit — одинаково для обеих версий: окружение оригинала и кандидата должно совпадать.
        boolean trapExit = ExitTrap.safeFor(request.originalSource()) && ExitTrap.safeFor(request.candidateSource());
        ProgramBuilder.Built original = builder.build(ORIGINAL, request.originalSource(), null, trapExit);
        if (!original.ok()) {
            return Verdict.incomplete("Эталон (исходная версия) не собирается: "
                    + ProgramBuilder.describe(original.errors()), elapsedMs());
        }
        if (!original.launchable()) {
            // Без main обе версии одинаково не запустятся, и «совпадение» ничего бы не значило.
            return Verdict.incomplete("В классе " + original.mainClass() + " нет метода main — программу нечем запустить, "
                    + "поведение не проверено. Для библиотеки нужны тесты на её методы", elapsedMs());
        }
        // Главный класс — по дереву эталона: запускаем ровно то, что запускал оригинал.
        ProgramBuilder.Built candidate = builder.build(CANDIDATE, request.candidateSource(), original.mainClass(), trapExit);
        if (!candidate.ok()) {
            return Verdict.buildError(candidate.errors(), elapsedMs());
        }

        List<LabeledInput> generated = inputs.generate(request.fileName(), request.originalSource());
        synchronized (this) {
            checks = new ArrayList<>(generated.stream().map(in -> new InputCheck(in, null, null, null)).toList());
            publish(false);
        }
        CompletableFuture<CoverageMeasurement> measuring = measureChangedCode(original.mainClass(), generated);
        boolean plainJvm = original.program().plainJvm() || candidate.program().plainJvm();
        runner.runAll(List.of(original.program().withPlainJvm(plainJvm), candidate.program().withPlainJvm(plainJvm)),
                jobs(generated), timeout, workers,
                this::onResult);
        CoverageMeasurement measured = measuring.join();
        synchronized (this) {
            return VerdictAssembler.assemble(List.copyOf(checks), request.intent(), true, timeout.toMillis(), elapsedMs(),
                    changedCoverage(measured), measured.incompleteReason(i -> compared(checks.get(i))));
        }
    }

    /**
     * Режим «замер» (план 3.4): копия со счётчиками на изменённых операторах, по прогону на вход — параллельно
     * с основными прогонами, в своём рабочем процессе. Вердикт по этой копии не выносится: она только отвечает,
     * дошли ли входы до изменённого кода. Сбой замера сохраняется как причина неполной проверки.
     */
    private CompletableFuture<CoverageMeasurement> measureChangedCode(String mainClass, List<LabeledInput> generated) {
        List<String> texts = generated.stream().map(LabeledInput::text).toList();
        return CompletableFuture.supplyAsync(
                        () -> coverage.measure(request.originalSource(), request.candidateSource(), mainClass, texts, timeout),
                        task -> Thread.ofVirtual().name("coverage").start(task))
                .exceptionally(error -> CoverageMeasurement.failed("Не удалось измерить покрытие изменённого кода: "
                        + error.getMessage()));
    }

    /** Покрытие считаем только по входам, где эталон и кандидат реально сравнены (совпали или разошлись). */
    private synchronized ChangedCoverage changedCoverage(CoverageMeasurement measured) {
        return measured.summary(i -> compared(checks.get(i)));
    }

    private static boolean compared(InputCheck check) {
        InputCheck.Outcome outcome = check.outcome();
        return outcome == InputCheck.Outcome.MATCH || outcome == InputCheck.Outcome.MISMATCH;
    }

    /** Для входа i: id 3i и 3i+1 — оригинал, 3i+2 — кандидат. Прогоны одного входа идут подряд. */
    private static List<WorkerProtocol.Job> jobs(List<LabeledInput> generated) {
        List<WorkerProtocol.Job> jobs = new ArrayList<>();
        for (int i = 0; i < generated.size(); i++) {
            String text = generated.get(i).text();
            jobs.add(new WorkerProtocol.Job(i * RUNS_PER_INPUT, ORIGINAL, text));
            jobs.add(new WorkerProtocol.Job(i * RUNS_PER_INPUT + 1, ORIGINAL, text));
            jobs.add(new WorkerProtocol.Job(i * RUNS_PER_INPUT + 2, CANDIDATE, text));
        }
        return jobs;
    }

    private synchronized void onResult(WorkerProtocol.Job job, RunResult result) {
        int index = job.id() / RUNS_PER_INPUT;
        checks.set(index, checks.get(index).with(job.id() % RUNS_PER_INPUT, result));
        publish(false);
    }

    private void publish(boolean finished) {
        verdict = VerdictAssembler.assemble(List.copyOf(checks), request.intent(), finished, timeout.toMillis(), elapsedMs());
    }

    private long elapsedMs() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
