package blueprints.profile;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.LongUnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/** Подбор модели роста по точным счётчикам разных форм. */
class GrowthFitTest {

    private static final List<Integer> SIZES = GrowingInputs.SIZES;

    @Test
    void picksModelFromCounts() {
        assertEquals("1", fit(n -> 7));
        assertEquals("log n", fit(n -> Math.round(3 * Math.log(n))));
        assertEquals("n", fit(n -> 2 * n));
        assertEquals("n log n", fit(n -> Math.round(n * Math.log(n))));
        assertEquals("n²", fit(n -> n * n + n));      // вложенный цикл + внешний: n² + n
        assertEquals("n³", fit(n -> n * n * n));
    }

    @Test
    void sameCountsAndZerosAreConstant() {
        assertEquals("1", fit(n -> 0));
    }

    @Test
    void logLogSlopeShowsPower() {
        assertEquals(2.0, GrowthFit.logLogSlope(SIZES, counts(n -> n * n)), 1e-9);
        assertEquals(1.0, GrowthFit.logLogSlope(SIZES, counts(n -> 5 * n)), 1e-9);
    }

    private static String fit(LongUnaryOperator f) {
        return GrowthFit.bestModel(SIZES, counts(f)).label();
    }

    private static List<Long> counts(LongUnaryOperator f) {
        return SIZES.stream().map(n -> f.applyAsLong(n)).toList();
    }
}
