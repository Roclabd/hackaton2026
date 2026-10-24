package blueprints.diag;

import com.github.javaparser.Position;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.PrimitiveType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Заплатка «индекс частот» для подсчёта совпадений:
 *
 *   for (x : a) for (y : b) if (Objects.equals(x, y)) total++;
 *
 * заменяется на один проход по b, который строит таблицу «значение → сколько раз встретилось»,
 * и по одному поиску в таблице на каждый x. Поведение то же:
 *  - таблица строится лениво при первом x — при пустом a список b не читается, как в оригинале
 *    (иначе при b == null и пустом a появилось бы исключение);
 *  - HashMap ищет ключ как Objects.equals(x, ключ) и допускает ключ null.
 */
final class FrequencyIndexPatch {

    /** Счётчик целого типа: total += n даёт то же, что n раз total++, включая переполнение. */
    private static final Set<PrimitiveType.Primitive> COUNTER_TYPES = Set.of(
            PrimitiveType.Primitive.INT, PrimitiveType.Primitive.LONG,
            PrimitiveType.Primitive.SHORT, PrimitiveType.Primitive.BYTE);

    Optional<PatchProposal> build(LoopPair p, SourceText source, String patchId) {
        if (p.test().kind() != EqualityTest.Kind.OBJECTS_EQUALS
                || !LoopShape.plainNestedIf(p) || !LoopShape.standsInBlock(p.outer())) {
            return Optional.empty();
        }
        Optional<String> a = LoopShape.simpleName(p.outer().getIterable());
        Optional<String> b = LoopShape.simpleName(p.inner().getIterable());
        Optional<String> counter = counterName(p.check().getThenStmt()).filter(name -> isLocalCounter(p, name));
        if (a.isEmpty() || b.isEmpty() || counter.isEmpty() || source.textOf(p.outer()).isEmpty()) {
            return Optional.empty();
        }

        String freq = new NameAllocator(p.declaration()).fresh("freq");
        CodeLines code = new CodeLines()
                .add(0, "java.util.Map<Object, Long> " + freq + " = null;")
                .add(0, "for (" + p.outer().getVariable() + " : " + a.get() + ") {")
                .add(1, "if (" + freq + " == null) {")
                .add(2, freq + " = new java.util.HashMap<>();")
                .add(2, "for (" + p.inner().getVariable() + " : " + b.get() + ") {")
                .add(3, freq + ".merge(" + p.test().innerKey() + ", 1L, Long::sum);")
                .add(2, "}")
                .add(1, "}")
                .add(1, counter.get() + " += " + freq + ".getOrDefault(" + p.test().outerKey() + ", 0L);")
                .add(0, "}");

        int firstLine = p.outer().getBegin().orElseThrow().line;
        String replacement = code.render(source.indentOf(firstLine), source.lineSeparator());
        Patch patch = new Patch(patchId, Patch.REFACTOR, "Индекс частот вместо перебора",
                "Один проход по " + b.get() + " строит таблицу «значение → сколько раз встретилось», "
                        + "и для каждого элемента " + a.get() + " число совпадений берётся из таблицы. "
                        + "Таблица строится при первом элементе " + a.get() + " — как в оригинале, "
                        + "при пустом " + a.get() + " список " + b.get() + " не читается.",
                source.replace(p.outer(), replacement),
                new int[]{firstLine, firstLine + code.size() - 1});
        return Optional.of(new PatchProposal(patch, "n", conditions(p, a.get(), b.get()),
                " Здесь считается число совпадающих пар — его можно получить из индекса частот за один проход по "
                        + b.get() + "."));
    }

    private static List<String> conditions(LoopPair p, String a, String b) {
        List<String> conditions = new ArrayList<>();
        conditions.add(a.equals(b)
                ? "Список " + a + " не меняется внутри цикла"
                : "Списки " + a + " и " + b + " не меняются внутри цикла");
        conditions.add("У элементов согласованы equals и hashCode");
        conditions.add(a.equals(b)
                ? a + " — переменная: вычисляется один раз"
                : a + " и " + b + " — переменные: вычисляются один раз");
        if (!p.test().bareVariables()) {
            conditions.add(p.test().outerKey() + " и " + p.test().innerKey()
                    + " без побочных эффектов: повторный вызов даёт то же значение");
        }
        conditions.add("Сравнение через Objects.equals: null обрабатывается так же");
        return conditions;
    }

    /** total++, ++total или total += 1 — единственный оператор в if. */
    private static Optional<String> counterName(Statement then) {
        Optional<Expression> expression = LoopShape.sole(then)
                .filter(ExpressionStmt.class::isInstance)
                .map(s -> ((ExpressionStmt) s).getExpression());
        if (expression.isEmpty()) {
            return Optional.empty();
        }
        Expression e = expression.get();
        if (e instanceof UnaryExpr unary
                && (unary.getOperator() == UnaryExpr.Operator.POSTFIX_INCREMENT
                || unary.getOperator() == UnaryExpr.Operator.PREFIX_INCREMENT)) {
            return LoopShape.simpleName(unary.getExpression());
        }
        if (e instanceof AssignExpr assign && assign.getOperator() == AssignExpr.Operator.PLUS
                && Set.of("1", "1L", "1l").contains(assign.getValue().toString())) {
            return LoopShape.simpleName(assign.getTarget());
        }
        return Optional.empty();
    }

    /**
     * Счётчик — локальная переменная целого типа, объявленная до цикла в охватывающем его блоке.
     * Поле или Long-объект не подходят: у них другая семантика (видимость из других методов, null).
     */
    private static boolean isLocalCounter(LoopPair p, String name) {
        Position loopStart = p.outer().getBegin().orElseThrow();
        for (VariableDeclarator v : p.declaration().findAll(VariableDeclarator.class)) {
            boolean local = v.getParentNode().filter(VariableDeclarationExpr.class::isInstance).isPresent();
            boolean integral = v.getType() instanceof PrimitiveType t && COUNTER_TYPES.contains(t.getType());
            boolean before = v.getEnd().filter(end -> end.isBefore(loopStart)).isPresent();
            boolean inScope = v.findAncestor(BlockStmt.class).filter(block -> block.isAncestorOf(p.outer())).isPresent();
            if (v.getNameAsString().equals(name) && local && integral && before && inScope) {
                return true;
            }
        }
        return false;
    }
}
