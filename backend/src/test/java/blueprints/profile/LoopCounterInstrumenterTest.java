package blueprints.profile;

import blueprints.runner.InMemoryCompiler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Счётчики в телах циклов: строки не сдвигаются, копия собирается, тела без скобок оборачиваются. */
class LoopCounterInstrumenterTest {

    private static final String SOURCE = """
            public class Loops {
                public static void main(String[] args) {
                    int s = 0;
                    for (int i = 0; i < 3; i++) for (int j = 0; j < i; j++) s++;
                    int k = 0;
                    while (k < 2) k++;
                    do { k--; } while (k > 0);
                    for (String a : args) {
                        s += a.length();
                    }
                    System.out.println(s);
                }
            }
            """;

    @Test
    void everyLoopGetsCounterOnSameLine() {
        CountedSource counted = LoopCounterInstrumenter.instrument(SOURCE).orElseThrow();

        assertEquals(List.of(4, 4, 6, 7, 8), counted.loops().stream().map(LoopSite::line).toList());
        String[] original = SOURCE.split("\n");
        String[] copy = counted.source().split("\n");
        assertTrue(copy[3].contains("{ __BlueprintsCoverage.HITS[0]++; for"), copy[3]);
        assertTrue(copy[3].contains("{ __BlueprintsCoverage.HITS[1]++; s++; } }"), copy[3]);
        assertTrue(copy[7].endsWith("{ __BlueprintsCoverage.HITS[4]++;"), copy[7]);
        assertEquals(original[10], copy[10], "строки без циклов не меняются");
        assertTrue(new InMemoryCompiler().compile("Loops", counted.source()).ok(), counted.source());
    }

    @Test
    void countersWithinSelectsLoopsByStartLine() {
        CountedSource counted = LoopCounterInstrumenter.instrument(SOURCE).orElseThrow();
        assertEquals(List.of(0, 1), counted.countersWithin(4, 4));
        assertEquals(List.of(4), counted.countersWithin(8, 10));
        assertEquals(2, counted.nestingWithin(4, 4), "for в for");
        assertEquals(1, counted.nestingWithin(6, 10), "циклы подряд, не вложены");
        assertEquals(0, counted.nestingWithin(11, 12));
    }

    @Test
    void noLoopsMeansNothingToCount() {
        assertTrue(LoopCounterInstrumenter.instrument("class A { void f() {} }").isEmpty());
    }
}
