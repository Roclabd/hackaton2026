package blueprints.diag;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * R16: {@code linkedList.get(i)} в цикле по индексу. LinkedList не хранит элементы подряд: get(i) идёт по звеньям
 * от начала (или конца) до позиции i, поэтому весь цикл — порядка n² шагов. Обход for-each или итератором — n.
 *
 * Срабатываем, только если переменная явно объявлена как LinkedList (локальная, параметр или поле): у List
 * неизвестно, какая реализация, а у ArrayList get(i) — одно обращение. Индекс — ровно переменная цикла,
 * цикл зависит от данных. Заплатки нет.
 */
final class LinkedListIndexRule {

    static final String RULE = "R16";
    private static final Set<String> LINKED = Set.of("LinkedList");

    List<Finding> find(DiagnosticContext context) {
        List<Finding> found = new ArrayList<>();
        for (MethodCallExpr call : context.unit.findAll(MethodCallExpr.class, c -> c.getNameAsString().matches("get|set"))) {
            if (call.getArguments().isEmpty() || context.methodId(call).isEmpty()
                    || !(call.getScope().map(DiagnosticContext::unwrapped).orElse(null) instanceof NameExpr list)
                    || context.standardType(list, LINKED).isEmpty()) continue;
            Optional<IndexLoop> loop = DiagnosticContext.enclosingLoop(call).flatMap(IndexLoop::of)
                    .filter(l -> l.counting() && l.growing() && l.isVariable(call.getArgument(0)));
            if (loop.isEmpty() || DiagnosticContext.inputLoop(loop.get().loop()) || declaredInside(list, loop.get().loop())) continue;
            found.add(new Finding(RULE + "-" + (found.size() + 1), RULE, context.methodId(call).orElseThrow(),
                    DiagnosticContext.lines(call), "LinkedList по индексу в цикле",
                    "«" + list + "» — LinkedList: «" + call + "» каждый раз идёт по звеньям списка до позиции "
                            + loop.get().variable() + ", а вызывается на каждом шаге цикла. При n элементах это порядка n² "
                            + "шагов. Обход for-each или итератором проходит список за n, ArrayList отдаёт элемент сразу.",
                    "n²", "n",
                    List.of("Список не меняется внутри цикла (иначе for-each бросит ConcurrentModificationException)",
                            "Заплатки нет: замену обхода нужно проверить на ваших входах"),
                    List.of(), Finding.INEFFICIENCY));
        }
        return found;
    }

    /** Список объявлен внутри цикла — на каждой итерации свой. */
    private static boolean declaredInside(NameExpr list, Statement loop) {
        return loop.findAll(VariableDeclarator.class).stream().anyMatch(v -> v.getNameAsString().equals(list.getNameAsString()));
    }
}
