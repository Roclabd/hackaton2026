package blueprints.runner;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryCompilerTest {

    private final InMemoryCompiler compiler = new InMemoryCompiler();

    @Test
    void compilesAndRunsMiniLangInFreshClassLoader() throws Exception {
        String source = Files.readString(Path.of("../corpus/ours/MiniLang/MiniLang.java"), StandardCharsets.UTF_8);
        CompileResult result = compiler.compile("MiniLang", source);
        assertTrue(result.ok(), "компилируется: " + result.messages());

        String first = runMain(result, "MiniLang");
        String second = runMain(result, "MiniLang");
        assertTrue(first.contains("=== MiniLang demo ==="), "демо напечатало заголовок");
        assertEquals(first, second, "новый ClassLoader → одинаковый вывод на каждом прогоне");
    }

    @Test
    void reportsCompileErrorWithLine() {
        CompileResult result = compiler.compile("Broken", """
                public class Broken {
                    public static void main(String[] args) {
                        int x = "строка";
                    }
                }
                """);
        assertFalse(result.ok());
        assertTrue(result.messages().stream().anyMatch(m -> m.line() == 3), "ошибка указывает на строку 3: " + result.messages());
    }

    /** Простой прогон для теста. Настоящие прогоны с таймаутом и изоляцией — в пуле рабочих процессов (задача B4). */
    private static String runMain(CompileResult compiled, String mainClass) throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            Method main = new MemoryClassLoader(compiled.classes()).loadClass(mainClass).getMethod("main", String[].class);
            main.invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(originalOut);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }
}
