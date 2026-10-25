package blueprints.heat;

import blueprints.graph.Blueprint;
import blueprints.graph.NodeKind;
import blueprints.inputs.InputGenerator;
import blueprints.instrument.CoverageInstrumenter;
import blueprints.instrument.NodeInstrumentedSource;
import blueprints.project.Blueprints;
import blueprints.project.Project;
import blueprints.runner.CompileResult;
import blueprints.runner.ExitTrap;
import blueprints.runner.InMemoryCompiler;
import blueprints.runner.MainClassFinder;
import blueprints.runner.ProgramRunner;
import blueprints.runner.RunResult;
import blueprints.runner.StackSensitive;
import blueprints.runner.WorkerProtocol;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Замер на отдельной копии исходника: исходный проект и его AST не меняются. */
public final class HeatService {
    private static final String PROGRAM = "heat";
    private final InMemoryCompiler compiler = new InMemoryCompiler();
    private final ProgramRunner runner = new ProgramRunner();
    private final InputGenerator inputs = new InputGenerator();
    private final Duration timeout;

    public HeatService() {
        this(Duration.ofSeconds(2));
    }

    HeatService(Duration timeout) {
        this.timeout = timeout;
    }

    /** Ожидаемая ошибка запроса или недостоверный замер; API возвращает её с русским пояснением. */
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

    public HeatResult measure(Project project, String methodId, String input) {
        if (!project.errors().isEmpty() || project.lastGood().isEmpty()) {
            throw new RequestException(422, "Текущий код не разбирается — тепловая карта недоступна");
        }
        if (!project.compileErrors().isEmpty()) {
            throw new RequestException(422, "Текущий код не компилируется — тепловая карта недоступна");
        }
        Blueprint blueprint = new Blueprints().forMethod(project, methodId)
                .orElseThrow(() -> new RequestException(404, "Метод не найден: " + methodId));
        var unit = project.lastGood().orElseThrow();
        String mainClass = MainClassFinder.find(unit);
        if (!MainClassFinder.hasLaunchableMain(unit, mainClass)) {
            throw new RequestException(422, "У программы нет main — нечего запускать для тепловой карты");
        }
        if (StackSensitive.needsPlainJvm(unit)) {
            throw new RequestException(422, "Тепловая карта недоступна: программа читает или печатает стек, "
                    + "а вставка счётчиков меняет наблюдаемый стек");
        }
        if (unit.findAll(TypeDeclaration.class).stream()
                .anyMatch(type -> type.getNameAsString().equals(blueprints.runner.CoverageCounters.CLASS_NAME))) {
            throw new RequestException(422, "Имя служебного класса счётчиков уже используется в программе");
        }
        Map<String, Node> measurable = new LinkedHashMap<>();
        blueprint.graph().nodes().stream().filter(node -> node.kind() != NodeKind.RAW)
                .forEach(node -> measurable.put(node.id(), blueprint.astById().get(node.id())));
        NodeInstrumentedSource counted = CoverageInstrumenter.instrumentNodes(project.source(), measurable);
        CompileResult compiled = compiler.compile(mainClass, counted.source());
        if (!compiled.ok()) {
            throw new RequestException(422, "Не удалось собрать копию программы со счётчиками — замер не выполнен");
        }
        WorkerProtocol.Program program = new WorkerProtocol.Program(PROGRAM, mainClass, compiled.classes(),
                ExitTrap.safeFor(project.source()));
        List<String> texts = input == null
                ? inputs.generate(project.fileName(), project.source()).stream().map(value -> value.text()).toList()
                : List.of(input);
        List<WorkerProtocol.Job> jobs = new ArrayList<>();
        for (String text : texts) jobs.add(new WorkerProtocol.Job(jobs.size(), PROGRAM, text));
        RunResult[] results = new RunResult[jobs.size()];
        ProgramRunner execution = runner.forCheck();
        String[] failure = new String[1];
        execution.runAll(List.of(program), jobs, timeout, 1, (job, result) -> {
            results[job.id()] = result;
            String reason = invalid(result, counted.nodeIds().size());
            if (reason != null && failure[0] == null) {
                failure[0] = reason;
                execution.cancel();
            }
        });
        if (failure[0] != null) throw new RequestException(422, "Тепловая карта не измерена: " + failure[0]);
        Map<String, Long> counts = new LinkedHashMap<>();
        counted.nodeIds().forEach(id -> counts.put(id, 0L));
        int unsuccessful = 0;
        for (RunResult result : results) {
            String reason = invalid(result, counted.nodeIds().size());
            if (reason != null) throw new RequestException(422, "Тепловая карта не измерена: " + reason);
            for (int k = 0; k < counted.nodeIds().size(); k++) {
                counts.merge(counted.nodeIds().get(k), (long) result.coverageHits()[k], Long::sum);
            }
            if (result.exception() != null || result.exitCode() != null && result.exitCode() != 0) unsuccessful++;
        }
        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        int skipped = blueprint.graph().nodes().size() - counts.size();
        String note = "Сумма посещений операторов перед выполнением по " + results.length + " прогонам. "
                + "Узел цикла — число входов в оператор, не число итераций тела. "
                + (input == null ? "Входы созданы генератором. " : "Использован заданный вход. ")
                + "Это замер на этих входах, а не доказательство поведения на любых данных.";
        if (skipped > 0) note += " Не измерено узлов: " + skipped + "; они отсутствуют в counts.";
        if (unsuccessful > 0) note += " Прогонов с исключением или ненулевым кодом выхода: " + unsuccessful
                + "; учтены посещения до завершения программы.";
        return new HeatResult(methodId, results.length, counts, max, note);
    }

    /** Частичный или потерянный прогон не превращается в успешную карту с нулями. */
    static String invalid(RunResult result, int size) {
        if (result == null) return "прогон не выполнен";
        if (result.timedOut()) return "программа превысила лимит времени";
        if (result.outputTruncated()) return "вывод программы обрезан";
        if (result.outputUnknown()) return "рабочий процесс не вернул полный результат";
        int[] hits = result.coverageHits();
        if (hits == null || hits.length != size) return "счётчики не прочитаны полностью";
        for (int hit : hits) if (hit < 0) return "счётчик переполнился";
        return null;
    }
}
