package blueprints.diag;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * R17: внутри цикла вызывается свой метод, который сам ищет значение перебором —
 * {@code for (…) { indexOfWord(list, w); }}, где indexOfWord идёт по list до совпадения с word.
 * Цикл на n шагов × поиск по n элементам = порядка n² сравнений; R10 этого не видит, потому что поиск спрятан в метод.
 *
 * Консервативно: вызываемый метод — единственный с таким именем и числом параметров в том же классе, и в нём есть
 * цикл по параметру-массиву или коллекции, внутри которого if сравнивает элемент (== или equals) с другим
 * параметром и сразу выходит (return или break). Это и есть линейный поиск. Заплатки нет.
 */
final class OwnSearchInLoopRule {

    static final String RULE = "R17";

    List<Finding> find(DiagnosticContext context) {
        List<Finding> found = new ArrayList<>();
        for (MethodCallExpr call : context.unit.findAll(MethodCallExpr.class,
                c -> c.getScope().isEmpty() || c.getScope().get() instanceof ThisExpr)) {
            Optional<Statement> loop = DiagnosticContext.enclosingLoop(call);
            if (context.methodId(call).isEmpty() || loop.isEmpty() || DiagnosticContext.inputLoop(loop.get())
                    || IndexLoop.of(loop.get()).filter(IndexLoop::growing).isEmpty()) continue;
            Optional<MethodDeclaration> callee = callee(call);
            if (callee.isEmpty() || callee.get().isAncestorOf(call)) continue;
            Optional<Integer> searched = searchedParameter(callee.get());
            if (searched.isEmpty() || freshInside(call.getArgument(searched.get()), loop.get())) continue;
            // Два поиска в одном условии — одна находка на строку.
            if (found.stream().anyMatch(f -> f.lines()[0] == DiagnosticContext.lines(call)[0])) continue;
            String name = callee.get().getNameAsString();
            String list = call.getArgument(searched.get()).toString();
            found.add(new Finding(RULE + "-" + (found.size() + 1), RULE, context.methodId(call).orElseThrow(),
                    DiagnosticContext.lines(call), "Поиск перебором в цикле",
                    "Метод «" + name + "» ищет значение в «" + list + "», перебирая элементы по одному, а вызывается "
                            + "на каждом шаге цикла: при n элементах это порядка n² сравнений. Индекс (HashMap: значение → "
                            + "позиция) или множество, построенные один раз до цикла, находят значение за одно обращение.",
                    "n²", "n",
                    List.of("Данные, по которым ищем, не меняются внутри цикла — или индекс обновляется вместе с ними",
                            "У элементов согласованы equals и hashCode (для чисел и строк — да)",
                            "Заплатки нет: замену поиска на индекс нужно проверить на ваших входах"),
                    List.of(), Finding.INEFFICIENCY));
        }
        return found;
    }

    /** Единственный метод того же класса с таким именем и числом параметров; перегрузки не угадываем. */
    private static Optional<MethodDeclaration> callee(MethodCallExpr call) {
        Optional<TypeDeclaration> owner = call.findAncestor(TypeDeclaration.class);
        if (owner.isEmpty()) return Optional.empty();
        List<MethodDeclaration> candidates = ((TypeDeclaration<?>) owner.get()).getMethodsByName(call.getNameAsString()).stream()
                .filter(m -> m.getParameters().size() == call.getArguments().size() && m.getBody().isPresent()).toList();
        return candidates.size() == 1 && ((TypeDeclaration<?>) owner.get()).getMethodsByName(call.getNameAsString()).size() == 1
                ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    /** Номер параметра, по которому метод ищет перебором; пусто, если это не линейный поиск. */
    private static Optional<Integer> searchedParameter(MethodDeclaration method) {
        List<Parameter> parameters = method.getParameters();
        for (int p = 0; p < parameters.size(); p++) {
            String data = parameters.get(p).getNameAsString();
            if (!(parameters.get(p).getType().isArrayType() || parameters.get(p).getType().isClassOrInterfaceType())) continue;
            for (Statement statement : method.findAll(Statement.class, s -> s instanceof ForStmt || s instanceof ForEachStmt)) {
                Optional<IndexLoop> loop = IndexLoop.of(statement).filter(l -> walks(l, data));
                if (loop.isPresent() && searchesFor(loop.get(), parameters, data)) return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** for (x : data) или for (i …; i < data.length / data.size(); …). */
    private static boolean walks(IndexLoop loop, String data) {
        if (loop.loop() instanceof ForEachStmt each) {
            return DiagnosticContext.unwrapped(each.getIterable()) instanceof NameExpr n && n.getNameAsString().equals(data);
        }
        return ((ForStmt) loop.loop()).getCompare().map(Object::toString)
                .filter(c -> c.equals(loop.variable() + " < " + data + ".length") || c.equals(loop.variable() + " < " + data + ".size()"))
                .isPresent();
    }

    /** if (элемент == другой параметр) { return …; / break; } */
    private static boolean searchesFor(IndexLoop loop, List<Parameter> parameters, String data) {
        for (IfStmt check : loop.body().findAll(IfStmt.class)) {
            boolean exits = !check.getThenStmt().findAll(ReturnStmt.class).isEmpty() || !check.getThenStmt().findAll(BreakStmt.class).isEmpty();
            if (!exits) continue;
            for (Parameter key : parameters) {
                if (key.getNameAsString().equals(data)) continue;
                if (comparesWith(check.getCondition(), loop, key.getNameAsString())) return true;
            }
        }
        return false;
    }

    private static boolean comparesWith(Expression condition, IndexLoop loop, String key) {
        for (BinaryExpr eq : condition.findAll(BinaryExpr.class, b -> b.getOperator() == BinaryExpr.Operator.EQUALS)) {
            if (elementAndKey(eq.getLeft(), eq.getRight(), loop, key)) return true;
        }
        for (MethodCallExpr eq : condition.findAll(MethodCallExpr.class, c -> c.getNameAsString().equals("equals")
                && c.getArguments().size() == 1 && c.getScope().isPresent())) {
            if (elementAndKey(eq.getScope().get(), eq.getArgument(0), loop, key)) return true;
        }
        return false;
    }

    private static boolean elementAndKey(Expression a, Expression b, IndexLoop loop, String key) {
        return loop.selects(a, key) && isName(b, key) || loop.selects(b, key) && isName(a, key);
    }

    private static boolean isName(Expression e, String name) {
        return DiagnosticContext.unwrapped(e) instanceof NameExpr n && n.getNameAsString().equals(name);
    }

    /** Данные для поиска объявлены внутри цикла — на каждом шаге свои, общий рост не накапливается. */
    private static boolean freshInside(Expression argument, Node loop) {
        if (!(DiagnosticContext.unwrapped(argument) instanceof NameExpr name)) return false;
        return loop.findAll(VariableDeclarator.class).stream().anyMatch(v -> v.getNameAsString().equals(name.getNameAsString()));
    }
}
