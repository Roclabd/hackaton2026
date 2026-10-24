package blueprints.gate;

import blueprints.project.ProjectStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static blueprints.gate.GateServiceTest.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Ложные 🟢 из аудита v4 (docs/аудит_v4): на каждом из этих примеров кандидат ведёт себя иначе, чем у настоящей java,
 * или проверка ничего не проверила — и вердикт не должен быть MATCHED.
 */
class HonestVerdictTest {

    private static GateService gate;
    private final ProjectStore store = new ProjectStore();

    @BeforeAll
    static void setUp() {
        gate = new GateService();
    }

    @AfterAll
    static void tearDown() {
        gate.close();
    }

    private Verdict check(String fileName, String original, String candidate) throws InterruptedException {
        return await(gate, gate.start(store.create(fileName, original), candidate, Intent.REFACTOR));
    }

    /** №5: без main обе версии одинаково не запускаются — это не «совпало». */
    @Test
    void libraryWithoutMainIsIncomplete() throws Exception {
        String original = "public class Library {\n    public static int twice(int x) { return x * 2; }\n}\n";
        Verdict verdict = check("Library.java", original, original.replace("x * 2", "x * 3"));

        assertEquals(VerdictStatus.INCOMPLETE, verdict.status());
        assertTrue(verdict.incompleteReason().contains("нет метода main"), verdict.incompleteReason());
    }

    /** №2: разница в хвосте вывода больше лимита не видна — значит, вердикт неполный, а не «совпало». */
    @Test
    void outputOverLimitIsIncomplete() throws Exception {
        String original = """
                public class Flood {
                    public static void main(String[] args) {
                        String line = "a".repeat(99);
                        for (int i = 0; i < 12_000; i++) System.out.println(line); // ~1,2 МБ
                        System.out.println("total=" + 1000);
                    }
                }
                """;
        Verdict verdict = check("Flood.java", original, original.replace("1000", "999"));

        assertEquals(VerdictStatus.INCOMPLETE, verdict.status());
        assertTrue(verdict.incompleteReason().contains("больше 1 МБ"), verdict.incompleteReason());
    }

    /** №2: 100 КБ теперь в пределах лимита — разница в последней строке видна. */
    @Test
    void outputUnderLimitIsComparedFully() throws Exception {
        String original = """
                public class HugeOut {
                    public static void main(String[] args) {
                        String line = "a".repeat(99);
                        for (int i = 0; i < 1000; i++) System.out.println(line);
                        System.out.println("total=" + 1000);
                    }
                }
                """;
        Verdict verdict = check("HugeOut.java", original, original.replace("1000)", "999)"));

        assertEquals(VerdictStatus.MISMATCH, verdict.status());
    }

    /** №7: стек как у обычной java — рекурсия глубиной 100 000 падает, как у java, и кандидат «цикл → рекурсия» расходится. */
    @Test
    void recursionDeeperThanDefaultStackIsMismatch() throws Exception {
        String original = """
                public class Iterative {
                    static long sum(int n) { long s = 0; for (int i = 1; i <= n; i++) s += i; return s; }
                    public static void main(String[] args) {
                        System.out.println("sum=" + sum(100000));
                    }
                }
                """;
        String candidate = original.replace("long s = 0; for (int i = 1; i <= n; i++) s += i; return s;",
                "return n == 0 ? 0 : n + sum(n - 1);");
        Verdict verdict = check("Iterative.java", original, candidate);

        assertEquals(VerdictStatus.MISMATCH, verdict.status());
        assertEquals("java.lang.StackOverflowError", verdict.smallestMismatch().candidate().exception().type());
    }

    /** №6: если оригинал почти на всех входах печатает разное, сравнивать не на чем — не «совпало на 27». */
    @Test
    void mostlyRandomOriginalIsIncomplete() throws Exception {
        String original = """
                import java.util.Scanner;
                public class Flaky {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        String line = in.hasNextLine() ? in.nextLine() : "";
                        if (line.isBlank()) {
                            System.out.println("empty");
                        } else {
                            System.out.println(line.length() + " " + System.nanoTime());
                        }
                    }
                }
                """;
        Verdict verdict = check("Flaky.java", original, original);

        assertEquals(VerdictStatus.INCOMPLETE, verdict.status());
        assertTrue(verdict.matched() < VerdictAssembler.MIN_COMPARED, "сравнено: " + verdict.matched());
        assertTrue(verdict.incompleteReason().startsWith("Сравнено только"), verdict.incompleteReason());
    }

    /** №1: входы не в том формате — программа на всех падает или делает одно и то же, правка не проверена. */
    @Test
    void inputsInWrongFormatAreIncomplete() throws Exception {
        String original = """
                import java.util.*;
                public class Orders {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        long total = 0;
                        while (in.hasNextLine()) {
                            String[] parts = in.nextLine().split(";");
                            String name = parts[0];
                            int qty = Integer.parseInt(parts[1]);
                            int price = Integer.parseInt(parts[2]);
                            total += (long) qty * price;
                            System.out.println(name + " " + qty * price);
                        }
                        System.out.println("total " + total);
                    }
                }
                """;
        Verdict verdict = check("Orders.java", original, original.replace("(long) qty * price;", "(long) qty * price + 1;"));

        assertEquals(VerdictStatus.INCOMPLETE, verdict.status());
        assertTrue(verdict.incompleteReason().contains("не подходят к программе"), verdict.incompleteReason());
    }

    /** №1: правка в ветке для больших чисел — группа «Крупные числа» до неё доходит. */
    @Test
    void branchForLargeValuesIsReached() throws Exception {
        String original = """
                import java.util.Scanner;
                public class Discount {
                    static int price(int qty) {
                        if (qty >= 10 && qty < 1000) {
                            return qty * 90;
                        }
                        return qty * 100;
                    }
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        while (in.hasNextInt()) {
                            int qty = in.nextInt();
                            System.out.println("qty=" + qty + " price=" + price(qty));
                        }
                    }
                }
                """;
        Verdict verdict = check("Discount.java", original, original.replace("qty * 90", "qty * 80"));

        assertEquals(VerdictStatus.MISMATCH, verdict.status());
    }
}
