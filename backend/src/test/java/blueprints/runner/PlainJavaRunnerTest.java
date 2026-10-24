package blueprints.runner;

import blueprints.parse.SourceParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PlainJavaRunnerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private final InMemoryCompiler compiler = new InMemoryCompiler();
    private final ProgramRunner runner = new ProgramRunner();
    @TempDir Path temporary;

    private WorkerProtocol.Program program(String name, String source) {
        CompileResult compiled = compiler.compile(name, source);
        assertTrue(compiled.ok(), compiled.messages().toString());
        return new WorkerProtocol.Program(name, name, compiled.classes(), true,
                StackSensitive.needsPlainJvm(SourceParser.parseOrThrow(source)));
    }

    @Test
    void caughtPrintStackTraceMatchesOrdinaryJavaByteForByte() throws Exception {
        WorkerProtocol.Program program = program("sample.Caught", """
                package sample;
                public class Caught {
                    public static void main(String[] args) {
                        System.out.print("до\\n");
                        try { fail(); } catch (IllegalStateException e) { e.printStackTrace(); }
                        System.out.print("после\\n");
                    }
                    static void fail() { throw new IllegalStateException("ошибка"); }
                }
                """);
        RunResult actual = runner.runOne(program, "", TIMEOUT);
        PlainOutput plain = ordinaryJava(program, "");
        assertSameOutput(plain, actual);
        assertNull(actual.exception(), "пойманное исключение не становится необработанным");
        assertFalse(actual.stderr().contains("blueprints.runner"));
    }

    @Test
    void stackLengthLineNumbersThreadDumpAndStackWalkerMatchOrdinaryJava() throws Exception {
        WorkerProtocol.Program program = program("InspectStack", """
                public class InspectStack {
                    public static void main(String[] args) {
                        inspect();
                        Thread.dumpStack();
                        long count = StackWalker.getInstance().walk(s -> s.count());
                        System.out.println(count);
                    }
                    static void inspect() {
                        Exception error = new Exception("saved");
                        StackTraceElement[] frames = error.getStackTrace();
                        System.out.println(frames.length);
                        for (StackTraceElement frame : frames) System.out.println(frame);
                        error.fillInStackTrace();
                        System.out.println(error.getStackTrace()[0].getLineNumber());
                    }
                }
                """);
        RunResult actual = runner.runOne(program, "", TIMEOUT);
        assertSameOutput(ordinaryJava(program, ""), actual);
        assertTrue(actual.stdout().startsWith("2" + System.lineSeparator()), actual.stdout());
        assertTrue(actual.stdout().contains("InspectStack.java:9"), actual.stdout());
    }

    @Test
    void uncaughtExceptionPreservesRealStderrAndMetadata() throws Exception {
        WorkerProtocol.Program program = program("Fail", """
                public class Fail {
                    public static void main(String[] args) {
                        new Throwable().getStackTrace();
                        throw new IllegalStateException("first\\nsecond", new IllegalArgumentException("cause"));
                    }
                }
                """);
        RunResult actual = runner.runOne(program, "", TIMEOUT);
        assertSameOutput(ordinaryJava(program, ""), actual);
        assertEquals(1, actual.exitCode());
        assertEquals(new RunResult.ExceptionInfo("java.lang.IllegalStateException", "first\nsecond"), actual.exception());
        assertTrue(actual.stderr().contains("Caused by: java.lang.IllegalArgumentException: cause"));
    }

    @Test
    void actualSystemExitKeepsStdinOutputAndExitCode() throws Exception {
        WorkerProtocol.Program program = program("QuitPlain", """
                public class QuitPlain {
                    public static void main(String[] args) {
                        new Throwable().getStackTrace();
                        System.out.println(new java.util.Scanner(System.in).nextLine());
                        System.err.print("warn");
                        System.exit(7);
                    }
                }
                """);
        RunResult actual = runner.runOne(program, "Привет", TIMEOUT);
        assertSameOutput(ordinaryJava(program, "Привет"), actual);
        assertEquals(7, actual.exitCode());
        assertNull(actual.exception());
        assertNull(actual.coverageHits());
    }

    @Test
    void timeoutRetainsOutputAndOnlyStopsItsOwnProcess() {
        WorkerProtocol.Program program = program("LoopPlain", """
                public class LoopPlain {
                    public static void main(String[] args) {
                        new Throwable().getStackTrace();
                        System.out.print("started"); System.out.flush();
                        System.err.print("warn"); System.err.flush();
                        while (true) { }
                    }
                }
                """);
        RunResult actual = runner.runOne(program, "", Duration.ofSeconds(2));
        assertTrue(actual.timedOut());
        assertTrue(actual.unknown());
        assertNull(actual.exitCode());
        assertNull(actual.exception());
        assertEquals("started", actual.stdout());
        assertEquals("warn", actual.stderr());
    }

    @Test
    void drainsBothStreamsBeyondLimitWithoutDeadlockAndMarksTruncation() {
        WorkerProtocol.Program program = program("FloodPlain", """
                public class FloodPlain {
                    public static void main(String[] args) {
                        new Throwable().getStackTrace();
                        System.out.print("x".repeat(1048600));
                        System.err.print("y".repeat(1048600));
                    }
                }
                """);
        RunResult actual = runner.runOne(program, "", TIMEOUT);
        assertEquals(0, actual.exitCode());
        assertFalse(actual.timedOut());
        assertTrue(actual.outputTruncated());
        assertTrue(actual.unknown());
        assertEquals(LimitedOutputStream.LIMIT + LimitedOutputStream.TRUNCATED_MARK.length(), actual.stdout().length());
        assertTrue(actual.stdout().endsWith(LimitedOutputStream.TRUNCATED_MARK));
        assertTrue(actual.stderr().endsWith(LimitedOutputStream.TRUNCATED_MARK));
    }

    @Test
    void removesOnlyItsTemporaryClassesAfterRun() throws Exception {
        Path keep = Files.writeString(temporary.resolve("keep.txt"), "чужой файл");
        WorkerProtocol.Program program = program("Location", """
                public class Location {
                    public static void main(String[] args) {
                        new Throwable().getStackTrace();
                        System.out.print(System.getProperty("java.class.path"));
                    }
                }
                """);
        Path classes;
        try (PlainJavaRunner plain = new PlainJavaRunner(temporary)) {
            RunResult actual = plain.run(program, "", TIMEOUT);
            assertFalse(actual.unknown());
            classes = Path.of(actual.stdout());
            assertTrue(classes.startsWith(temporary));
            assertTrue(Files.exists(classes.resolve("Location.class")));
        }
        assertFalse(Files.exists(classes));
        assertTrue(Files.exists(keep));
        try (var remaining = Files.list(temporary)) {
            assertEquals(List.of(keep), remaining.toList());
        }
    }

    @Test
    void ordinaryMatchDemoStillReusesOneFastWorker() throws Exception {
        String source = Files.readString(Path.of("../corpus/ours/MatchDemo/MatchDemo.java"), StandardCharsets.UTF_8)
                .replace("Scanner in = new Scanner(System.in);", "System.err.print(ProcessHandle.current().pid()); Scanner in = new Scanner(System.in);");
        WorkerProtocol.Program program = program("MatchDemo", source);
        assertFalse(program.plainJvm());
        List<WorkerProtocol.Job> jobs = java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> new WorkerProtocol.Job(i, program.name(), "1\n1")).toList();
        Map<Integer, RunResult> results = new ConcurrentHashMap<>();
        runner.runAll(List.of(program), jobs, TIMEOUT, 1, (job, result) -> results.put(job.id(), result));
        assertEquals(8, results.size());
        assertEquals(1, results.values().stream().map(RunResult::stderr).distinct().count(), "восемь запусков используют один worker PID");
        for (RunResult result : results.values()) {
            assertFalse(result.unknown());
            assertEquals("Совпадений: 1" + System.lineSeparator(), result.stdout());
            assertTrue(result.stderr().matches("\\d+"));
        }
    }

    @Test
    void mixedBatchReturnsEveryWorkerAndPlainJob() {
        WorkerProtocol.Program worker = program("Fast", "public class Fast { public static void main(String[] a) { System.out.print(\"fast\"); } }");
        WorkerProtocol.Program plain = program("Plain", "public class Plain { public static void main(String[] a) { System.out.print(new Throwable().getStackTrace().length); } }");
        List<WorkerProtocol.Job> jobs = List.of(new WorkerProtocol.Job(10, "Fast", ""), new WorkerProtocol.Job(20, "Plain", ""),
                new WorkerProtocol.Job(30, "Fast", ""), new WorkerProtocol.Job(40, "Plain", ""));
        Map<Integer, RunResult> results = new ConcurrentHashMap<>();
        runner.runAll(List.of(worker, plain), jobs, TIMEOUT, 2, (job, result) -> results.put(job.id(), result));
        assertEquals(4, results.size());
        assertEquals("fast", results.get(10).stdout());
        assertEquals("fast", results.get(30).stdout());
        assertEquals("1", results.get(20).stdout());
        assertEquals("1", results.get(40).stdout());
        assertTrue(results.values().stream().noneMatch(RunResult::unknown));
    }

    private record PlainOutput(String stdout, String stderr, int exit) {}

    /** Независимый эталон: обычный java из той же JDK; production PlainJavaRunner здесь не используется. */
    private PlainOutput ordinaryJava(WorkerProtocol.Program program, String input) throws Exception {
        Path classes = Files.createTempDirectory(temporary, "reference-");
        for (var entry : program.classes().entrySet()) {
            Path file = classes.resolve(entry.getKey().replace('.', '/') + ".class");
            Files.createDirectories(file.getParent());
            Files.write(file, entry.getValue());
        }
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString(),
                "-Duser.language=en", "-Duser.country=US", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-cp", classes.toString(), program.mainClass()));
        Process process = new ProcessBuilder(command).start();
        try {
            CompletableFuture<byte[]> stdout = readAll(process.getInputStream());
            CompletableFuture<byte[]> stderr = readAll(process.getErrorStream());
            try (var stdin = process.getOutputStream()) { stdin.write(input.getBytes(StandardCharsets.UTF_8)); }
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "эталонный java завершился");
            return new PlainOutput(new String(stdout.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8),
                    new String(stderr.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8), process.exitValue());
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }

    private static CompletableFuture<byte[]> readAll(java.io.InputStream stream) {
        CompletableFuture<byte[]> done = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try (stream) { done.complete(stream.readAllBytes()); }
            catch (java.io.IOException error) { done.completeExceptionally(error); }
        });
        return done;
    }

    private static void assertSameOutput(PlainOutput plain, RunResult actual) {
        assertFalse(actual.unknown());
        assertEquals(plain.stdout(), actual.stdout());
        assertEquals(plain.stderr(), actual.stderr());
        assertEquals(plain.exit(), actual.exitCode());
    }
}
