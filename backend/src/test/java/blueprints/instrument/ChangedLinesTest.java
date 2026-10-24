package blueprints.instrument;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Построчный diff: изменённые, добавленные и удалённые строки. */
class ChangedLinesTest {

    private static final String BASE = "a\nb\nc\nd\ne\n";

    @Test
    void changedLineIsInBothVersions() {
        ChangedLines changed = ChangedLines.between(BASE, "a\nb\nC\nd\ne\n");
        assertEquals(Set.of(3), changed.inCandidate());
        assertEquals(Set.of(3), changed.removed());
    }

    @Test
    void addedLinesAreNumberedInCandidate() {
        ChangedLines changed = ChangedLines.between(BASE, "a\nb\nx\ny\nc\nd\ne\n");
        assertEquals(Set.of(3, 4), changed.inCandidate());
        assertTrue(changed.removed().isEmpty());
    }

    @Test
    void deletionOnlyIsVisibleOnlyInOriginal() {
        ChangedLines changed = ChangedLines.between(BASE, "a\nd\ne\n");
        assertTrue(changed.inCandidate().isEmpty());
        assertEquals(Set.of(2, 3), changed.removed());
    }

    @Test
    void indentAndLineEndingsAreNotChanges() {
        ChangedLines changed = ChangedLines.between(BASE, "a\r\n    b\r\nc  \r\nd\r\ne\r\n");
        assertTrue(changed.none(), changed.toString());
    }

    @Test
    void repeatedLinesAreMatchedByLcs() {
        // Общая подпоследовательность «} } }» — изменилась только одна строка в середине.
        ChangedLines changed = ChangedLines.between("}\nx\n}\n}\n", "}\ny\n}\n}\n");
        assertEquals(Set.of(2), changed.inCandidate());
        assertEquals(Set.of(2), changed.removed());
    }

    @Test
    void sameTextHasNoChanges() {
        assertTrue(ChangedLines.between(BASE, BASE).none());
    }
}
