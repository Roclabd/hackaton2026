package blueprints.runner;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Жизнь одного рабочего процесса: запустить, отдать запрос, читать события, разобраться с концом.
 *
 * Если процесс умер посреди прогона (программа вызвала System.exit, таймаут, падение JVM),
 * этот прогон получает итог по коду выхода процесса. Остальные задания перезапускает ProgramRunner.
 */
final class WorkerSession {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String END_OF_STREAM = "\u0000eof";
    /** Сверх таймаута прогона: запуск JVM и загрузка классов. Дольше молчит — процесс завис, убиваем. */
    private static final long SILENCE_GRACE_MS = 15_000;
    private static final int STDERR_TAIL_CHARS = 2_000;

    private final StringBuilder stderrTail = new StringBuilder();
    private Process process;
    private BlockingQueue<String> lines;

    private WorkerSession() {
    }

    /**
     * Поднимает процесс сразу. Запрос он ждёт в stdin, поэтому процесс можно запустить заранее:
     * пока он стартует (~0,5 с), предыдущий ещё работает. Не запустился — {@link #run} вернёт пустой итог.
     */
    static WorkerSession start(List<String> command) {
        WorkerSession session = new WorkerSession();
        try {
            session.process = new ProcessBuilder(command).start();
            session.drainStderr(session.process.getErrorStream());
            session.lines = readLines(session.process.getInputStream());
        } catch (IOException e) {
            session.appendStderr("Не удалось запустить рабочий процесс: " + e.getMessage());
        }
        return session;
    }

    /**
     * Отдаёт запрос и читает события до конца процесса. Вызывается один раз.
     *
     * @param onResult вызывается на каждый законченный прогон (из этого же потока)
     * @return id прогонов, получивших итог; пусто — процесс не сделал ни одного прогона
     */
    Set<Integer> run(WorkerProtocol.Request request, BiConsumer<Integer, RunResult> onResult) {
        Set<Integer> delivered = new HashSet<>();
        if (process == null) {
            return delivered;
        }
        try {
            sendRequest(process, request);
            readEvents(process, lines, request.timeoutMs(), (job, result) -> {
                if (delivered.add(job)) {
                    onResult.accept(job, result);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stop();
        }
        return delivered;
    }

    /** Остановить процесс — только этот, по его PID. */
    void stop() {
        if (process != null) {
            process.destroyForcibly();
        }
    }

    /** Последние строки stderr процесса — для сообщения, если он не запустился. */
    synchronized String stderrTail() {
        return stderrTail.toString().strip();
    }

    private void readEvents(Process process, BlockingQueue<String> lines, long timeoutMs,
                            BiConsumer<Integer, RunResult> deliver) throws InterruptedException {
        Integer inFlight = null;
        long inFlightStart = 0;
        RunResult exitOutput = null;
        events:
        while (true) {
            String line = lines.poll(timeoutMs + SILENCE_GRACE_MS, TimeUnit.MILLISECONDS);
            if (line == null) {
                // Процесс молчит дольше любого разумного срока — завис. Убиваем и считаем таймаутом.
                process.destroyForcibly();
                if (inFlight != null) {
                    deliver.accept(inFlight, RunResult.timeout("", "", elapsedMs(inFlightStart)));
                }
                return;
            }
            if (line.equals(END_OF_STREAM)) {
                break;
            }
            WorkerProtocol.Event event = parse(line);
            if (event == null) {
                continue; // посторонняя строка (программа писала прямо в дескриптор stdout) — не протокол
            }
            switch (event.event()) {
                case WorkerProtocol.Event.START -> {
                    inFlight = event.job();
                    inFlightStart = System.nanoTime();
                    exitOutput = null;
                }
                case WorkerProtocol.Event.RESULT -> {
                    deliver.accept(event.job(), event.result());
                    inFlight = null;
                }
                case WorkerProtocol.Event.EXIT -> {
                    exitOutput = event.result();
                    break events; // процесс уже завершается — ждём только его код выхода
                }
                default -> { }
            }
        }
        if (inFlight != null) {
            Integer code = exitCode(process);
            RunResult result = exitOutput != null
                    ? exitOutput.withExitCode(code)
                    : RunResult.lost(code, "[программа завершила процесс, вывод не сохранён]", elapsedMs(inFlightStart));
            deliver.accept(inFlight, result);
        }
    }

    private static Integer exitCode(Process process) throws InterruptedException {
        return process.waitFor(5, TimeUnit.SECONDS) ? process.exitValue() : null;
    }

    private static void sendRequest(Process process, WorkerProtocol.Request request) {
        try (OutputStream stdin = process.getOutputStream()) {
            JSON.writeValue(stdin, request);
        } catch (IOException e) {
            // Процесс умер до чтения запроса — это увидит readEvents (сразу конец потока).
        }
    }

    private static WorkerProtocol.Event parse(String line) {
        if (!line.startsWith("{\"event\"")) {
            return null;
        }
        try {
            return JSON.readValue(line, WorkerProtocol.Event.class);
        } catch (IOException e) {
            return null;
        }
    }

    private static BlockingQueue<String> readLines(InputStream stdout) {
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        daemon("worker-stdout", () -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } catch (IOException ignored) {
                // поток закрылся вместе с процессом
            } finally {
                lines.add(END_OF_STREAM);
            }
        });
        return lines;
    }

    /** stderr рабочего процесса нужно вычитывать, иначе он заблокируется на полном буфере. */
    private void drainStderr(InputStream stderr) {
        daemon("worker-stderr", () -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stderr, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    appendStderr(line);
                }
            } catch (IOException ignored) {
                // процесс завершился
            }
        });
    }

    private synchronized void appendStderr(String line) {
        stderrTail.append(line).append('\n');
        if (stderrTail.length() > STDERR_TAIL_CHARS) {
            stderrTail.delete(0, stderrTail.length() - STDERR_TAIL_CHARS);
        }
    }

    private static void daemon(String name, Runnable body) {
        Thread thread = new Thread(body, name);
        thread.setDaemon(true);
        thread.start();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
