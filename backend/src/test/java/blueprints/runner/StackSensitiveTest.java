package blueprints.runner;

import blueprints.parse.SourceParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StackSensitiveTest {

    @Test
    void explicitStackApisAndMethodReferencesRequirePlainJava() {
        for (String body : List.of("new Exception().printStackTrace();", "new Exception().getStackTrace();",
                "new Exception().fillInStackTrace();", "Thread.currentThread().getStackTrace();", "Thread.dumpStack();",
                "java.lang.StackWalker.getInstance().walk(s -> s.count());",
                "java.util.function.Consumer<Throwable> sink = Throwable::printStackTrace;")) {
            assertTrue(StackSensitive.needsPlainJvm(SourceParser.parseOrThrow("class P { void f() { " + body + " } }")), body);
        }
        assertTrue(StackSensitive.needsPlainJvm(SourceParser.parseOrThrow(
                "import static java.lang.StackWalker.getInstance; class P { void f() { var walker = getInstance(); walker.walk(s -> s.count()); } }")));
        assertTrue(StackSensitive.needsPlainJvm(SourceParser.parseOrThrow(
                "import java.lang.StackWalker.StackFrame; class P {}")));
    }

    @Test
    void stringsAndCommentsDoNotSelectPlainJavaAndMatchDemoStaysFast() throws Exception {
        assertFalse(StackSensitive.needsPlainJvm(SourceParser.parseOrThrow("""
                class P { void f() {
                    // Thread.dumpStack(); new Exception().printStackTrace();
                    System.out.print("StackWalker getStackTrace fillInStackTrace");
                } }
                """)));
        String source = Files.readString(Path.of("../corpus/ours/MatchDemo/MatchDemo.java"), StandardCharsets.UTF_8);
        assertFalse(StackSensitive.needsPlainJvm(SourceParser.parseOrThrow(source)));
    }
}
