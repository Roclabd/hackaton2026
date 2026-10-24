package blueprints.parse;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Регрессия аудита v2 (P1): один SourceParser на всё приложение не должен портить параллельные разборы. */
class SourceParserConcurrencyTest {

    private static final int THREADS = 8;
    private static final int PER_THREAD = 50;

    @Test
    void sharedParserIsSafeUnderParallelLoad() throws Exception {
        SourceParser shared = new SourceParser();
        AtomicInteger correct = new AtomicInteger();
        List<String> failures = new CopyOnWriteArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        List<Future<?>> tasks = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            int worker = t;
            tasks.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < PER_THREAD; i++) {
                    String name = "W" + worker + "R" + i;
                    ParseOutcome outcome = shared.parse(classWithMethods(name));
                    if (outcome.ok() && outcome.unit().get().getType(0).getNameAsString().equals(name)) {
                        correct.incrementAndGet();
                    } else if (failures.size() < 3) {
                        failures.add(name + ": " + outcome.errors());
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> task : tasks) {
            task.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(THREADS * PER_THREAD, correct.get(), "все разборы корректны; примеры ошибок: " + failures);
    }

    private static String classWithMethods(String name) {
        StringBuilder source = new StringBuilder("class " + name + " {");
        for (int m = 0; m < 30; m++) {
            source.append(" int method").append(m).append("(int x) { return x + ").append(m).append("; }");
        }
        return source.append("}").toString();
    }
}
