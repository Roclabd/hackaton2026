package blueprints.gate;

import blueprints.instrument.ChangedCoverage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Правило «изменённый код выполнился меньше чем на 3 входах → INCOMPLETE» и текст причины. */
class CoverageRuleTest {

    private static ChangedCoverage coverage(int inputs, boolean onOriginal, ChangedCoverage.Statement... statements) {
        int covered = (int) List.of(statements).stream().filter(s -> s.inputs() > 0).count();
        int min = List.of(statements).stream().mapToInt(ChangedCoverage.Statement::inputs).min().orElse(0);
        int reached = List.of(statements).stream().mapToInt(ChangedCoverage.Statement::inputs).max().orElse(0);
        return new ChangedCoverage(statements.length, covered, min, reached, inputs, onOriginal, List.of(statements));
    }

    private static ChangedCoverage.Statement at(int line, int inputs) {
        return new ChangedCoverage.Statement(line, inputs);
    }

    @Test
    void absentCoverageIsAllowedWhenCallerHasNoRealChangeOrReportedTheIssueSeparately() {
        assertNull(CoverageRule.incompleteReason(null));
    }

    @Test
    void enoughInputsIsFine() {
        assertNull(CoverageRule.incompleteReason(coverage(27, false, at(14, 20), at(16, 3))));
    }

    @Test
    void untouchedChangeNamesTheLines() {
        String reason = CoverageRule.incompleteReason(coverage(27, false, at(12, 0), at(13, 0), at(14, 0)));
        assertEquals("Изменённый код выполнился только на 0 из 27 входов: проверка не затронула изменение. "
                + "Добавьте вход, который доходит до строк 12–14", reason);
    }

    @Test
    void oneRareStatementIsEnoughForIncomplete() {
        String reason = CoverageRule.incompleteReason(coverage(27, false, at(5, 20), at(9, 1)));
        assertNotNull(reason);
        assertTrue(reason.startsWith("Изменённый код на строке 9 выполнился меньше чем на 3 из 27 входов"), reason);
        assertTrue(reason.endsWith("до строки 9"), reason);
    }

    @Test
    void deletionSpeaksAboutOriginal() {
        String reason = CoverageRule.incompleteReason(coverage(10, true, at(7, 0)));
        assertTrue(reason.startsWith("Удалённый или заменённый код эталона выполнился только на 0 из 10 входов"), reason);
        assertTrue(reason.endsWith("до строки 7 эталона"), reason);
    }

    @Test
    void programWithoutInputDoesNotClaimTheChangeWasMissed() {
        // Slide5Trap: ввода нет, один запуск, изменение выполнено — «не затронула» было бы неправдой.
        String reason = CoverageRule.incompleteReason(coverage(1, false, at(20, 1), at(21, 1)));
        assertTrue(reason.startsWith("Изменённый код выполнился на 1 из 1 входов, но для «совпало» нужно хотя бы 3"), reason);
        assertFalse(reason.contains("не затронула"), reason);
    }

    @Test
    void reachedButTooFewInputsSaysSo() {
        String reason = CoverageRule.incompleteReason(coverage(27, false, at(9, 2)));
        assertEquals("Изменённый код выполнился только на 2 из 27 входов — меньше 3 разных. Добавьте вход, который доходит до строки 9", reason);
    }

    @Test
    void consecutiveLinesBecomeRanges() {
        assertEquals("3, 5–7, 9", CoverageRule.ranges(List.of(3, 5, 6, 7, 9)));
    }
}
