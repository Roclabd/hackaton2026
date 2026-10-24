package blueprints.inputs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Команда выхода из меню: находится в коде и дописывается в конец сценариев, чтобы они завершались штатно. */
class ExitCommandTest {

    private final InputGenerator generator = new InputGenerator(List.of());

    private static String menu(String cases) {
        return """
                import java.util.Scanner;
                public class Menu {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        while (true) {
                            String c = in.nextLine();
                            %s
                        }
                    }
                }
                """.formatted(cases);
    }

    @Test
    void numericMenuExitIsZero() {
        String source = menu("switch (c) { case \"1\": System.out.println(1); break; case \"0\": return; }");
        assertEquals("0", InputGenerator.exitCommand(source).orElseThrow());
        List<LabeledInput> scenarios = generator.generate("Menu.java", source).stream()
                .filter(in -> in.group().equals(InputGenerator.COMMANDS)).toList();
        assertFalse(scenarios.isEmpty());
        assertTrue(scenarios.stream().allMatch(in -> InputGenerator.endsWithExit(in, "0")), scenarios.toString());
        assertTrue(scenarios.stream().anyMatch(in -> in.text().equals("1\n0")), scenarios.toString());
    }

    @Test
    void wordIsPreferredOverZeroAndReversedEqualsIsFound() {
        String source = menu("if (\"выход\".equalsIgnoreCase(c)) return; switch (c) { case \"0\": System.out.println(0); }");
        assertEquals("выход", InputGenerator.exitCommand(source).orElseThrow(), "слово надёжнее числа");
    }

    @Test
    void intCaseZeroCounts() {
        String source = menu("switch (Integer.parseInt(c)) { case 1 -> System.out.println(1); case 0 -> System.exit(0); }");
        assertEquals("0", InputGenerator.exitCommand(source).orElseThrow());
    }

    @Test
    void noExitCommandLeavesScenariosAsTheyWere() {
        String source = menu("switch (c) { case \"add\" -> System.out.println(1); default -> {} }");
        assertTrue(InputGenerator.exitCommand(source).isEmpty());
        assertTrue(generator.generate("Menu.java", source).stream().anyMatch(in -> in.text().equals("add")));
    }
}
