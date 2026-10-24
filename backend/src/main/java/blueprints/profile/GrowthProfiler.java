package blueprints.profile;

import blueprints.diag.Finding;
import blueprints.diag.FindingsService;
import blueprints.diag.Patch;
import blueprints.parse.ParseOutcome;
import blueprints.parse.SourceParser;
import blueprints.project.Project;
import blueprints.runner.CompileResult;
import blueprints.runner.ExitTrap;
import blueprints.runner.InMemoryCompiler;
import blueprints.runner.MainClassFinder;
import blueprints.runner.ProgramRunner;
import blueprints.runner.StackSensitive;
import blueprints.runner.WorkerProtocol;
import com.github.javaparser.ast.CompilationUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Замер роста для находки: «Big-O подсчётом выполнений, а не секундомером».
 *
 * Шаги: копия оригинала (и заплатки) со счётчиком в теле каждого цикла ({@link LoopCounterInstrumenter}) →
 * компиляция → прогоны на входах n = 100…1600 ({@link GrowingInputs}, {@link CountingRunner}) →
 * сумма счётчиков циклов в строках находки (у заплатки — в строках замены) → модель роста ({@link GrowthFit}).
 *
 * Счётчики считают операции, поэтому результат не зависит от загрузки машины и прогрева JVM.
 * Поведение здесь не проверяется — это делает gate на чистом коде.
 */
public final class GrowthProfiler {

    /** Бюджет на прогоны; ещё ~1–2 с уходит на разбор и компиляцию — всего укладываемся в 15 с. */
    private static final long RUN_BUDGET_MS = 12_000;
    private static final int MIN_POINTS = 3;
    private static final String BEFORE = "before";
    private static final String AFTER = "after";

    /** Ошибка запроса: неизвестная находка (404), неразборчивый код (400). */
    public static final class RequestException extends RuntimeException {
        private final int status;

        RequestException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    private final FindingsService findings = new FindingsService();
    private final InMemoryCompiler compiler;
    private final CountingRunner runner;

    public GrowthProfiler() {
        this(new InMemoryCompiler(), new ProgramRunner());
    }

    GrowthProfiler(InMemoryCompiler compiler, ProgramRunner runner) {
        this.compiler = compiler;
        this.runner = new CountingRunner(runner);
    }

    /**
     * @param patchId заплатка находки для замера «после»; null — только оригинал
     * @throws RequestException находка или заплатка не найдены, код не разбирается
     */
    public ProfileResult profile(Project project, String findingId, String patchId) {
        long start = System.currentTimeMillis();
        if (!project.errors().isEmpty()) {
            throw new RequestException(400, "Текущий код не разбирается — замер невозможен");
        }
        Finding finding = findings.find(project).stream().filter(f -> f.id().equals(findingId)).findFirst()
                .orElseThrow(() -> new RequestException(404, "Находка не найдена: " + findingId));
        Patch patch = patchId == null ? null : finding.patches().stream().filter(p -> p.id().equals(patchId)).findFirst()
                .orElseThrow(() -> new RequestException(404, "Заплатка не найдена: " + patchId));
        return measure(project.source(), finding, patch, start);
    }

    /** Замер для уже найденной находки (и заплатки, если не null) на тексте оригинала. */
    ProfileResult measure(String source, Finding finding, Patch patch, long start) {
        GrowingInputs.Detection detection = GrowingInputs.detect(source);
        if (detection.inputs().isEmpty()) {
            return ProfileResult.unsupported(unsupportedFormat(detection.reason()), elapsed(start));
        }
        GrowingInputs inputs = detection.inputs().get();
        CompilationUnit unit = SourceParser.parseOrThrow(source);
        if (StackSensitive.needsPlainJvm(unit)) {
            return ProfileResult.unsupported("Замер роста не поддержан: программа читает или печатает стек, "
                    + "а счётчики работают только в рабочем процессе.", elapsed(start));
        }
        String mainClass = MainClassFinder.find(unit);
        if (!MainClassFinder.hasLaunchableMain(unit, mainClass)) {
            return ProfileResult.unsupported("Замер роста не поддержан: у программы нет main — нечего запускать.", elapsed(start));
        }

        int[] findingLines = finding.lines();
        Optional<CountedSource> counted = LoopCounterInstrumenter.instrument(source);
        List<Integer> beforeCounters = counted.map(c -> c.countersWithin(findingLines[0], findingLines[1])).orElse(List.of());
        if (beforeCounters.isEmpty()) {
            return ProfileResult.unsupported("Замер роста не поддержан: в строках находки (" + linesText(findingLines)
                    + ") нет циклов — считать нечего.", elapsed(start));
        }
        Optional<WorkerProtocol.Program> beforeProgram = compile(BEFORE, counted.get().source(), mainClass);
        if (beforeProgram.isEmpty()) {
            return ProfileResult.unsupported("Не удалось собрать копию оригинала со счётчиками — замер не выполнен.", elapsed(start));
        }

        List<String> notes = new ArrayList<>();
        List<CountingRunner.Side> sides = new ArrayList<>();
        sides.add(new CountingRunner.Side(BEFORE, beforeProgram.get(), beforeCounters));
        PatchSide patchSide = patch == null ? null : patchSide(patch, mainClass, notes);
        if (patchSide != null) {
            sides.add(patchSide.side());
        }

        // Не больше двух вложенных циклов в измеряемых строках — все размеры одним заходом (см. CountingRunner).
        boolean shallow = counted.get().nestingWithin(findingLines[0], findingLines[1]) <= 2
                && (patchSide == null || patchSide.nesting() <= 2);
        CountingRunner.Outcome outcome = runner.run(sides, inputs, start + RUN_BUDGET_MS, shallow);
        notes.addAll(outcome.notes());
        return assemble(finding, patch, beforeCounters.size(), patchSide, inputs, outcome.points(), notes, start);
    }

    /** Заплатка под счётчиками: циклы в строках замены. null — измерить нельзя (причина — в notes). */
    private PatchSide patchSide(Patch patch, String mainClass, List<String> notes) {
        Optional<CountedSource> counted = LoopCounterInstrumenter.instrument(patch.source());
        if (counted.isEmpty()) {
            notes.add("Заплатка не измерена: в её коде нет циклов или он не разбирается.");
            return null;
        }
        int[] lines = patch.changedLines();
        Optional<WorkerProtocol.Program> program = compile(AFTER, counted.get().source(), mainClass);
        if (program.isEmpty()) {
            notes.add("Заплатка не измерена: её копия со счётчиками не собирается.");
            return null;
        }
        List<Integer> counters = counted.get().countersWithin(lines[0], lines[1]);
        if (counters.isEmpty()) {
            notes.add("В строках замены (" + linesText(lines) + ") циклов нет — у заплатки 0 выполнений тел циклов.");
        }
        return new PatchSide(new CountingRunner.Side(AFTER, program.get(), counters), lines,
                counted.get().nestingWithin(lines[0], lines[1]));
    }

    private record PatchSide(CountingRunner.Side side, int[] lines, int nesting) {}

    private ProfileResult assemble(Finding finding, Patch patch, int beforeLoops, PatchSide patchSide, GrowingInputs inputs,
                                   Map<String, List<CountingRunner.Point>> points, List<String> notes, long start) {
        List<CountingRunner.Point> before = okPrefix(points.get(BEFORE));
        if (before.isEmpty()) {
            CountingRunner.Point first = points.get(BEFORE).get(0);
            String failed = "на входе n = " + first.n() + " (" + inputs.describe() + ") оригинал не дал счётчиков — "
                    + first.failure() + ".";
            // Исключение или код выхода — программа отвергла вход; иначе сбой прогона, о формате судить нельзя.
            return ProfileResult.unsupported(first.rejectedInput()
                    ? "Замер роста для этого формата ввода не поддержан: " + failed
                    : "Замер не выполнен: " + failed, elapsed(start));
        }
        if (before.size() < MIN_POINTS) {
            CountingRunner.Point failed = points.get(BEFORE).get(before.size());
            return ProfileResult.unsupported("Замерено только " + before.size() + " размера — модель роста не подобрать: на n = "
                    + failed.n() + " " + failed.failure() + ".", elapsed(start));
        }
        if (before.stream().allMatch(p -> p.count() == 0)) {
            return ProfileResult.unsupported("Цикл находки не выполнялся на сгенерированных входах — рост не измерен.", elapsed(start));
        }

        List<CountingRunner.Point> after = patchSide == null ? List.of() : okPrefix(points.get(AFTER));
        if (patchSide != null && after.size() < MIN_POINTS) {
            CountingRunner.Point failed = points.get(AFTER).get(after.size());
            notes.add("Заплатка не измерена: на n = " + failed.n() + " " + failed.failure() + ".");
            after = List.of();
        }
        int common = after.isEmpty() ? before.size() : Math.min(before.size(), after.size());
        if (common < before.size()) {
            CountingRunner.Point failed = points.get(AFTER).get(after.size());
            notes.add("Заплатка на n = " + failed.n() + ": " + failed.failure() + " — показаны размеры, где измерены обе версии.");
        } else if (before.size() < points.get(BEFORE).size()) {
            CountingRunner.Point failed = points.get(BEFORE).get(before.size());
            notes.add("Размеры от n = " + failed.n() + " не измерены: " + failed.failure() + ".");
        }

        List<Integer> sizes = before.subList(0, common).stream().map(CountingRunner.Point::n).toList();
        ProfileResult.Series beforeSeries = series(before.subList(0, common), sizes, finding.lines(), beforeLoops);
        ProfileResult.Series afterSeries = after.isEmpty() ? null
                : series(after.subList(0, common), sizes, patchSide.lines(), patchSide.side().counters().size());
        Double factor = factor(beforeSeries, afterSeries);

        notes.add(0, explanation(finding, patch, inputs, beforeSeries, afterSeries));
        return new ProfileResult(true, sizes, beforeSeries, afterSeries, factor, String.join(" ", notes), elapsed(start));
    }

    private static ProfileResult.Series series(List<CountingRunner.Point> points, List<Integer> sizes, int[] lines, int loops) {
        List<Long> counts = points.stream().map(CountingRunner.Point::count).toList();
        GrowthModel model = GrowthFit.bestModel(sizes, counts);
        double slope = Math.round(GrowthFit.logLogSlope(sizes, counts) * 100) / 100.0;
        return new ProfileResult.Series(counts, model.label(), slope, lines, loops);
    }

    /** Отношение счётчиков на наибольшем общем n, до десятых. */
    private static Double factor(ProfileResult.Series before, ProfileResult.Series after) {
        if (after == null) {
            return null;
        }
        long last = after.counts().get(after.counts().size() - 1);
        if (last == 0) {
            return null;
        }
        long first = before.counts().get(before.counts().size() - 1);
        return Math.round(10.0 * first / last) / 10.0;
    }

    private static String explanation(Finding finding, Patch patch, GrowingInputs inputs,
                                      ProfileResult.Series before, ProfileResult.Series after) {
        StringBuilder text = new StringBuilder("Считали выполнения тел циклов (операции, а не время): до — в строках находки "
                + linesText(finding.lines()));
        if (after != null) {
            text.append(", после — в строках замены ").append(linesText(after.lines()));
        }
        text.append("; вход — ").append(inputs.describe()).append(".");
        text.append(compare("оригинала", finding.growthBefore(), before.model()));
        if (after != null && patch != null) {
            text.append(compare("заплатки", finding.growthAfter(), after.model()));
        }
        return text.toString();
    }

    private static String compare(String what, String estimate, String measured) {
        if (estimate == null) {
            return "";
        }
        return estimate.equals(measured)
                ? " Рост " + what + " " + measured + " совпал с оценкой по структуре кода."
                : " Оценка по структуре кода для " + what + " — " + estimate + ", замер показал " + measured + ".";
    }

    private static List<CountingRunner.Point> okPrefix(List<CountingRunner.Point> points) {
        List<CountingRunner.Point> ok = new ArrayList<>();
        for (CountingRunner.Point p : points) {
            if (!p.ok()) {
                break;
            }
            ok.add(p);
        }
        return ok;
    }

    private Optional<WorkerProtocol.Program> compile(String name, String source, String mainClass) {
        ParseOutcome parsed = new SourceParser().parse(source);
        if (parsed.unit().isEmpty()) {
            return Optional.empty();
        }
        CompileResult compiled = compiler.compile(MainClassFinder.find(parsed.unit().get()), source);
        if (!compiled.ok()) {
            return Optional.empty();
        }
        return Optional.of(new WorkerProtocol.Program(name, mainClass, compiled.classes(), ExitTrap.safeFor(source)));
    }

    private static String unsupportedFormat(String reason) {
        return "Замер роста для этого формата ввода не поддержан: " + reason
                + ". Поддержан формат «строки целых чисел через Scanner.nextLine».";
    }

    private static String linesText(int[] lines) {
        return lines[0] == lines[1] ? String.valueOf(lines[0]) : lines[0] + "–" + lines[1];
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
