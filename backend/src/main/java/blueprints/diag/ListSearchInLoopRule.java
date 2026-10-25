package blueprints.diag;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * R10: {@code list.contains / indexOf / lastIndexOf} внутри цикла. Каждый вызов просматривает весь список,
 * поэтому цикл на n шагов по списку из n элементов делает порядка n² сравнений. Типичный код ИИ — «уникальные
 * значения» через {@code if (!seen.contains(x)) seen.add(x)}.
 *
 * Срабатываем, только если переменная объявлена стандартным списком java.util (List, ArrayList, LinkedList)
 * и объявлена не внутри этого же цикла: свежий список на каждой итерации — не рост. Заплатки нет: замена на
 * HashSet меняет поведение, если элементы сравниваются equals без согласованного hashCode или важен порядок.
 */
final class ListSearchInLoopRule {

    static final String RULE = "R10";
    private static final Set<String> LISTS = Set.of("List", "ArrayList", "LinkedList");
    private static final Set<String> SEARCHES = Set.of("contains", "indexOf", "lastIndexOf");

    List<Finding> find(DiagnosticContext context) {
        List<Finding> found = new ArrayList<>();
        for (MethodCallExpr call : context.unit.findAll(MethodCallExpr.class)) {
            if (!SEARCHES.contains(call.getNameAsString()) || call.getArguments().size() != 1
                    || !(call.getScope().map(DiagnosticContext::unwrapped).orElse(null) instanceof NameExpr list)
                    || context.methodId(call).isEmpty() || context.standardType(list, LISTS).isEmpty()) continue;
            Statement loop = DiagnosticContext.enclosingLoop(call).orElse(null);
            if (loop == null || DiagnosticContext.inputLoop(loop) || declaredInside(list, loop) || constantList(list, context)) continue;
            int ordinal = found.size() + 1;
            found.add(new Finding(RULE + "-" + ordinal, RULE, context.methodId(call).orElseThrow(),
                    DiagnosticContext.lines(call), "Поиск в списке внутри цикла",
                    "«" + list + "." + call.getNameAsString() + "(…)» каждый раз просматривает весь список, а вызывается на каждой "
                            + "итерации цикла: при n элементах это порядка n² сравнений. Множество (HashSet) или индекс "
                            + "по ключу находят элемент за одно обращение.",
                    "n²", "n",
                    List.of("Порядок элементов не важен или сохраняется отдельно (LinkedHashSet)",
                            "У элементов согласованы equals и hashCode",
                            "Заплатки нет: замена структуры данных требует проверки на ваших входах"),
                    List.of(), Finding.INEFFICIENCY));
        }
        return found;
    }

    /** Неизменяемый список из перечисленных литералов (List.of(…), Arrays.asList(…)): его размер — константа. */
    private static boolean constantList(NameExpr list, DiagnosticContext context) {
        return context.unit.findAll(com.github.javaparser.ast.body.VariableDeclarator.class).stream()
                .filter(v -> v.getNameAsString().equals(list.getNameAsString()))
                .anyMatch(v -> v.getInitializer().map(DiagnosticContext::unwrapped).filter(init -> init instanceof MethodCallExpr m
                        && m.getNameAsString().matches("of|asList")
                        && m.getScope().map(Object::toString).filter(s -> s.matches("(java\\.util\\.)?(List|Arrays)")).isPresent()
                        && m.getArguments().stream().allMatch(com.github.javaparser.ast.expr.Expression::isLiteralExpr)).isPresent());
    }

    /** Список объявлен внутри цикла — на каждой итерации свой, общий рост не накапливается. */
    private static boolean declaredInside(NameExpr list, Statement loop) {
        return loop.findAll(com.github.javaparser.ast.body.VariableDeclarator.class).stream()
                .anyMatch(v -> v.getNameAsString().equals(list.getNameAsString()) && isInside(v, loop));
    }

    private static boolean isInside(Node node, Node container) {
        return container.isAncestorOf(node);
    }
}
