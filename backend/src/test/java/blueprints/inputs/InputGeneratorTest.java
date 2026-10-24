package blueprints.inputs;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class InputGeneratorTest {

    private final InputGenerator generator = new InputGenerator();

    @Test
    void programWithoutInputGetsOneEmptyInput() {
        List<LabeledInput> inputs = generator.generate("Hello.java",
                "public class Hello { public static void main(String[] a) { System.out.println(1); } }");
        assertEquals(List.of(new LabeledInput(InputGenerator.NO_INPUT, "")), inputs);
    }

    @Test
    void matchDemoGetsAllGroupsDeterministically() throws Exception {
        String source = Files.readString(Path.of("../corpus/ours/MatchDemo/MatchDemo.java"), StandardCharsets.UTF_8);
        List<LabeledInput> first = generator.generate("MatchDemo.java", source);
        List<LabeledInput> second = generator.generate("MatchDemo.java", source);
        assertEquals(first, second, "одинаковые входы при каждом вызове");

        Map<String, Long> groups = first.stream().collect(Collectors.groupingBy(LabeledInput::group, Collectors.counting()));
        for (String group : List.of(InputGenerator.NORMAL, InputGenerator.REPEATED, InputGenerator.EMPTY,
                InputGenerator.NULLS, InputGenerator.LARGE)) {
            assertTrue(groups.containsKey(group), "есть группа " + group + ": " + groups);
        }
        assertTrue(first.size() >= 25 && first.size() <= 45, "около 30 входов: " + first.size());
        assertTrue(first.stream().anyMatch(in -> in.text().equals("null\n1")), "null только в первой строке");
        assertTrue(first.stream().allMatch(in -> in.text().split("\n", -1).length == 2
                        || in.group().equals(InputGenerator.COMMANDS)),
                "две строки — по числу nextLine");
        assertEquals(first.size(), first.stream().map(LabeledInput::text).distinct().count(), "без повторов");
    }

    @Test
    void menuProgramGetsCommandScenarios() {
        String menu = """
                import java.util.Scanner;
                public class Menu {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        while (true) {
                            switch (in.nextLine()) {
                                case "add" -> System.out.println("+");
                                case "exit" -> { return; }
                                default -> System.out.println("?");
                            }
                        }
                    }
                }
                """;
        List<LabeledInput> inputs = generator.generate("Menu.java", menu);
        assertTrue(inputs.stream().anyMatch(in -> in.group().equals(InputGenerator.COMMANDS) && in.text().equals("exit")));
        assertTrue(inputs.stream().anyMatch(in -> in.group().equals(InputGenerator.COMMANDS) && in.text().contains("\n")),
                "есть последовательности команд");
    }

    @Test
    void tokenReadersGetLinesByNumberOfReads() {
        String source = """
                import java.util.Scanner;
                public class Sum {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        System.out.println(in.nextInt() + in.nextInt() + in.nextInt());
                    }
                }
                """;
        List<LabeledInput> inputs = generator.generate("Sum.java", source);
        assertTrue(inputs.stream().filter(in -> in.group().equals(InputGenerator.NORMAL))
                .allMatch(in -> in.text().split("\n", -1).length == 3));
    }

    @Test
    void corpusInputsAreAdded(@org.junit.jupiter.api.io.TempDir Path corpus) throws Exception {
        Path dir = Files.createDirectories(corpus.resolve("ours/Shop/inputs"));
        Files.writeString(dir.resolve("01.txt"), "buy 1\nexit\n");
        List<LabeledInput> inputs = new InputGenerator(List.of(corpus))
                .generate("Shop.java", "class Shop { java.util.Scanner s = new java.util.Scanner(System.in); }");
        assertTrue(inputs.contains(new LabeledInput(InputGenerator.CORPUS, "buy 1\nexit\n")));
    }
}
