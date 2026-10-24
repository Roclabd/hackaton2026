package blueprints.project;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProjectStoreTest {

    private final ProjectStore store = new ProjectStore();

    @Test
    void secondEditToSameVersionIsRejected() {
        Project project = store.create("A.java", "class A {}");
        store.updateSource(project.id(), 1, "class A { int first; }");

        assertThrows(VersionConflictException.class, () -> store.updateSource(project.id(), 1, "class A { int second; }"));
        assertEquals("class A { int first; }", store.find(project.id()).orElseThrow().source(), "первая правка не потеряна");
    }

    /** Регрессия аудита v2 (P1): две параллельные правки к одной версии — проходит ровно одна. */
    @RepeatedTest(30)
    void parallelEditsToSameVersionLetExactlyOneThrough() throws Exception {
        Project project = store.create("Race.java", "class Race {}");
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<Void> edit = () -> {
            start.await();
            try {
                store.updateSource(project.id(), 1, "class Race { int x" + Thread.currentThread().threadId() + "; }");
                succeeded.incrementAndGet();
            } catch (VersionConflictException e) {
                conflicts.incrementAndGet();
            }
            return null;
        };
        Future<Void> first = pool.submit(edit);
        Future<Void> second = pool.submit(edit);
        start.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, succeeded.get(), "прошла одна правка");
        assertEquals(1, conflicts.get(), "вторая получила конфликт");
        assertEquals(2, store.find(project.id()).orElseThrow().version());
    }

    @Test
    void unknownProjectIsReported() {
        assertThrows(ProjectNotFoundException.class, () -> store.updateSource("нет-такого", 1, "class A {}"));
    }
}
