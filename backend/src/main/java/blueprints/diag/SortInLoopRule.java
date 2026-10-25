package blueprints.diag;

import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * R11: сортировка внутри цикла — {@code list.sort(…)}, {@code Collections.sort(…)}, {@code Arrays.sort(…)}.
 * Каждая сортировка стоит n·log n, на каждой из n итераций — n²·log n. Обычно достаточно отсортировать
 * один раз после цикла. Заплатки нет: если отсортированный порядок читается внутри цикла, перенос меняет поведение.
 */
final class SortInLoopRule {

    static final String RULE = "R11";
    private static final Set<String> LISTS = Set.of("List", "ArrayList", "LinkedList");

    List<Finding> find(DiagnosticContext context) {
        List<Finding> found = new ArrayList<>();
        for (MethodCallExpr call : context.unit.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals("sort") || context.methodId(call).isEmpty() || !isSort(call, context)) continue;
            var loop = DiagnosticContext.enclosingLoop(call);
            if (loop.isEmpty() || DiagnosticContext.inputLoop(loop.get())) continue;
            int ordinal = found.size() + 1;
            found.add(new Finding(RULE + "-" + ordinal, RULE, context.methodId(call).orElseThrow(),
                    DiagnosticContext.lines(call), "Сортировка внутри цикла",
                    "Сортировка выполняется на каждой итерации цикла: n итераций по n·log n — порядка n²·log n операций. "
                            + "Если отсортированный порядок нужен только после цикла, достаточно одной сортировки в конце.",
                    "n² log n", "n log n",
                    List.of("Внутри цикла не читается отсортированный порядок",
                            "Заплатки нет: перенос сортировки нужно проверить запуском"),
                    List.of(), Finding.INEFFICIENCY));
        }
        return found;
    }

    /** list.sort(…) у стандартного списка или Collections.sort / Arrays.sort (в том числе java.util.Arrays.sort). */
    private static boolean isSort(MethodCallExpr call, DiagnosticContext context) {
        var scope = call.getScope().map(DiagnosticContext::unwrapped).orElse(null);
        if (scope instanceof NameExpr name) {
            String owner = name.getNameAsString();
            if (owner.equals("Collections") || owner.equals("Arrays")) return ownTypeAbsent(owner, context);
            return context.standardType(name, LISTS).isPresent();
        }
        if (scope instanceof FieldAccessExpr field && field.toString().matches("java\\.util\\.(Collections|Arrays)")) return true;
        return false;
    }

    /** Collections / Arrays — именно java.util: в файле нет своего класса и переменной с таким именем. */
    private static boolean ownTypeAbsent(String name, DiagnosticContext context) {
        return context.unit.findAll(com.github.javaparser.ast.body.TypeDeclaration.class).stream()
                .noneMatch(t -> t.getNameAsString().equals(name))
                && context.unit.findAll(com.github.javaparser.ast.body.VariableDeclarator.class).stream()
                .noneMatch(v -> v.getNameAsString().equals(name));
    }
}
