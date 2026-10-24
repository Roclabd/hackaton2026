package blueprints.export;

import blueprints.runner.CompileResult;
import blueprints.runner.InMemoryCompiler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Литерал, записанный в тест, после javac должен дать ровно исходную строку — включая неудобные символы. */
class JavaLiteralsTest {

    private static final List<String> SAMPLES = List.of(
            "",
            "\n",
            "Совпадений: 2\n",
            "строка 1\nстрока 2\n\nпосле пустой\n",
            "  отступ в начале\nи без\n",
            "пробел в конце \n",
            "нет перевода в конце",
            "a\\b \\u0041 \"кавычки\" \"\"\" тройные\n",
            "таб\tвнутри\n",
            "windows\r\nстрока\r\n",
            "управляющий \u0001 и \u007f символы\n",
            "null\nnull\n");

    @Test
    void literalsSurviveJavac() throws Exception {
        StringBuilder source = new StringBuilder("public class Lit {\n    public static final String[] VALUES = {\n");
        for (String sample : SAMPLES) {
            source.append("            ").append(JavaLiterals.literal(sample, " ".repeat(12))).append(",\n");
        }
        source.append("    };\n}\n");

        CompileResult compiled = new InMemoryCompiler().compile("Lit", source.toString());
        assertTrue(compiled.ok(), compiled.messages() + "\n" + source);
        String[] values = (String[]) load(compiled.classes()).getField("VALUES").get(null);
        assertEquals(SAMPLES, List.of(values));
    }

    @Test
    void textBlockOnlyWhenItKeepsTheText() {
        assertTrue(JavaLiterals.fitsTextBlock("Совпадений: 2\n"));
        assertFalse(JavaLiterals.fitsTextBlock("пробел в конце \n"), "текстовый блок срезал бы пробел");
        assertFalse(JavaLiterals.fitsTextBlock("без перевода строки"));
        assertFalse(JavaLiterals.fitsTextBlock("обратная \\ черта\n"));
    }

    private static Class<?> load(Map<String, byte[]> classes) throws ClassNotFoundException {
        ClassLoader loader = new ClassLoader(JavaLiteralsTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = classes.get(name);
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        return loader.loadClass("Lit");
    }
}
