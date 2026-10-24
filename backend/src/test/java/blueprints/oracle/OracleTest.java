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
