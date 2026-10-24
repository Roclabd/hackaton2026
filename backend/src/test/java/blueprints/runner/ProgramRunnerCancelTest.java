package blueprints.runner;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Досрочная остановка прогонов одной проверки: зависшая программа не держит проверку минутами. */
class ProgramRunnerCancelTest {

    @Test
    void cancelStopsRunningAndPendingJobs() {
        CompileResult compiled = new InMemoryCompiler().compile("Spin",
                "public class Spin { public static void main(String[] a) { while (true) { } } }");
        assertTrue(compiled.ok(), compiled.messages().toString());
        WorkerProtocol.Program spin = new WorkerProtocol.Program("p", "Spin", compiled.classes());
        List<WorkerProtocol.Job> jobs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            jobs.add(new WorkerProtocol.Job(i, "p", ""));
        }
        ProgramRunner check = new ProgramRunner().forCheck();
        Map<Integer, RunResult> results = new ConcurrentHashMap<>();
        long start = System.nanoTime();
        check.runAll(List.of(spin), jobs, Duration.ofSeconds(3), 1, (job, result) -> {
            results.put(job.id(), result);
            check.cancel(); // после первого же итога
        });
        long ms = (System.nanoTime() - start) / 1_000_000;

        assertEquals(6, results.size(), "у каждого задания есть итог");
        assertTrue(results.get(0).timedOut(), "первый прогон — честный таймаут");
        assertTrue(results.values().stream().filter(r -> !r.timedOut()).allMatch(RunResult::outputUnknown),
                "отменённые прогоны — «вывод неизвестен», а не совпадение");
        assertTrue(ms < 10_000, "без отмены шесть таймаутов заняли бы ~18 с, а прошло " + ms + " мс");
        assertTrue(check.isCancelled());
    }
}
