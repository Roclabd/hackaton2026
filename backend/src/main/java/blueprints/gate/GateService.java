package blueprints.gate;

import blueprints.inputs.InputGenerator;
import blueprints.instrument.ChangedCoverageMeter;
import blueprints.project.Project;
import blueprints.runner.ExitTrap;
import blueprints.runner.InMemoryCompiler;
import blueprints.runner.ProgramRunner;
import blueprints.runner.RunResult;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Проверка кандидата: прогнать эталон и кандидат на одних входах и вынести вердикт.
 * Проверки идут в фоне; клиент опрашивает {@link #job(String)}.
 */
public final class GateService implements AutoCloseable {

    /** Лимит на один прогон. Дольше — таймаут, вердикт INCOMPLETE, а не MATCHED. */
    static final Duration RUN_TIMEOUT = Duration.ofSeconds(5);
    /** Параллельные проверки. Больше двух одновременно на демо не бывает. */
    private static final int PARALLEL_CHECKS = 2;

    /** Покрытие изменённого кода меряет один рабочий процесс — параллельно с основными прогонами. */
    private static final int COVERAGE_WORKERS = 1;

    private final InMemoryCompiler compiler = new InMemoryCompiler();
    private final ProgramBuilder builder = new ProgramBuilder(compiler);
    private final ProgramRunner runner;
    private final InputGenerator inputs;
    private final ChangedCoverageMeter coverage;
    private final Map<String, GateJob> jobs = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_CHECKS, r -> {
        Thread thread = new Thread(r, "gate");
        thread.setDaemon(true);
        return thread;
    });
    /** Рабочих процессов на одну проверку: ~30 входов × 3 прогона делятся на части. */
    private final int workersPerCheck = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 2));

    public GateService() {
        this(new ProgramRunner(), new InputGenerator());
    }

    public GateService(ProgramRunner runner, InputGenerator inputs) {
        this.runner = runner;
        this.inputs = inputs;
        this.coverage = new ChangedCoverageMeter(compiler, runner, COVERAGE_WORKERS);
    }

    /** Эталон — текущая версия проекта. */
    public String start(Project project, String candidateSource, Intent intent) {
        return start(project, null, candidateSource, intent);
    }

    /**
     * @param originalSource эталон; null — текущая версия проекта (после правки узла проект уже новый,
     *                       и без явного эталона правка сравнивалась бы сама с собой)
     * @return id проверки для опроса
     */
    public String start(Project project, String originalSource, String candidateSource, Intent intent) {
        String original = originalSource != null ? originalSource : project.source();
        GateJob.Request request = new GateJob.Request(project.fileName(), original, candidateSource,
                intent == null ? Intent.REFACTOR : intent);
        GateJob job = new GateJob(request, builder, runner, inputs, coverage, RUN_TIMEOUT, workersPerCheck);
        String id = "j" + nextId.getAndIncrement();
        jobs.put(id, job);
        executor.submit(job);
        return id;
    }

    public Optional<Verdict> job(String jobId) {
        return Optional.ofNullable(jobs.get(jobId)).map(GateJob::verdict);
    }

    /**
     * Один прогон текущего кода на заданном входе — для кнопки «Запустить».
     *
     * @throws IllegalArgumentException код не собирается (текст — по-русски, для ответа 400)
     */
    public RunResult runOnce(String source, String input) {
        ProgramBuilder.Built built = builder.build(GateJob.ORIGINAL, source, null, ExitTrap.safeFor(source));
        if (!built.ok()) {
            throw new IllegalArgumentException("Код не собирается: " + ProgramBuilder.describe(built.errors()));
        }
        return runner.runOne(built.program(), input, RUN_TIMEOUT);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
