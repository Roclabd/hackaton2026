package blueprints.runner;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/** Рабочий процесс: обычный вывод, исключение, зависание, System.exit, большой вывод. */
class ProgramRunnerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final InMemoryCompiler compiler = new InMemoryCompiler();
    private final ProgramRunner runner = new ProgramRunner();

    private WorkerProtocol.Program program(String className, String source) {
        CompileResult compiled = compiler.compile(className, source);
        assertTrue(compiled.ok(), compiled.messages().toString());
        return new WorkerProtocol.Program("p", className, compiled.classes());
    }

    @Test
    void printsReadsStdinAndExitsWithZero() {
        WorkerProtocol.Program echo = program("Echo", """
                import java.util.Scanner;
                public class Echo {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        System.out.println("Привет, " + in.nextLine());
                        System.err.print("warn");
                        System.out.println(1.5);
                    }
                }
                """);
        RunResult result = runner.runOne(echo, "мир", TIMEOUT);
        assertEquals("Привет, мир\n1.5\n", result.stdout().replace("\r\n", "\n"), "кириллица и точка в 1.5 (локаль en-US)");
        assertEquals("warn", result.stderr());
        assertEquals(0, result.exitCode());
        assertNull(result.exception());
        assertFalse(result.unknown());
    }

    @Test
    void uncaughtExceptionLooksLikeJvmOutput() {
        WorkerProtocol.Program boom = program("Boom", """
                public class Boom {
                    public static void main(String[] args) {
                        System.out.println("до");
                        fail(null);
                    }
                    static void fail(String s) { s.length(); }
                }
                """);
        RunResult result = runner.runOne(boom, "", TIMEOUT);
        assertEquals(1, result.exitCode());
        assertEquals("java.lang.NullPointerException", result.exception().type());
        assertTrue(result.exception().message().contains("\"s\" is null"), result.exception().message());
        assertTrue(result.stderr().startsWith("Exception in thread \"main\" java.lang.NullPointerException"), result.stderr());
        assertTrue(result.stderr().contains("at Boom.main(Boom.java:4)"), result.stderr());
        assertFalse(result.stderr().contains("blueprints.runner"), "кадры рабочего процесса убраны: " + result.stderr());
        assertFalse(result.stderr().contains("reflect"), result.stderr());
        assertTrue(result.stdout().startsWith("до"));
    }

    @Test
    void infiniteLoopTimesOutAndNextJobsStillRun() {
        WorkerProtocol.Program loop = program("Loop", """
                public class Loop {
                    public static void main(String[] args) {
                        if (new java.util.Scanner(System.in).nextLine().equals("loop")) {
                            System.out.println("зашли");
                            while (true) { }
                        }
                        System.out.println("ok");
                    }
                }
                """);
        Map<Integer, RunResult> results = new ConcurrentHashMap<>();
        List<WorkerProtocol.Job> jobs = List.of(
                new WorkerProtocol.Job(1, "p", "loop"),
                new WorkerProtocol.Job(2, "p", "go"));
        long start = System.nanoTime();
        runner.runAll(List.of(loop), jobs, Duration.ofMillis(800), 1, (job, r) -> results.put(job.id(), r));
        long ms = (System.nanoTime() - start) / 1_000_000;

        assertTrue(results.get(1).timedOut());
        assertTrue(results.get(1).stdout().startsWith("зашли"), "вывод до зависания сохранён");
        assertEquals("ok\n", results.get(2).stdout().replace("\r\n", "\n"), "следующее задание выполнил новый процесс");
        assertTrue(ms < 10_000, "не зависли: " + ms + " мс");
    }

    @Test
    void systemExitKeepsCodeAndOutputAndRestartsWorker() {
        WorkerProtocol.Program exit = program("Exit", """
                public class Exit {
                    public static void main(String[] args) {
                        String line = new java.util.Scanner(System.in).nextLine();
                        System.out.println("строка " + line);
                        if (line.equals("exit")) System.exit(5);
                    }
                }
                """);
        Map<Integer, RunResult> results = new ConcurrentHashMap<>();
        List<WorkerProtocol.Job> jobs = List.of(
                new WorkerProtocol.Job(1, "p", "a"),
                new WorkerProtocol.Job(2, "p", "exit"),
                new WorkerProtocol.Job(3, "p", "b"),
                new WorkerProtocol.Job(4, "p", "exit"),
                new WorkerProtocol.Job(5, "p", "c"));
        runner.runAll(List.of(exit), jobs, TIMEOUT, 1, (job, r) -> results.put(job.id(), r));

        assertEquals(5, results.size(), "все задания получили итог");
        assertEquals(5, results.get(2).exitCode());
        assertEquals("строка exit\n", results.get(2).stdout().replace("\r\n", "\n"), "вывод до System.exit сохранён");
        assertFalse(results.get(2).unknown());
        assertEquals(5, results.get(4).exitCode());
        assertEquals(0, results.get(5).exitCode());
        assertEquals("строка c\n", results.get(5).stdout().replace("\r\n", "\n"));
    }

    @Test
    void trappedExitBehavesLikeExitWithoutRestartingWorker() {
        String source = """
                public class Quit {
                    public static void main(String[] args) {
                        try {
                            System.out.println("пока");
                            System.exit(7);
                        } finally {
                            System.out.println("finally не должен выполниться");
                        }
                    }
                }
                """;
        assertTrue(ExitTrap.safeFor(source));
        CompileResult compiled = compiler.compile("Quit", source);
        WorkerProtocol.Program quit = new WorkerProtocol.Program("p", "Quit", compiled.classes(), true);
        Map<Integer, RunResult> results = new ConcurrentHashMap<>();
        List<WorkerProtocol.Job> jobs = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> new WorkerProtocol.Job(i, "p", "")).toList();
        long start = System.nanoTime();
        runner.runAll(List.of(quit), jobs, TIMEOUT, 1, (job, r) -> results.put(job.id(), r));
        long ms = (System.nanoTime() - start) / 1_000_000;

        assertEquals(10, results.size());
        for (RunResult r : results.values()) {
            assertEquals(7, r.exitCode());
            assertEquals("пока\n", r.stdout().replace("\r\n", "\n"));
            assertNull(r.exception());
        }
        assertTrue(ms < 4_000, "10 выходов без перезапуска процесса: " + ms + " мс");
    }

    @Test
    void exitTrapIsOffForProgramsWithThreadsOrHooks() {
        assertFalse(ExitTrap.safeFor("class A { void f() { new Thread(() -> {}).start(); } }"));
        assertFalse(ExitTrap.safeFor("class A { void f() { Runtime.getRuntime().addShutdownHook(null); } }"));
    }

    @Test
    void hugeOutputIsTruncated() {
        WorkerProtocol.Program flood = program("Flood", """
                public class Flood {
                    public static void main(String[] args) {
                        for (int i = 0; i < 200_000; i++) System.out.println("строка номер " + i);
                    }
                }
                """);
        RunResult result = runner.runOne(flood, "", TIMEOUT);
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().endsWith(LimitedOutputStream.TRUNCATED_MARK));
        assertTrue(result.outputTruncated());
        assertTrue(result.unknown(), "обрезанный вывод нельзя сравнивать");
        assertTrue(result.stdout().length() < LimitedOutputStream.LIMIT + 100, "не больше 1 МБ: " + result.stdout().length());
    }

    @Test
    void parallelWorkersGiveEveryJobAResult() {
        WorkerProtocol.Program echo = program("Twice", """
                public class Twice {
                    public static void main(String[] args) {
                        int n = new java.util.Scanner(System.in).nextInt();
                        System.out.println(n * 2);
                    }
                }
                """);
        Map<Integer, RunResult> results = new ConcurrentHashMap<>();
        List<WorkerProtocol.Job> jobs = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> new WorkerProtocol.Job(i, "p", String.valueOf(i))).toList();
        runner.runAll(List.of(echo), jobs, TIMEOUT, 3, (job, r) -> results.put(job.id(), r));
        assertEquals(12, results.size());
        assertEquals("22", results.get(11).stdout().strip());
    }

    @Test
    void missingMainClassIsReportedLikeJava() {
        WorkerProtocol.Program none = new WorkerProtocol.Program("p", "Nope",
                program("Other", "public class Other { public static void main(String[] a) {} }").classes());
        RunResult result = runner.runOne(none, "", TIMEOUT);
        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().startsWith("Error: Could not find or load main class Nope"), result.stderr());
    }
}
