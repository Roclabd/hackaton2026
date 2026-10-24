package blueprints.runner;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Рабочий процесс JVM: получает байткод версий и список прогонов, выполняет их по очереди.
 *
 * Почему отдельный процесс, а не поток сервера: зависший поток в JVM не остановить (Thread.stop не работает
 * с JDK 20), а System.exit программы убил бы сервер. Здесь и то, и другое завершает только рабочий процесс,
 * а сервер (ProgramRunner) поднимает новый для оставшихся прогонов.
 *
 * Прогоны внутри процесса быстрые (~20 мс): новый загрузчик классов на каждый прогон = статика с нуля.
 */
public final class RunWorker {

    /**
     * 0 — размер стека по умолчанию (-Xss), как у потока main при обычном запуске {@code java}.
     * Больший стек менял бы поведение: рекурсия, которая у {@code java} падает с StackOverflowError, здесь проходила бы.
     */
    private static final long MAIN_STACK_BYTES = 0;
    static final int TIMEOUT_EXIT_CODE = 3;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PrintStream protocol;
    private final long timeoutMs;
    /** Прогон, который идёт сейчас: его вывод забирают перехватчики выхода. */
    private volatile CurrentRun current;

    /**
     * @param finished   отпускается, когда main вернулся, упал или программа вызвала System.exit
     * @param exitStatus код из пойманного System.exit (только при ловушке, см. ExitTrap)
     * @param loader     загрузчик классов этого прогона — из него читаются счётчики копии «замер»
     * @param counters   полное имя класса со счётчиками или null (обычная программа)
     */
    private record CurrentRun(int job, boolean trapExit, LimitedOutputStream out, LimitedOutputStream err,
                              long startNanos, CountDownLatch finished, AtomicReference<Integer> exitStatus,
                              ClassLoader loader, String counters) {}

    private RunWorker(PrintStream protocol, long timeoutMs) {
        this.protocol = protocol;
        this.timeoutMs = timeoutMs;
    }

    public static void main(String[] args) throws Exception {
        // Настоящий stdout сохраняем до подмены System.out: это канал протокола, программа его не видит.
        PrintStream protocol = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        WorkerProtocol.Request request = JSON.readValue(System.in.readAllBytes(), WorkerProtocol.Request.class);
        RunWorker worker = new RunWorker(protocol, request.timeoutMs());
        Runtime.getRuntime().addShutdownHook(new Thread(worker::reportExitDuringRun, "exit-reporter"));
        try {
            ExitTrap.install(worker::trapExit);
        } catch (Throwable noLogging) {
            // нет модуля java.logging (урезанная JRE) — System.exit будет настоящим, с перезапуском процесса
        }
        worker.runAll(request);
        protocol.flush();
        // halt, а не exit: не ждём потоков, которые программа могла оставить после себя.
        Runtime.getRuntime().halt(0);
    }

    private void runAll(WorkerProtocol.Request request) {
        Map<String, WorkerProtocol.Program> programs = new HashMap<>();
        request.programs().forEach(p -> programs.put(p.name(), p));
        for (WorkerProtocol.Job job : request.jobs()) {
            send(new WorkerProtocol.Event(WorkerProtocol.Event.START, job.id(), null));
            WorkerProtocol.Program program = programs.get(job.program());
            RunResult result = program != null ? runOne(job, program)
                    : RunResult.lost(null, "[нет версии кода «" + job.program() + "»]", 0);
            send(new WorkerProtocol.Event(WorkerProtocol.Event.RESULT, job.id(), result));
        }
    }

    private RunResult runOne(WorkerProtocol.Job job, WorkerProtocol.Program program) {
        ClassLoader loader = new MemoryClassLoader(program.classes());
        CurrentRun run = new CurrentRun(job.id(), program.trapExit(), new LimitedOutputStream(), new LimitedOutputStream(),
                System.nanoTime(), new CountDownLatch(1), new AtomicReference<>(),
                loader, CoverageCounters.find(program.classes().keySet()));
        PrintStream programErr = new PrintStream(run.err(), true, StandardCharsets.UTF_8);
        System.setIn(stdin(job.input()));
        System.setOut(new PrintStream(run.out(), true, StandardCharsets.UTF_8));
        System.setErr(programErr);
        current = run;

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread main = new Thread(null, () -> {
            try {
                MainInvoker.invoke(loader, program.mainClass());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                run.finished().countDown();
            }
        }, "main", MAIN_STACK_BYTES);
        main.setContextClassLoader(loader);
        main.start();
        boolean finished = awaitQuietly(run.finished());
        long durationMs = (System.nanoTime() - run.startNanos()) / 1_000_000;
        current = null;

        if (!finished) {
            stopOnTimeout(job, run, durationMs);
        }
        Integer exitStatus = run.exitStatus().get();
        if (exitStatus != null) {
            return completed(run, exitStatus, null, durationMs);
        }
        return finish(failure.get(), run, programErr, durationMs);
    }

    private static RunResult finish(Throwable failure, CurrentRun run, PrintStream programErr, long durationMs) {
        if (failure == null) {
            return completed(run, 0, null, durationMs);
        }
        if (failure instanceof MainInvoker.LaunchError launch) {
            programErr.println(launch.getMessage());
            return completed(run, 1, null, durationMs);
        }
        StackTraces.printUncaught(failure, programErr);
        RunResult.ExceptionInfo info = new RunResult.ExceptionInfo(failure.getClass().getName(), failure.getMessage());
        return completed(run, 1, info, durationMs);
    }

    /**
     * Программа завершилась сама: вывод целиком (или обрезан — тогда это отмечено в результате).
     * У копии «замер» к результату добавляются её счётчики; у обычной программы их нет (null).
     */
    private static RunResult completed(CurrentRun run, Integer exitCode, RunResult.ExceptionInfo exception,
                                       long durationMs) {
        boolean truncated = run.out().truncated() || run.err().truncated();
        int[] hits = CoverageCounters.read(run.loader(), run.counters());
        return new RunResult(run.out().text(), run.err().text(), exitCode, exception, false, durationMs, false, truncated,
                hits);
    }

    /** Ловушка System.exit (вызывается в потоке программы). true — выход пойман, поток будет остановлен. */
    private boolean trapExit(int status) {
        CurrentRun run = current;
        if (run == null || !run.trapExit() || !run.exitStatus().compareAndSet(null, status)) {
            return false; // настоящий выход: процесс завершится, сервер поднимет новый
        }
        System.out.flush();
        System.err.flush();
        run.finished().countDown();
        return true;
    }

    /** Зависший поток не остановить — сообщаем таймаут и завершаем процесс целиком. */
    private void stopOnTimeout(WorkerProtocol.Job job, CurrentRun run, long durationMs) {
        send(new WorkerProtocol.Event(WorkerProtocol.Event.RESULT, job.id(),
                RunResult.timeout(run.out().text(), run.err().text(), durationMs)));
        protocol.flush();
        Runtime.getRuntime().halt(TIMEOUT_EXIT_CODE);
    }

    /** Перехватчик завершения JVM: программа по-настоящему вызвала System.exit — отдаём вывод на этот момент. */
    private void reportExitDuringRun() {
        CurrentRun run = current;
        if (run == null) {
            return;
        }
        long durationMs = (System.nanoTime() - run.startNanos()) / 1_000_000;
        RunResult partial = completed(run, null, null, durationMs);
        send(new WorkerProtocol.Event(WorkerProtocol.Event.EXIT, run.job(), partial));
        protocol.flush();
    }

    private boolean awaitQuietly(CountDownLatch latch) {
        try {
            return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static InputStream stdin(String input) {
        return new ByteArrayInputStream((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
    }

    private synchronized void send(WorkerProtocol.Event event) {
        try {
            protocol.println(JSON.writeValueAsString(event));
        } catch (Exception e) {
            protocol.println("{\"event\":\"error\"}");
        }
    }
}
