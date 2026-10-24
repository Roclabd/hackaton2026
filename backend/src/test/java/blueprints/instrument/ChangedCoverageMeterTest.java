package blueprints.instrument;

import blueprints.inputs.InputGenerator;
import blueprints.inputs.LabeledInput;
import blueprints.runner.InMemoryCompiler;
import blueprints.runner.ProgramRunner;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Замер целиком: diff → счётчики → прогоны. Регрессионный пример Discount. */
class ChangedCoverageMeterTest {

    private static final Path PROBES = Path.of("src/test/resources/regressions/gate");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ChangedCoverageMeter meter = new ChangedCoverageMeter(new InMemoryCompiler(), new ProgramRunner(), 1);

    @Test
    void discountChangeIsNotReachedBySmallQuantities() throws Exception {
        ChangedCoverage coverage = measure(List.of("1", "2", "3 4 5", ""));
        assertEquals(2, coverage.lines(), "заменённый return измеряется в обеих версиях");
        assertEquals(0, coverage.covered());
        assertEquals(0, coverage.minHits());
        assertEquals(0, coverage.inputsReached());
        assertEquals(4, coverage.inputs());
        assertFalse(coverage.onOriginal());
        assertEquals(List.of(5), coverage.linesBelow(3), "строка return qty * 80");
    }

    @Test
    void discountChangeIsReachedByWholesaleQuantities() throws Exception {
        ChangedCoverage coverage = measure(List.of("20", "1", "10 11", "999"));
        assertEquals(2, coverage.covered());
        assertEquals(3, coverage.minHits(), "оптовая цена на трёх входах из четырёх");
        assertEquals(3, coverage.inputsReached());
        assertTrue(coverage.linesBelow(3).isEmpty());
    }

    @Test
    void onlyCountedInputsAreSummarized() throws Exception {
        CoverageMeasurement runs = meter.measure(read("Discount.java"), read("Discount.candidate.java"), "Discount",
                List.of("20", "30", "1"), TIMEOUT);
        ChangedCoverage firstOnly = runs.summary(input -> input == 0);
        assertEquals(1, firstOnly.inputs());
        assertEquals(1, firstOnly.minHits());
    }

    @Test
    void deletionIsMeasuredOnOriginal() {
        String original = """
                public class Del {
                    public static void main(String[] args) {
                        int x = args.length;
                        if (x > 5) {
                            System.out.println("много");
                        }
                        System.out.println("готово");
                    }
                }
                """;
        String candidate = original.replace("        if (x > 5) {\n            System.out.println(\"много\");\n        }\n", "");
        assertNotEquals(original, candidate);
        ChangedCoverage coverage = meter.measure(original, candidate, "Del", List.of("", "a"), TIMEOUT)
                .summary();
        assertTrue(coverage.onOriginal());
        assertEquals(2, coverage.lines(), "удалены if и печать внутри");
        assertEquals(1, coverage.covered(), "if выполнялся, печать — нет");
        assertEquals(0, coverage.minHits());
        assertEquals(2, coverage.inputsReached());
    }

    @Test
    void sameCodeIsUnchangedButSignatureChangeIsIncomplete() throws Exception {
        String original = read("Discount.java");
        CoverageMeasurement same = meter.measure(original, original, "Discount", List.of("1"), TIMEOUT);
        assertNull(same.summary(), "изменений нет");
        assertNull(same.incompleteReason(i -> true));
        String renamedParam = original.replace("public static void main(String[] args)", "public static void main(String[] argv)");
        CoverageMeasurement renamed = meter.measure(original, renamedParam, "Discount", List.of("1"), TIMEOUT);
        assertNull(renamed.summary(), "изменена только сигнатура — не измеримо");
        assertTrue(renamed.incompleteReason(i -> true).contains("сигнатуры"));
    }

    @Test
    void formattingAndCommentsAreNoChangeButMeaningfulTextBlockWhitespaceIsNot() throws Exception {
        String original = read("Discount.java");
        CoverageMeasurement formatting = meter.measure(original, original.replace("    ", "        ")
                + "\n// только комментарий\n", "Discount", List.of("1"), TIMEOUT);
        assertNull(formatting.summary());
        assertNull(formatting.incompleteReason(i -> true));

        String textBlock = "public class Probe {\n static String label() { return \"\"\"\n        aa\n        \"\"\"; }\n"
                + " public static void main(String[] args) {}\n}";
        String candidate = textBlock.replace("        aa", "         aa");
        assertTrue(ChangedLines.between(textBlock, candidate).none(), "построчный diff обрезает отступы");
        CoverageMeasurement meaningful = meter.measure(textBlock, candidate, "Probe", List.of(""), TIMEOUT);
        assertNotNull(meaningful.incompleteReason(i -> true), "отсутствие счётчиков не означает отсутствие реальной правки");
    }

    @Test
    void menuCoverageCountsGeneratedInputsWithoutAddingTwoSidesTogether() {
        String menu = """
                import java.util.*;
                public class Menu {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        List<String> items = new ArrayList<>();
                        while (true) {
                            String cmd = in.nextLine();
                            switch (cmd) {
                                case "add" -> items.add("x" + items.size());
                                case "list" -> System.out.println(items);
                                default -> System.out.println("Неизвестная команда: " + cmd);
                            }
                        }
                    }
                }
                """;
        List<String> inputs = new InputGenerator().generate("Menu.java", menu).stream().map(LabeledInput::text).toList();
        List<String> addInputs = inputs.stream().filter(input -> input.lines().anyMatch("add"::equals)).toList();
        assertTrue(addInputs.size() >= 3, "генератор включает add в несколько командных последовательностей");
        String candidate = menu.replace("\"x\" + items.size()", "\"x\".concat(String.valueOf(items.size()))");
        CoverageMeasurement measured = meter.measure(menu, candidate, "Menu", inputs, TIMEOUT);
        assertNull(measured.incompleteReason(i -> true));
        ChangedCoverage coverage = measured.summary();
        assertEquals(addInputs.size(), coverage.inputsReached());
        assertEquals(addInputs.size(), coverage.minHits());
        assertEquals(2, coverage.statements().size(), "один оператор в каждой версии");
        assertTrue(coverage.statements().stream().allMatch(s -> s.inputs() == addInputs.size()));
        System.out.println("[проверка] исходное меню: inputsReached=" + coverage.inputsReached()
                + ", statements=" + coverage.statements() + ", входы add="
                + addInputs.stream().map(input -> input.replace("\n", "\\n")).toList());
    }

    private ChangedCoverage measure(List<String> inputs) throws Exception {
        CoverageMeasurement measured = meter.measure(read("Discount.java"), read("Discount.candidate.java"), "Discount", inputs, TIMEOUT);
        assertNull(measured.incompleteReason(i -> true));
        return measured.summary();
    }

    private static String read(String name) throws Exception {
        return Files.readString(PROBES.resolve(name), StandardCharsets.UTF_8);
    }
}
