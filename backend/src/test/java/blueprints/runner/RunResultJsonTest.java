package blueprints.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** RunResult ходит между процессами в JSON: счётчики замера доезжают, а у обычного прогона их нет в JSON. */
class RunResultJsonTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void coverageHitsSurviveRoundTrip() throws Exception {
        RunResult measured = new RunResult("out", "", 0, null, false, 5, false, false, new int[]{3, 0, 1});
        RunResult back = json.readValue(json.writeValueAsString(measured), RunResult.class);
        assertArrayEquals(new int[]{3, 0, 1}, back.coverageHits());
        assertEquals("out", back.stdout());
    }

    @Test
    void ordinaryRunHasNoCoverageField() throws Exception {
        RunResult plain = new RunResult("out", "", 0, null, false, 5, false, false);
        String text = json.writeValueAsString(plain);
        assertFalse(text.contains("coverageHits"), text);
        assertNull(json.readValue(text, RunResult.class).coverageHits());
    }
}
