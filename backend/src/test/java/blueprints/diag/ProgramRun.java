package blueprints.diag;

import blueprints.runner.CompileResult;
import blueprints.runner.InMemoryCompiler;
import blueprints.runner.MemoryClassLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Прогон main внутри теста: свежий загрузчик классов, подменённые System.in/out.
 * Итог — stdout плюс исключение (тип и текст), чтобы сравнивать и вывод, и падение.
 * Настоящие прогоны с таймаутом и изоляцией — в пакете runner; здесь достаточно простого.
 */
final class ProgramRun {

    private static final InMemoryCompiler COMPILER = new InMemoryCompiler();

    private ProgramRun() {}

    static CompileResult compile(String mainClass, String source) {
        CompileResult result = COMPILER.compile(mainClass, source);
        assertTrue(result.ok(), "компилируется: " + result.messages() + "\n" + source);
        return result;
    }

    static String run(CompileResult compiled, String mainClass, String input) throws Exception {
        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        String failure = "";
        try {
            Method main = new MemoryClassLoader(compiled.classes()).loadClass(mainClass).getMethod("main", String[].class);
            main.invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException e) {
            failure = "\nИсключение: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage();
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
        return captured.toString(StandardCharsets.UTF_8) + failure;
    }
}
