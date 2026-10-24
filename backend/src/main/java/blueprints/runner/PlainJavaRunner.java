package blueprints.runner;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Настоящий запуск java main: пользователь видит свой стек, stdout и stderr читаются как байты. */
final class PlainJavaRunner implements AutoCloseable {

    private final Map<WorkerProtocol.Program, Path> directories = new HashMap<>();
    private final Path parent;
    private Path root;

    PlainJavaRunner() {
        this(null);
    }

    PlainJavaRunner(Path parent) {
        this.parent = parent;
    }

    RunResult run(WorkerProtocol.Program program, String input, Duration timeout) {
        long start = System.nanoTime();
        Process process = null;
        LimitedOutputStream out = new LimitedOutputStream();
        LimitedOutputStream err = new LimitedOutputStream();
        try {
            Path classes = prepare(program);
            List<String> command = new ArrayList<>(ProgramRunner.javaCommand());
            command.addAll(List.of("-cp", classes.toString(), program.mainClass()));
            process = new ProcessBuilder(command).start();
            CompletableFuture<Boolean> stdout = drain(process.getInputStream(), out);
            CompletableFuture<Boolean> stderr = drain(process.getErrorStream(), err);
            writeInput(process.getOutputStream(), input);
            boolean timedOut = !process.waitFor(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
            if (timedOut) stop(process);
            boolean complete = collected(stdout) & collected(stderr);
            Integer exit = timedOut || process.isAlive() ? null : process.exitValue();
            boolean truncated = out.truncated() || err.truncated();
            RunResult.ExceptionInfo exception = exit == null || truncated ? null : JvmException.from(err.text(), exit);
            return new RunResult(out.text(), err.text(), exit, exception, timedOut, elapsed(start), !complete, truncated);
        } catch (InterruptedException interrupted) {
            if (process != null) stop(process);
            Thread.currentThread().interrupt();
            return new RunResult(out.text(), err.text(), null, null, false, elapsed(start), true,
                    out.truncated() || err.truncated());
        } catch (IOException | RuntimeException failed) {
            return new RunResult(out.text(), err.text() + "[не удалось запустить java: " + failed.getMessage() + "]",
                    null, null, false, elapsed(start), true, out.truncated() || err.truncated());
        } finally {
            if (process != null) stop(process);
        }
    }

    /** У каждой проверки свой каталог; путь каждого .class проверяется до записи. */
    private synchronized Path prepare(WorkerProtocol.Program program) throws IOException {
        Path existing = directories.get(program);
        if (existing != null) return existing;
        if (root == null) {
            root = (parent == null ? Files.createTempDirectory("blueprints-plain-")
                    : Files.createTempDirectory(parent, "blueprints-plain-")).toAbsolutePath().normalize();
        }
        Path directory = Files.createTempDirectory(root, "program-").toAbsolutePath().normalize();
        for (Map.Entry<String, byte[]> entry : program.classes().entrySet()) {
            Path file = directory.resolve(entry.getKey().replace('.', '/') + ".class").normalize();
            if (!file.startsWith(directory)) throw new IOException("имя класса выходит за каталог прогона");
            Files.createDirectories(file.getParent());
            Files.write(file, entry.getValue());
        }
        directories.put(program, directory);
        return directory;
    }

    private static CompletableFuture<Boolean> drain(InputStream stream, LimitedOutputStream output) {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        Thread.ofVirtual().name("plain-java-output").start(() -> {
            try (stream) {
                stream.transferTo(output);
                done.complete(true);
            } catch (IOException failed) {
                done.complete(false);
            }
        });
        return done;
    }

    private static void writeInput(OutputStream stdin, String input) {
        Thread.ofVirtual().name("plain-java-input").start(() -> {
            try (stdin) {
                stdin.write((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // Программа может завершиться, не прочитав вход: это не потеря её вывода.
            }
        });
    }

    private static boolean collected(CompletableFuture<Boolean> future) throws InterruptedException {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failed) {
            return false;
        }
    }

    /** Останавливаем только процесс, который создали в этом прогоне; чужие PID не перечисляем. */
    private static void stop(Process process) {
        if (process.isAlive()) process.destroyForcibly();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    @Override
    public synchronized void close() {
        if (root == null) return;
        // Без FOLLOW_LINKS: даже созданная программой ссылка не ведёт очистку в чужой каталог.
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    deleteOwn(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                    if (error != null) throw error;
                    deleteOwn(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Ошибка очистки собственного tempdir не меняет уже сохранённый результат программы.
        }
    }

    private void deleteOwn(Path path) throws IOException {
        if (!path.toAbsolutePath().normalize().startsWith(root)) throw new IOException("очистка вне каталога прогона");
        Files.deleteIfExists(path);
    }
}
