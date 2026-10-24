package blueprints.profile;

import blueprints.parse.ParseOutcome;
import blueprints.parse.SourceParser;
import blueprints.parse.SourceText;
import blueprints.runner.CoverageCounters;
import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Копия кода «для замера роста»: в начало тела каждого цикла файла вставляется
 * {@code __BlueprintsCoverage.HITS[k]++;}, где k — номер цикла.
 *
 * Тот же приём, что у {@code instrument.CoverageInstrumenter}, но цель другая: там — «выполнялся ли оператор»,
 * здесь — «сколько раз выполнилось тело цикла». Массив читает рабочий процесс после прогона
 * ({@link CoverageCounters}) — счётчики не печатаются и не смешиваются с выводом программы.
 *
 * Вставки только на тех же строках, поэтому номера строк копии совпадают с исходником:
 * <ul>
 *   <li>тело в фигурных скобках — счётчик сразу после {@code {};</li>
 *   <li>тело без скобок ({@code for (…) x();}) — оборачивается в блок {@code { HITS[k]++; x(); }}.</li>
 * </ul>
 */
final class LoopCounterInstrumenter {

    private static final String COUNTERS = CoverageCounters.CLASS_NAME + "." + CoverageCounters.FIELD;

    private LoopCounterInstrumenter() {}

    /** @return копия со счётчиками; пусто — код не разбирается или в нём нет циклов */
    static Optional<CountedSource> instrument(String source) {
        ParseOutcome parsed = new SourceParser().parse(source);
        if (parsed.unit().isEmpty()) {
            return Optional.empty();
        }
        List<Statement> loops = parsed.unit().get().findAll(Statement.class).stream()
                .filter(LoopCounterInstrumenter::isLoop)
                .filter(s -> s.getRange().isPresent() && body(s).getRange().isPresent())
                .sorted(Comparator.comparing((Statement s) -> range(s).begin))
                .toList();
        if (loops.isEmpty()) {
            return Optional.empty();
        }
        SourceText text = new SourceText(source);
        List<Insertion> insertions = new ArrayList<>();
        List<LoopSite> sites = new ArrayList<>();
        for (Statement loop : loops) {
            int k = sites.size();
            insertions.addAll(place(body(loop), "%s[%d]++;".formatted(COUNTERS, k), text));
            sites.add(new LoopSite(k, range(loop).begin.line, range(loop).end.line, depth(loop)));
        }
        String counted = Insertion.apply(source, insertions) + countersClass(sites.size());
        return Optional.of(new CountedSource(counted, sites));
    }

    private static boolean isLoop(Statement s) {
        return s instanceof ForStmt || s instanceof ForEachStmt || s instanceof WhileStmt || s instanceof DoStmt;
    }

    /** 1 + число циклов, внутри которых стоит этот. */
    private static int depth(Statement loop) {
        int depth = 1;
        for (Node n = loop.getParentNode().orElse(null); n != null; n = n.getParentNode().orElse(null)) {
            if (n instanceof Statement s && isLoop(s)) {
                depth++;
            }
        }
        return depth;
    }

    private static Statement body(Statement loop) {
        return switch (loop) {
            case ForStmt f -> f.getBody();
            case ForEachStmt f -> f.getBody();
            case WhileStmt w -> w.getBody();
            case DoStmt d -> d.getBody();
            default -> throw new IllegalArgumentException("не цикл: " + loop);
        };
    }

    private static List<Insertion> place(Statement body, String counter, SourceText text) {
        int begin = text.offset(range(body).begin);
        if (body instanceof BlockStmt) {
            return List.of(Insertion.open(begin + 1, " " + counter));
        }
        int end = text.offset(range(body).end) + 1;
        return List.of(Insertion.open(begin, "{ " + counter + " "), Insertion.close(end, " }"));
    }

    /** Вспомогательный класс верхнего уровня в том же файле: массив счётчиков (как у копии «замер»). */
    private static String countersClass(int size) {
        return "\nfinal class " + CoverageCounters.CLASS_NAME + " {\n    static final int[] "
                + CoverageCounters.FIELD + " = new int[" + size + "];\n}\n";
    }

    private static Range range(Node node) {
        return node.getRange().orElseThrow();
    }

    /**
     * Вставка текста по смещению. При равных смещениях закрывающие скобки идут раньше открывающих:
     * {@code }} закрывает тело, которое здесь кончилось, {@code {} открывает то, что здесь началось.
     */
    private record Insertion(int offset, int order, String text) {

        static Insertion close(int offset, String text) {
            return new Insertion(offset, 0, text);
        }

        static Insertion open(int offset, String text) {
            return new Insertion(offset, 1, text);
        }

        static String apply(String source, List<Insertion> insertions) {
            List<Insertion> sorted = new ArrayList<>(insertions);
            sorted.sort(Comparator.comparingInt(Insertion::offset).thenComparingInt(Insertion::order));
            StringBuilder result = new StringBuilder(source.length() + 40 * sorted.size());
            int copied = 0;
            for (Insertion insertion : sorted) {
                result.append(source, copied, insertion.offset()).append(insertion.text());
                copied = insertion.offset();
            }
            return result.append(source, copied, source.length()).toString();
        }
    }
}
