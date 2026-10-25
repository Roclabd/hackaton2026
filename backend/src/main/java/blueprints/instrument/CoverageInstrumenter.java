package blueprints.instrument;

import blueprints.parse.ParseOutcome;
import blueprints.parse.SourceParser;
import blueprints.parse.SourceText;
import blueprints.runner.CoverageCounters;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.LabeledStmt;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.stmt.LocalRecordDeclarationStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.UnparsableStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;

/**
 * Вставляет счётчик перед каждым оператором, который затронут изменёнными строками.
 *
 * Какие операторы считаем. Для каждой изменённой строки берём самые внутренние операторы на ней:
 * в {@code if (c) {⏎ x(); }} правка строки {@code x();} — это оператор {@code x()}, а не весь {@code if}.
 * Если же на строке начинается сам составной оператор ({@code if}, {@code while}, {@code for}…), считаем и его:
 * так правка условия измеряется тем, дошло ли выполнение до проверки условия.
 * Строки вне операторов (поля, сигнатуры, импорты) не измеримы и пропускаются.
 *
 * Как вставляем. Дерево разбора только говорит, где оператор; сам текст меняется точечными вставками
 * на тех же строках — номера строк копии совпадают с исходником, формат и комментарии не трогаются:
 * <ul>
 *   <li>оператор в блоке — {@code HITS[k]++;} прямо перед ним;</li>
 *   <li>оператор без фигурных скобок ({@code if (c) x();}) — обёрнут в блок: {@code if (c) { HITS[k]++; x(); }};</li>
 *   <li>{@code this(...)}/{@code super(...)} — счётчик сразу после вызова;</li>
 *   <li>стрелочная ветка switch-выражения {@code case 1 -> e;} — {@code case 1 -> { HITS[k]++; yield e; }}.</li>
 * </ul>
 * Счётчик — только инкремент элемента массива: он ничего не печатает и не меняет поведение программы.
 */
public final class CoverageInstrumenter {

    private static final String COUNTERS = CoverageCounters.CLASS_NAME + "." + CoverageCounters.FIELD;

    private CoverageInstrumenter() {}

    /**
     * @param lines изменённые строки этого исходника (с 1)
     * @return копия со счётчиками; пусто — код не разбирается или среди строк нет ни одного измеримого оператора
     */
    public static Optional<InstrumentedSource> instrument(String source, SortedSet<Integer> lines) {
        ParseOutcome parsed = new SourceParser().parse(source);
        if (parsed.unit().isEmpty() || lines.isEmpty()) {
            return Optional.empty();
        }
        List<Statement> statements = measurableStatements(parsed.unit().get());
        SourceText text = new SourceText(source);
        List<Insertion> insertions = new ArrayList<>();
        List<Integer> statementLines = new ArrayList<>();
        for (Statement statement : statements) {
            if (!touched(statement, lines)) {
                continue;
            }
            List<Insertion> placed = place(statement, counter(statementLines.size()), text);
            if (!placed.isEmpty()) {
                insertions.addAll(placed);
                statementLines.add(range(statement).begin.line);
            }
        }
        if (statementLines.isEmpty()) {
            return Optional.empty();
        }
        String instrumented = Insertion.apply(source, insertions) + countersClass(statementLines.size());
        return Optional.of(new InstrumentedSource(instrumented, statementLines));
    }

    /**
     * Счётчики выбранных узлов чертежа. Связь с оператором берётся из AST, а не из номера строки:
     * условие if/while/do поднимается до своего оператора. Цикл считается при входе в оператор,
     * поэтому счётчик стоит перед всем циклом, а не внутри его тела.
     */
    public static NodeInstrumentedSource instrumentNodes(String source, Map<String, Node> astById) {
        SourceText text = new SourceText(source);
        List<Insertion> insertions = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, Node> entry : astById.entrySet()) {
            Node target = entry.getValue();
            Statement statement = target instanceof Statement s ? s
                    : target.findAncestor(Statement.class).orElse(null);
            // Вызов this/super нельзя честно измерить до входа существующим механизмом конструктора.
            if (statement == null || statement instanceof ExplicitConstructorInvocationStmt || !measurable(statement)) continue;
            List<Insertion> placed = place(statement, counter(ids.size()), text);
            if (placed.isEmpty()) continue;
            insertions.addAll(placed);
            ids.add(entry.getKey());
        }
        return new NodeInstrumentedSource(Insertion.apply(source, insertions) + countersClass(ids.size()), ids);
    }

    /** Операторы, перед которыми можно поставить счётчик, — в порядке появления в файле. */
    private static List<Statement> measurableStatements(CompilationUnit unit) {
        return unit.findAll(Statement.class).stream()
                .filter(CoverageInstrumenter::measurable)
                .sorted(Comparator.comparing((Statement s) -> range(s).begin))
                .toList();
    }

    /**
     * Блок и метка — лишь обёртки вокруг других операторов; объявление локального класса не выполняется;
     * тело лямбды-выражения {@code x -> x * 2} JavaParser представляет оператором, но это выражение.
     */
    private static boolean measurable(Statement s) {
        if (s.getRange().isEmpty() || s instanceof BlockStmt || s instanceof LabeledStmt
                || s instanceof LocalClassDeclarationStmt || s instanceof LocalRecordDeclarationStmt
                || s instanceof UnparsableStmt) {
            return false;
        }
        return !(s instanceof ExpressionStmt && s.getParentNode().orElse(null) instanceof LambdaExpr);
    }

    /** Затронут ли оператор: на изменённой строке нет вложенных операторов — или на ней начинается он сам. */
    private static boolean touched(Statement statement, SortedSet<Integer> lines) {
        Range own = range(statement);
        for (int line : lines.subSet(own.begin.line, own.end.line + 1)) {
            if (line == own.begin.line || !hasNestedOn(statement, line)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNestedOn(Statement statement, int line) {
        return statement.findAll(Statement.class).stream()
                .filter(t -> t != statement && measurable(t))
                .anyMatch(t -> range(t).begin.line <= line && line <= range(t).end.line);
    }

    /** Вставки для одного счётчика; пусто — в этом месте счётчик не поставить (оператор не измерим). */
    private static List<Insertion> place(Statement statement, String counter, SourceText text) {
        if (statement instanceof ExplicitConstructorInvocationStmt) {
            // this(...)/super(...) — счётчик после вызова: так он не мешает правилам о первом операторе конструктора.
            return List.of(Insertion.after(text.offset(range(statement).end) + 1, " " + counter));
        }
        Node anchor = statement;
        while (anchor.getParentNode().orElse(null) instanceof LabeledStmt label) {
            anchor = label; // метка должна остаться прямо перед циклом, поэтому счётчик ставим перед меткой
        }
        Node parent = anchor.getParentNode().orElse(null);
        int begin = text.offset(range(anchor).begin);
        int end = text.offset(range(anchor).end) + 1;
        if (parent instanceof BlockStmt) {
            return List.of(Insertion.before(begin, counter + " "));
        }
        if (parent instanceof SwitchEntry entry) {
            return switch (entry.getType()) {
                case STATEMENT_GROUP -> List.of(Insertion.before(begin, counter + " "));
                case EXPRESSION -> entry.getParentNode().orElse(null) instanceof SwitchExpr
                        ? wrap(begin, end, counter + " yield ")
                        : wrap(begin, end, counter);
                case THROWS_STATEMENT -> wrap(begin, end, counter);
                case BLOCK -> List.of();
            };
        }
        if (parent instanceof IfStmt || parent instanceof WhileStmt || parent instanceof ForStmt
                || parent instanceof ForEachStmt || parent instanceof DoStmt) {
            return wrap(begin, end, counter);
        }
        return List.of();
    }

    /** Оператор без скобок → блок со счётчиком в начале. */
    private static List<Insertion> wrap(int begin, int end, String prefix) {
        return List.of(Insertion.before(begin, "{ " + prefix + (prefix.endsWith(" ") ? "" : " ")),
                Insertion.close(end, " }"));
    }

    private static String counter(int k) {
        return COUNTERS + "[" + k + "]++;";
    }

    /** Вспомогательный класс верхнего уровня в том же файле (тот же пакет): массив счётчиков. */
    private static String countersClass(int size) {
        return "\nfinal class " + CoverageCounters.CLASS_NAME + " {\n    static final int[] "
                + CoverageCounters.FIELD + " = new int[" + size + "];\n}\n";
    }

    private static Range range(Node node) {
        return node.getRange().orElseThrow();
    }

    /**
     * Вставка текста по смещению. При равных смещениях закрывающие скобки идут раньше открывающих:
     * {@code }} относится к оператору, который здесь закончился, {@code {} — к тому, что здесь начался.
     */
    private record Insertion(int offset, int order, String text) {

        static Insertion close(int offset, String text) {
            return new Insertion(offset, 0, text);
        }

        static Insertion after(int offset, String text) {
            return new Insertion(offset, 1, text);
        }

        static Insertion before(int offset, String text) {
            return new Insertion(offset, 2, text);
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
