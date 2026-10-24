package blueprints.instrument;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoverageMeasurementTest {

    private static CoverageMeasurement bothSides() {
        CoverageRuns candidate = new CoverageRuns(new InstrumentedSource("", List.of(11)), false,
                new int[][]{{100}, {100}, null});
        CoverageRuns original = new CoverageRuns(new InstrumentedSource("", List.of(5)), true,
                new int[][]{{0}, {100}, {100}});
        return new CoverageMeasurement(List.of(candidate, original), null);
    }

    @Test
    void mixedSummaryCountsBothSidesAndInputsInsteadOfIterations() {
        ChangedCoverage coverage = bothSides().summary(i -> i < 2);
        assertEquals(2, coverage.lines());
        assertEquals(2, coverage.covered());
        assertEquals(1, coverage.minHits(), "удалённый оператор достигнут на одном входе, хотя цикл дал сто попаданий");
        assertEquals(2, coverage.inputs());
        assertEquals(2, coverage.inputsReached());
        assertEquals(List.of(new ChangedCoverage.Statement(11, 2, false), new ChangedCoverage.Statement(5, 1, true)),
                coverage.statements());
    }

    @Test
    void missingCountersOnlyMakeComparedInputsIncomplete() {
        CoverageMeasurement measurement = bothSides();
        assertNull(measurement.incompleteReason(i -> i < 2), "случайный или неизвестный вход исключён чистой проверкой");
        assertTrue(measurement.incompleteReason(i -> true).contains("на 1 сравнённых входах"));
        assertEquals(2, measurement.summary().inputs(), "сводка не приписывает отсутствующим счётчикам попадания");
    }

    @Test
    void unchangedAndFailedMeasurementsAreDifferent() {
        assertNull(CoverageMeasurement.unchanged().summary());
        assertNull(CoverageMeasurement.unchanged().incompleteReason(i -> true));
        CoverageMeasurement failed = CoverageMeasurement.failed("копия со счётчиками не собирается");
        assertNull(failed.summary());
        assertEquals("копия со счётчиками не собирается", failed.incompleteReason(i -> true));
    }
}
