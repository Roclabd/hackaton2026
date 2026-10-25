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
import java.util.function.Function;

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
    /** Номер прогона кандидата среди трёх прогонов входа (0 и 1 — эталон). */
    private static final int CANDIDATE_SLOT = 2;
    /**
     * На скольких разных входах подряд эталон должен не уложиться во время, чтобы считать, что программа
     * не завершается (см. {@link HangWatch}). Два — это первые прогоны двух из трёх параллельных процессов:
     * решение через ~5–10 с вместо минут. Больше — дольше: после таймаута процесс перезапускается (~1–4 с).
     */
    static final int HANG_INPUTS_IN_A_ROW = 2;

    /** Что проверить: эталон, кандидат и намерение. */
    record Request(String fileName, String originalSource, String candidateSource, Intent intent) {}

    private final Request request;
    private final ProgramBuilder builder;
    private final ProgramRunner runner;
    private final InputGenerator inputs;
    /** Замер покрытия на исполнителе этой проверки: при досрочной остановке он останавливается вместе с ней. */
    private final Function<ProgramRunner, ChangedCoverageMeter> coverage;
    private final Duration timeout;
    /** Общий лимит на всю проверку: сверх него прогоны останавливаются, вердикт — INCOMPLETE с причиной. */
    private final Duration budget;
    private final int workers;
    private final long startNanos = System.nanoTime();

    private List<InputCheck> checks = List.of();
    /** Сторож текущей серии прогонов; после срабатывания результаты этой серии больше не учитываются. */
    private HangWatch watch = new HangWatch(Integer.MAX_VALUE, () -> {});
    private volatile Verdict verdict = Verdict.starting();
    /** Исполнитель текущей серии прогонов — его останавливает таймер общего лимита. */
    private volatile ProgramRunner active;
    private volatile boolean overBudget;

    GateJob(Request request, ProgramBuilder builder, ProgramRunner runner, InputGenerator inputs,
            Function<ProgramRunner, ChangedCoverageMeter> coverage, Duration timeout, Duration budget, int workers) {
        this.request = request;
        this.builder = builder;
        this.runner = runner;
        this.inputs = inputs;
        this.coverage = coverage;
        this.timeout = timeout;
        this.budget = budget;
        this.workers = workers;
    }

    Verdict verdict() {
        return verdict;
    }

    /** Что проверялось — для экспорта тестов (только чтение). */
    Request request() {
        return request;
    }

    /** Прогоны по входам — для экспорта тестов (только чтение, копия). */
    synchronized List<InputCheck> checks() {
        return List.copyOf(checks);
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

        Thread budgetTimer = startBudgetTimer();
        try {
            return compare(original, candidate);
        } finally {
            budgetTimer.interrupt();
        }
    }

    /**
     * Таймер общего лимита. Зачем: тяжёлая программа (монолит организаторов LibraryManagerMess запускается
     * отдельной JVM, ~1–4 с на прогон) на ~50 входах × 3 прогона шла минутами, и проверка каждой заплатки —
     * столько же: на защите это выглядит зависанием. По истечении лимита прогоны этой проверки останавливаются.
     */
    private Thread startBudgetTimer() {
        return Thread.ofVirtual().name("gate-budget").start(() -> {
            try {
                Thread.sleep(budget);
            } catch (InterruptedException finishedInTime) {
                return;
            }
            overBudget = true;
            ProgramRunner current = active;
            if (current != null) {
                current.cancel();
            }
        });
    }

    /** Сделать исполнитель текущим; если лимит уже вышел — сразу остановить его. */
    private ProgramRunner activate(ProgramRunner checkRunner) {
        active = checkRunner;
        if (overBudget) {
            checkRunner.cancel();
        }
        return checkRunner;
    }

    private Verdict compare(ProgramBuilder.Built original, ProgramBuilder.Built candidate) {
        List<LabeledInput> generated = inputs.generate(request.fileName(), request.originalSource());
        synchronized (this) {
            checks = new ArrayList<>(generated.stream().map(in -> new InputCheck(in, null, null, null)).toList());
            publish(false);
        }
        // Свой исполнитель на проверку: его можно остановить досрочно, не задев другие проверки.
        ProgramRunner checkRunner = activate(runner.forCheck());
        CompletableFuture<CoverageMeasurement> measuring = measureChangedCode(checkRunner, original.mainClass(), generated);
        boolean plainJvm = original.program().plainJvm() || candidate.program().plainJvm();
        List<WorkerProtocol.Program> programs =
                List.of(original.program().withPlainJvm(plainJvm), candidate.program().withPlainJvm(plainJvm));
        String exit = InputGenerator.exitCommand(request.originalSource()).orElse(null);
        if (!runWatched(checkRunner, programs, generated, exitScenariosFirst(generated, exit))) {
            // Эталон не завершается: замер остановлен вместе с прогонами, его итог не нужен.
            return checkExitScenarios(programs, generated, exit);
        }
        CoverageMeasurement measured = measuring.join();
        if (overBudget) {
            return overBudgetVerdict();
        }
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
    private CompletableFuture<CoverageMeasurement> measureChangedCode(ProgramRunner checkRunner, String mainClass,
                                                                     List<LabeledInput> generated) {
        List<String> texts = generated.stream().map(LabeledInput::text).toList();
        ChangedCoverageMeter meter = coverage.apply(checkRunner);
        return CompletableFuture.supplyAsync(
                        () -> meter.measure(request.originalSource(), request.candidateSource(), mainClass, texts, timeout),
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

    /**
     * Порядок прогонов: сначала сценарии, которые заканчиваются командой выхода, потом остальные входы.
     * Для обычной программы порядок ничего не меняет. Для меню, которое на конце ввода не выходит, к моменту
     * досрочной остановки ({@link HangWatch}) сценарии с выходом обычно уже сравнены, и их не нужно гонять заново.
     */
    private static List<Integer> exitScenariosFirst(List<LabeledInput> generated, String exit) {
        List<Integer> first = new ArrayList<>();
        List<Integer> rest = new ArrayList<>();
        for (int i = 0; i < generated.size(); i++) {
            (InputGenerator.endsWithExit(generated.get(i), exit) ? first : rest).add(i);
        }
        first.addAll(rest);
        return first;
    }

    /** Для входа i: id 3i и 3i+1 — оригинал, 3i+2 — кандидат. Прогоны одного входа идут подряд. */
    private static List<WorkerProtocol.Job> jobs(List<LabeledInput> generated, List<Integer> order) {
        List<WorkerProtocol.Job> jobs = new ArrayList<>();
        for (int i : order) {
            String text = generated.get(i).text();
            jobs.add(new WorkerProtocol.Job(i * RUNS_PER_INPUT, ORIGINAL, text));
            jobs.add(new WorkerProtocol.Job(i * RUNS_PER_INPUT + 1, ORIGINAL, text));
            jobs.add(new WorkerProtocol.Job(i * RUNS_PER_INPUT + CANDIDATE_SLOT, CANDIDATE, text));
        }
        return jobs;
    }

    /**
     * Прогоны входов с номерами {@code order} под присмотром {@link HangWatch}.
     *
     * @return false — эталон на нескольких входах подряд не уложился во время, прогоны остановлены досрочно
     */
    private boolean runWatched(ProgramRunner checkRunner, List<WorkerProtocol.Program> programs,
                               List<LabeledInput> generated, List<Integer> order) {
        activate(checkRunner);
        HangWatch current = new HangWatch(HANG_INPUTS_IN_A_ROW, checkRunner::cancel);
        synchronized (this) {
            watch = current;
        }
        checkRunner.runAll(programs, jobs(generated, order), timeout, workers, this::onResult);
        return !current.tripped();
    }

    /**
     * Эталон не завершается на обычных входах — так ведёт себя меню, которое на конце ввода не выходит.
     * Сравниваем только сценарии, которые заканчиваются командой выхода из кода: на них программа должна
     * завершиться штатно. Уже сравненные до остановки сценарии не повторяются, недоделанные прогоняются заново.
     * Найденное расхождение — настоящий результат; иначе INCOMPLETE с причиной: остальные входы не сравнены.
     */
    private Verdict checkExitScenarios(List<WorkerProtocol.Program> programs, List<LabeledInput> generated, String exit) {
        List<Integer> scenarios = new ArrayList<>();
        List<Integer> unfinished = new ArrayList<>();
        synchronized (this) {
            for (int i = 0; i < generated.size(); i++) {
                if (InputGenerator.endsWithExit(generated.get(i), exit)) {
                    scenarios.add(i);
                    if (!checks.get(i).finished()) {
                        checks.set(i, new InputCheck(generated.get(i), null, null, null));
                        unfinished.add(i);
                    }
                }
            }
        }
        boolean scenariosFinish = unfinished.isEmpty() || runWatched(runner.forCheck(), programs, generated, unfinished);
        if (overBudget) {
            return overBudgetVerdict();
        }
        synchronized (this) {
            List<InputCheck> scenarioChecks = scenarios.stream().map(checks::get).toList();
            return VerdictAssembler.assembleNonTerminating(scenarioChecks, request.intent(), timeout.toMillis(),
                    elapsedMs(), new NonTerminating(generated.size(), HANG_INPUTS_IN_A_ROW, exit, scenariosFinish));
        }
    }

    private synchronized Verdict overBudgetVerdict() {
        return VerdictAssembler.assembleOverBudget(List.copyOf(checks), request.intent(), timeout.toMillis(), elapsedMs(),
                budget.toMillis());
    }

    private synchronized void onResult(WorkerProtocol.Job job, RunResult result) {
        if (watch.tripped()) {
            return; // серия остановлена: это итоги убитых и отменённых прогонов
        }
        int index = job.id() / RUNS_PER_INPUT;
        int slot = job.id() % RUNS_PER_INPUT;
        checks.set(index, checks.get(index).with(slot, result));
        publish(false);
        if (slot != CANDIDATE_SLOT) {
            watch.original(index, result.timedOut());
        }
    }

    private void publish(boolean finished) {
        verdict = VerdictAssembler.assemble(List.copyOf(checks), request.intent(), finished, timeout.toMillis(), elapsedMs());
    }

    private long elapsedMs() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
