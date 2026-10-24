package blueprints.runner;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Прогоны программы в рабочих процессах JVM: сервер не рискует ни зависанием, ни System.exit программы.
 *
 * Все процессы запускаются в одинаковом окружении: та же JDK, что у сервера, фиксированная локаль и UTF-8 —
 * так оригинал и кандидат гарантированно видят одно и то же (правило 3 в CLAUDE.md).
 */
public final class ProgramRunner {

    /** Слушатель итогов. Может вызываться из нескольких потоков одновременно. */
    @FunctionalInterface
    public interface ResultListener {
        void onResult(WorkerProtocol.Job job, RunResult result);
    }

    /**
     * Лимиты памяти и стека не задаём — они по умолчанию, как у обычного запуска {@code java}: иначе программа,
     * которой хватает памяти у {@code java}, падала бы здесь с OutOfMemoryError (и наоборот).
     * Флаги ниже влияют только на скорость (быстрый старт JVM), а не на результат программы.
     */
    private static final List<String> JVM_FLAGS = List.of(
            "-Duser.language=en", "-Duser.country=US",
            "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
            "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1");

    /** Сколько процессов держать запущенными впрок, если программа завершает процесс сама (System.exit). */
    private static final int SPARE_WORKERS = 2;
    /** Запуск процесса на Windows сам по себе может занять ~1 с — запасные поднимаем в фоне, не задерживая прогоны. */
    private static final Executor SPAWNER = Thread.ofVirtual().name("worker-spawn")::start;

    private final List<String> command;
    /** Процессы, которые сейчас выполняют прогоны этого исполнителя: их останавливает {@link #cancel()}. */
    private final Set<WorkerSession> active = ConcurrentHashMap.newKeySet();
    private volatile boolean cancelled;

    /** Рабочий процесс видит те же классы, что и сервер: java.class.path (в jar — сам jar). */
    public ProgramRunner() {
        this(System.getProperty("java.class.path"));
    }

    ProgramRunner(String classpath) {
        List<String> cmd = new ArrayList<>(javaCommand());
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(RunWorker.class.getName());
        this.command = List.copyOf(cmd);
    }

    private ProgramRunner(List<String> command) {
        this.command = command;
    }

    /**
     * Отдельный исполнитель для одной проверки: те же JDK и флаги, но своя отмена. {@link #cancel()} у него
     * останавливает только прогоны этой проверки, не трогая параллельные.
     */
    public ProgramRunner forCheck() {
        return new ProgramRunner(command);
    }

    /**
     * Досрочно остановить все прогоны этого исполнителя: убить его рабочие процессы и не запускать новые.
     * Зачем: программа-меню, которая на конце ввода ждёт ввод вечно, иначе съедала бы по 5 с на каждый прогон.
     * Невыполненные задания получают итог {@link RunResult#cancelled()} — «вывод неизвестен», не совпадение.
     */
    public void cancel() {
        cancelled = true;
        active.forEach(WorkerSession::stop);
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /** Один прогон — для кнопки «Запустить» в интерфейсе. */
    public RunResult runOne(WorkerProtocol.Program program, String input, Duration timeout) {
        RunResult[] holder = new RunResult[1];
        WorkerProtocol.Job job = new WorkerProtocol.Job(0, program.name(), input);
        runAll(List.of(program), List.of(job), timeout, 1, (j, result) -> holder[0] = result);
        return holder[0];
    }

    /**
     * Выполняет все прогоны и возвращается, когда у каждого задания есть итог.
     * Задания делятся на {@code workers} подряд идущих частей, каждая — в своём процессе, параллельно.
     * Если процесс умер посреди части, оставшиеся задания этой части выполняет новый процесс.
     */
    public void runAll(List<WorkerProtocol.Program> programs, List<WorkerProtocol.Job> jobs, Duration timeout,
                       int workers, ResultListener listener) {
        try (PlainJavaRunner plain = new PlainJavaRunner()) {
            runAll(programs, jobs, timeout, workers, listener, plain);
        }
    }

    private void runAll(List<WorkerProtocol.Program> programs, List<WorkerProtocol.Job> jobs, Duration timeout,
                        int workers, ResultListener listener, PlainJavaRunner plain) {
        List<List<WorkerProtocol.Job>> parts = split(jobs, Math.max(1, workers));
        if (parts.size() == 1) {
            runPart(programs, parts.get(0), timeout, listener, plain);
            return;
        }
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (List<WorkerProtocol.Job> part : parts) {
                futures.add(pool.submit(() -> runPart(programs, part, timeout, listener, plain)));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Прогоны прерваны: " + e.getMessage(), e);
        }
    }

    private void runPart(List<WorkerProtocol.Program> programs, List<WorkerProtocol.Job> part, Duration timeout,
                         ResultListener listener, PlainJavaRunner plain) {
        Map<String, WorkerProtocol.Program> byName = programs.stream()
                .collect(Collectors.toMap(WorkerProtocol.Program::name, Function.identity()));
        List<WorkerProtocol.Job> workerJobs = new ArrayList<>();
        for (WorkerProtocol.Job job : part) {
            WorkerProtocol.Program program = byName.get(job.program());
            if (cancelled) {
                notify(listener, job, RunResult.cancelled());
            } else if (program != null && program.plainJvm()) {
                if (!workerJobs.isEmpty()) {
                    runWorkerPart(programs, List.copyOf(workerJobs), timeout.toMillis(), listener);
                    workerJobs.clear();
                }
                notify(listener, job, plain.run(program, job.input(), timeout));
            } else {
                workerJobs.add(job);
            }
        }
        if (!workerJobs.isEmpty()) runWorkerPart(programs, workerJobs, timeout.toMillis(), listener);
    }

    private void runWorkerPart(List<WorkerProtocol.Program> programs, List<WorkerProtocol.Job> part, long timeoutMs,
                         ResultListener listener) {
        List<WorkerProtocol.Job> remaining = new ArrayList<>(part);
        if (cancelled) {
            remaining.forEach(job -> notify(listener, job, RunResult.cancelled()));
            return;
        }
        WorkerSession session = track(WorkerSession.start(command));
        Deque<CompletableFuture<WorkerSession>> spares = new ArrayDeque<>();
        try {
            while (!remaining.isEmpty()) {
                Map<Integer, WorkerProtocol.Job> byId = remaining.stream()
                        .collect(Collectors.toMap(WorkerProtocol.Job::id, Function.identity()));
                Set<Integer> done = session.run(new WorkerProtocol.Request(programs, List.copyOf(remaining), timeoutMs),
                        (id, result) -> notify(listener, byId.get(id), result));
                active.remove(session);
                remaining.removeIf(job -> done.contains(job.id()));
                if (cancelled) {
                    remaining.forEach(job -> notify(listener, job, RunResult.cancelled()));
                    return;
                }
                if (done.isEmpty()) {
                    // Процесс не сделал ни одного прогона — повторять бессмысленно, отдаём причину.
                    String reason = ("[рабочий процесс не запустился] " + session.stderrTail()).strip();
                    remaining.forEach(job -> notify(listener, job, RunResult.lost(null, reason, 0)));
                    return;
                }
                if (remaining.isEmpty()) {
                    return;
                }
                // Процесс умер раньше времени (System.exit программы, таймаут). Раз такое случилось, вероятно,
                // случится снова: следующий процесс берём уже запущенный и держим запасные наготове —
                // запуск JVM на Windows доходит до ~1 с, а прогон длится ~20 мс.
                session = track(spares.isEmpty() ? WorkerSession.start(command) : spares.poll().join());
                while (spares.size() < SPARE_WORKERS) {
                    spares.add(CompletableFuture.supplyAsync(() -> WorkerSession.start(command), SPAWNER));
                }
            }
        } finally {
            session.stop();
            active.remove(session);
            spares.forEach(spare -> spare.thenAccept(WorkerSession::stop));
        }
    }

    /** Запомнить процесс для отмены; если отмена уже была — сразу остановить, он не нужен. */
    private WorkerSession track(WorkerSession session) {
        active.add(session);
        if (cancelled) {
            session.stop();
        }
        return session;
    }

    /** Ошибка в слушателе не должна оборвать прогоны остальных заданий. */
    private static void notify(ResultListener listener, WorkerProtocol.Job job, RunResult result) {
        if (job == null) {
            return;
        }
        try {
            listener.onResult(job, result);
        } catch (RuntimeException ignored) {
            // слушатель сам отвечает за свои ошибки
        }
    }

    static List<List<WorkerProtocol.Job>> split(List<WorkerProtocol.Job> jobs, int parts) {
        if (jobs.isEmpty()) {
            return List.of(List.of());
        }
        int count = Math.min(parts, jobs.size());
        int size = (jobs.size() + count - 1) / count;
        List<List<WorkerProtocol.Job>> result = new ArrayList<>();
        for (int from = 0; from < jobs.size(); from += size) {
            result.add(jobs.subList(from, Math.min(jobs.size(), from + size)));
        }
        return result;
    }

    private static String javaExecutable() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString();
    }

    static List<String> javaCommand() {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.addAll(JVM_FLAGS);
        return command;
    }
}
