package blueprints.gate;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Сторож «эталон не завершается»: срабатывает только на разных зависших входах подряд и только один раз. */
class HangWatchTest {

    @Test
    void tripsOnceAfterDifferentInputsHangInARow() {
        AtomicInteger stops = new AtomicInteger();
        HangWatch watch = new HangWatch(3, stops::incrementAndGet);
        watch.original(0, true);
        watch.original(5, true);
        assertFalse(watch.tripped());
        watch.original(9, true);
        watch.original(12, true);
        assertTrue(watch.tripped());
        assertEquals(1, stops.get(), "остановка вызывается один раз");
    }

    @Test
    void repeatedRunOfTheSameInputIsNotANewInput() {
        HangWatch watch = new HangWatch(2, () -> {});
        watch.original(4, true);
        watch.original(4, true); // повтор эталона на том же входе: один медленный вход — ещё не «вечное меню»
        assertFalse(watch.tripped());
        watch.original(7, true);
        assertTrue(watch.tripped());
    }

    @Test
    void finishedRunResetsTheCount() {
        AtomicInteger stops = new AtomicInteger();
        HangWatch watch = new HangWatch(3, stops::incrementAndGet);
        for (int i = 0; i < 30; i += 3) {
            watch.original(i, true);
            watch.original(i + 1, true);
            watch.original(i + 2, false); // программа иногда завершается — это не «вечное меню»
        }
        assertFalse(watch.tripped());
        assertEquals(0, stops.get());
    }
}
