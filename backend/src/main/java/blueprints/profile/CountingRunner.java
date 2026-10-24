package blueprints.profile;

import blueprints.runner.ProgramRunner;
import blueprints.runner.RunResult;
import blueprints.runner.WorkerProtocol;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Прогоны копий со счётчиками на входах растущего размера — в рабочих процессах, с лимитом времени.
 *
 * Если в измеряемых строках не больше двух вложенных циклов — все размеры одним заходом (см. {@link #run}).
 * Иначе бюджет запроса не позволяет запускать вслепую, и размеры идут в два захода:
 * <ol>
 *   <li>малые (100, 200, 400) — всегда;</li>
 *   <li>большие (800, 1600) — только если хватает времени и рост на малых не обещает слишком много операций:
 *       при росте n³ размер 1600 дал бы ~4·10⁹ выполнений — это и десятки секунд, и переполнение счётчика int.</li>
 * </ol>
 * Пропущенный размер не выдумывается: о нём говорится в пояснениях ({@link Outcome#notes()}).
 */
final class CountingRunner {

    private static final int SMALL_SIZES = 3;
    /** Малые размеры: n³ при n = 400 — 6,4·10⁷ операций, это десятые доли секунды; секунды хватает с запасом. */
    private static final Duration SMALL_TIMEOUT = Duration.ofSeconds(1);
    private static final long MAX_LARGE_TIMEOUT_MS = 4000;
    private static final long SHALLOW_MAX_TIMEOUT_MS = 2000;
    private static final long MIN_TIMEOUT_MS = 300;
    /** Больше этого числа выполнений на одном прогоне не запускаем (предел счётчика int — 2,1·10⁹). */
    static final long MAX_EXPECTED_COUNT = 500_000_000L;

    /**
     * Одна версия программы для замера.
     *
     * @param counters какие счётчики складывать: циклы находки (или замены в заплатке)
     */
    record Side(String name, WorkerProtocol.Program program, List<Integer> counters) {}

    /** Итог одной версии на одном размере; failure != null — замер на этом размере не удался. */
    record Point(int n, long count, String failure) {
        boolean ok() {
            return failure == null;
        }

        /** Программа сама отвергла вход (исключение, ненулевой код выхода), а не сломался прогон. */
        boolean rejectedInput() {
            return failure != null && (failure.startsWith(EXCEPTION) || failure.startsWith(EXIT_CODE));
        }
    }

    private static final String EXCEPTION = "исключение ";
    private static final String EXIT_CODE = "код выхода ";

    /** Точки по имени версии (в порядке размеров) и пояснения о пропущенных размерах. */
    record Outcome(Map<String, List<Point>> points, List<String> notes) {}

    private final ProgramRunner runner;

    CountingRunner(ProgramRunner runner) {
        this.runner = runner;
    }

    /**
     * @param shallow в измеряемых строках не больше двух вложенных циклов: даже при n = 1600 это порядка n² ≈ 2,6·10⁶
     *                операций, поэтому все размеры идут одним заходом — запуск JVM (около секунды, на занятой
     *                машине — несколько) оплачивается один раз. Лимит времени на прогон всё равно стоит: если внутри
     *                цикла скрыта ещё работа (вызов метода с циклом), размер честно отрезается по таймауту.
     */
    Outcome run(List<Side> sides, GrowingInputs inputs, long deadlineMillis, boolean shallow) {
        Map<String, List<Point>> points = new LinkedHashMap<>();
        sides.forEach(side -> points.put(side.name(), new ArrayList<>()));
        List<String> notes = new ArrayList<>();
        if (shallow) {
            long jobs = (long) GrowingInputs.SIZES.size() * sides.size();
            long timeout = Math.clamp((deadlineMillis - System.currentTimeMillis()) / jobs, MIN_TIMEOUT_MS, SHALLOW_MAX_TIMEOUT_MS);
            runPhase(sides, GrowingInputs.SIZES, inputs, Duration.ofMillis(timeout), points);
            return new Outcome(points, notes);
        }

        List<Integer> small = GrowingInputs.SIZES.subList(0, SMALL_SIZES);
        long smallStart = System.currentTimeMillis();
        runPhase(sides, small, inputs, SMALL_TIMEOUT, points);
        // Время малой фазы — в основном запуск рабочей JVM (сами прогоны — миллисекунды); столько же уйдёт и на большую.
        long phaseCost = System.currentTimeMillis() - smallStart;
        if (points.values().stream().anyMatch(list -> list.stream().anyMatch(p -> !p.ok()))) {
            return new Outcome(points, notes); // уже на малых размерах что-то не так — большие не запускаем
        }

        List<Integer> large = affordable(GrowingInputs.SIZES.subList(SMALL_SIZES, GrowingInputs.SIZES.size()), points, notes);
        if (large.isEmpty()) {
            return new Outcome(points, notes);
        }
        long remaining = deadlineMillis - System.currentTimeMillis() - phaseCost;
        long timeout = Math.min(MAX_LARGE_TIMEOUT_MS, remaining / ((long) large.size() * sides.size()));
        if (timeout < MIN_TIMEOUT_MS) {
            notes.add("Размеры n = " + large.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "))
                    + " не запускались: не хватило бюджета времени запроса (15 с).");
            return new Outcome(points, notes);
        }
        runPhase(sides, large, inputs, Duration.ofMillis(timeout), points);
        return new Outcome(points, notes);
    }

    /**
     * Большие размеры, у которых ожидаемый счётчик не больше {@link #MAX_EXPECTED_COUNT}.
     * Ожидание — грубое продолжение роста между двумя последними малыми размерами; только чтобы решить, запускать ли.
     */
    private static List<Integer> affordable(List<Integer> large, Map<String, List<Point>> points, List<String> notes) {
        List<Integer> chosen = new ArrayList<>();
        for (int n : large) {
            if (points.values().stream().anyMatch(list -> expected(list, n) > MAX_EXPECTED_COUNT)) {
                notes.add("Размеры от n = " + n + " не запускались: по росту на меньших n ожидалось больше "
                        + "5·10⁸ выполнений тела цикла (предел счётчика и бюджета времени).");
                break;
            }
            chosen.add(n);
        }
        return chosen;
    }

    private static double expected(List<Point> measured, int n) {
        Point last = measured.get(measured.size() - 1);
        Point previous = measured.get(measured.size() - 2);
        double ratio = Math.max(1.0, (double) last.count() / Math.max(1, previous.count()));
        double doublings = Math.log((double) n / last.n()) / Math.log(2);
        return last.count() * Math.pow(ratio, doublings);
    }

    /**
     * Все версии на всех размерах фазы — в одном рабочем процессе подряд: сами прогоны занимают миллисекунды,
     * а запуск JVM на Windows — около секунды; два параллельных запуска на занятой машине выходят дольше одного.
     */
    private void runPhase(List<Side> sides, List<Integer> sizes, GrowingInputs inputs, Duration timeout,
                          Map<String, List<Point>> points) {
        List<WorkerProtocol.Job> jobs = new ArrayList<>();
        Map<Integer, Side> sideOf = new LinkedHashMap<>();
        Map<Integer, Integer> sizeOf = new LinkedHashMap<>();
        for (Side side : sides) {
            for (int n : sizes) {
                int id = jobs.size();
                jobs.add(new WorkerProtocol.Job(id, side.name(), inputs.of(n)));
                sideOf.put(id, side);
                sizeOf.put(id, n);
            }
        }
        Map<Integer, RunResult> results = new ConcurrentHashMap<>();
        List<WorkerProtocol.Program> programs = sides.stream().map(Side::program).toList();
        runner.runAll(programs, jobs, timeout, 1, (job, result) -> results.put(job.id(), result));
        for (WorkerProtocol.Job job : jobs) {
            Side side = sideOf.get(job.id());
            int n = sizeOf.get(job.id());
            points.get(side.name()).add(point(n, results.get(job.id()), side.counters(), timeout));
        }
    }

    static Point point(int n, RunResult result, List<Integer> counters, Duration timeout) {
        if (result == null) {
            return new Point(n, 0, "прогон не выполнен");
        }
        if (result.timedOut()) {
            return new Point(n, 0, "не уложился в " + timeout.toMillis() + " мс");
        }
        if (result.outputUnknown()) {
            return new Point(n, 0, "процесс завершился, не отдав результат");
        }
        if (result.exception() != null) {
            return new Point(n, 0, EXCEPTION + result.exception().type());
        }
        if (result.exitCode() != null && result.exitCode() != 0) {
            return new Point(n, 0, EXIT_CODE + result.exitCode());
        }
        int[] hits = result.coverageHits();
        if (hits == null) {
            return new Point(n, 0, "счётчики не прочитаны");
        }
        long sum = 0;
        for (int k : counters) {
            if (k >= hits.length || hits[k] < 0) {
                return new Point(n, 0, "переполнение или потеря счётчика");
            }
            sum += hits[k];
        }
        return new Point(n, sum, null);
    }
}
