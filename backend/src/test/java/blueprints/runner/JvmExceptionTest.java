package blueprints.runner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JvmExceptionTest {

    @Test
    void readsNullEmptyAndMultilineMessagesWithoutChangingLineSeparators() {
        String trace = "\r\n\tat P.main(P.java:4)\r\n";
        assertEquals(new RunResult.ExceptionInfo("java.lang.Error", null),
                JvmException.from("Exception in thread \"main\" java.lang.Error" + trace, 1));
        assertEquals(new RunResult.ExceptionInfo("java.lang.Error", ""),
                JvmException.from("Exception in thread \"main\" java.lang.Error: " + trace, 1));
        assertEquals(new RunResult.ExceptionInfo("java.lang.Error", "first\nsecond\n"),
                JvmException.from("prefix\r\nException in thread \"main\" java.lang.Error: first\nsecond\n" + trace, 1));
        assertEquals(new RunResult.ExceptionInfo("java.lang.Error", "boom"),
                JvmException.from("warn-without-newlineException in thread \"main\" java.lang.Error: boom" + trace, 1));
    }

    @Test
    void caughtPrintAndOrdinaryStderrHaveNoUncaughtMetadata() {
        assertNull(JvmException.from("java.lang.Error: caught\n\tat P.main(P.java:4)\n", 0));
        assertNull(JvmException.from("warn", 7));
        assertNull(JvmException.from("Exception in thread \"main\" java.lang.Error: printed manually\n", 0));
    }
}
