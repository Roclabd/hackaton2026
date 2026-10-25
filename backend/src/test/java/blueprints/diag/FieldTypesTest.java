package blueprints.diag;

import blueprints.parse.SourceParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Типы полей для R7/R8: собственное поле класса и {@code x.поле} у класса из того же файла.
 * Ошибки ровно такого вида заложены в учебных монолитах (corpus/robustness/Grades) — без полей правила их не видели.
 */
class FieldTypesTest {

    @Test
    void r8SeesArrayFieldLengthInAverage() {
        String source = """
                class Student {
                    int[] marks;
                    double average() {
                        int sum = 0;
                        for (int m : marks) sum += m;
                        return sum / marks.length;
                    }
                }
                """;
        assertEquals(1, rule("R8", source).size());
    }

    @Test
    void r7SeesStringFieldOfClassFromSameFile() {
        String source = """
                import java.util.List;
                class Grades {
                    static class Student { String name; }
                    static Student find(List<Student> all, String name) {
                        for (Student s : all) {
                            if (s.name == name) return s;
                        }
                        return null;
                    }
                }
                """;
        assertEquals(1, rule("R7", source).size());
    }

    @Test
    void r7SeesThisFieldAgainstParameter() {
        assertEquals(1, rule("R7", "class P { String id; boolean same(String other) { return this.id == other; } }").size());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // поле предка: без разрешения имён не угадываем
            "class A { String n; } class B extends A { boolean f(String x) { return n == x; } }",
            // поле внешнего класса из вложенного: не ищем
            "class O { String n; class I { boolean f(String x) { return n == x; } } }",
            // класс с параметрами типа
            "class Box<T> { T v; } class U { boolean f(Box<String> b, String x) { return b.v == x; } }",
            // два класса с одинаковым именем
            "class O1 { static class S { String n; } } class O2 { static class S { int n; } } class U { boolean f(S s, String x) { return s.n == x; } }",
            // поле не строка
            "class P { Object id; boolean same(String other) { return this.id == other; } }"
    })
    void r7SkipsFieldsItCannotResolveExactly(String source) {
        assertEquals(List.of(), rule("R7", source));
    }

    @Test
    void robustnessGradesHasBothPlantedDefects() throws Exception {
        String source = Files.readString(Path.of("../corpus/robustness/Grades/Grades.java"), StandardCharsets.UTF_8);
        // sum / marks.length в average() и total / count в ветке тернарного оператора (средняя по группе)
        assertEquals(List.of(35, 92), rule("R8", source).stream().map(f -> f.lines()[0]).toList());
        assertEquals(1, rule("R7", source).size(), "s.name == name в findByName()");
    }

    private static List<Finding> rule(String rule, String source) {
        return new FindingsService().find(SourceParser.parseOrThrow(source), source).stream()
                .filter(f -> f.rule().equals(rule)).toList();
    }
}
