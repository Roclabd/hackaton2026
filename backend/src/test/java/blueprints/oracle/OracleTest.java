package blueprints.oracle;

import blueprints.runner.RunResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OracleTest {

    private static RunResult ok(String stdout) {
        return new RunResult(stdout, "", 0, null, false, 10, false, false);
    }

    private static RunResult crash(String stderr, String message) {
        return new RunResult("", stderr, 1, new RunResult.ExceptionInfo("java.lang.NullPointerException", message),
                false, 10, false, false);
    }

    private static RunResult err(String stderr) {
        return new RunResult("", stderr, 0, null, false, 10, false, false);
    }

    @Test
    void sameOutputMatches() {
        assertTrue(Oracle.compare(ok("1\n"), ok("1\n")).matches());
    }

    @Test
    void differentStdoutIsReported() {
        Comparison c = Oracle.compare(ok("1\n"), ok("2\n"));
        assertEquals(Comparison.Outcome.DIFFERENT, c.outcome());
        assertEquals(List.of(new Comparison.Difference("stdout", "1\n", "2\n")), c.differences());
    }

    @Test
    void stackTraceIgnoresLineNumbersAndJdkFrames() {
        String original = """
                Exception in thread "main" java.lang.NullPointerException: boom
                \tat java.base/java.util.Objects.requireNonNull(Objects.java:233)
                \tat jdk.internal.misc.Unsafe.foo(Unsafe.java:1)
                \tat MatchDemo.countMatches(MatchDemo.java:16)
                \tat MatchDemo.main(MatchDemo.java:40)
                """;
        String candidate = """
                Exception in thread "main" java.lang.NullPointerException: boom
                \tat java.base/java.util.Objects.requireNonNull(Objects.java:240)
                \tat MatchDemo.countMatches(MatchDemo.java:18)
                \tat MatchDemo.main(MatchDemo.java:42)
                """;
        assertTrue(Oracle.compare(crash(original, "boom"), crash(candidate, "boom")).matches());
        assertEquals("""
                Exception in thread "main" java.lang.NullPointerException: boom
                \tat MatchDemo.countMatches(MatchDemo.java)
                \tat MatchDemo.main(MatchDemo.java)
                """, Oracle.normalizeStderr(original));
    }

    @Test
    void plainStderrTextWithFileAndLineIsComparedExactly() {
        // Программа печатает ссылку на строку как обычный текст — это её поведение, а не стектрейс.
        assertFalse(Oracle.compare(err("Ошибка в счёте (Invoice.java:100)\n"), err("Ошибка в счёте (Invoice.java:200)\n")).matches());
        assertFalse(Oracle.compare(err("\tat Invoice.total(Invoice.java:100)\n"), err("\tat Invoice.total(Invoice.java:200)\n")).matches(),
                "строка, похожая на кадр, но без заголовка исключения — тоже текст программы");
    }

    @Test
    void chainedTraceLineNumbersAreIgnoredButFramesAreNot() {
        String a = """
                Exception in thread "main" java.lang.IllegalStateException: пусто
                \tat Invoice.total(Invoice.java:100)
                \tat Invoice.main(Invoice.java:12)
                Caused by: java.lang.NullPointerException
                \tat Invoice.load(Invoice.java:40)
                \t... 2 more
                """;
        String b = a.replace("Invoice.java:100", "Invoice.java:200").replace("Invoice.java:40", "Invoice.java:41");
        assertTrue(Oracle.compare(err(a), err(b)).matches());
        assertFalse(Oracle.compare(err(a), err(a.replace("Invoice.total", "Invoice.sum"))).matches(), "другой метод в стеке — различие");
    }

    @Test
    void textAfterTraceIsComparedExactly() {
        String head = "java.lang.RuntimeException: x\n\tat A.f(A.java:3)\n";
        assertFalse(Oracle.compare(err(head + "итог (A.java:5)\n"), err(head + "итог (A.java:6)\n")).matches());
    }

    @Test
    void exceptionTextIsComparedExactly() {
        Comparison c = Oracle.compare(crash("", "a"), crash("", "b"));
        assertEquals("exception", c.differences().get(0).field());
    }

    @Test
    void timeoutIsUnknownNotMatched() {
        RunResult timeout = new RunResult("", "", null, null, true, 3000, false, false);
        assertEquals(Comparison.Outcome.UNKNOWN, Oracle.compare(ok(""), timeout).outcome());
        assertFalse(Oracle.compare(timeout, timeout).matches());
    }
}
