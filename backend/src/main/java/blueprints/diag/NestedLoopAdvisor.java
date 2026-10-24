package blueprints.diag;

import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;

import java.util.List;
import java.util.Optional;

/**
 * Превращает найденную пару циклов в находку R1: объяснение по-русски, рост, условия и заплатку, если она есть.
 * Сначала пробует «индекс частот» (подсчёт совпадений), затем «индекс групп» (форма слайда 5).
 */
final class NestedLoopAdvisor {

    static final String RULE = "R1";

    private final FrequencyIndexPatch frequency = new FrequencyIndexPatch();
    private final GroupIndexPatch group = new GroupIndexPatch();

    Finding advise(LoopPair p, SourceText source, String findingId) {
        String patchId = findingId + "-index";
        Optional<PatchProposal> proposal = frequency.build(p, source, patchId)
                .or(() -> group.build(p, source, patchId));

        String why = growthWhy(p) + selfPairTrap(p).orElse("")
                + proposal.map(PatchProposal::why).orElseGet(() -> noPatchWhy(p));
        return new Finding(findingId, RULE, p.methodId(), lines(p.outer()), "Вложенный цикл", why,
                "n²",
                proposal.map(PatchProposal::growthAfter).orElse(null),
                proposal.map(PatchProposal::conditions).orElse(List.of()),
                proposal.map(pp -> List.of(pp.patch())).orElse(List.of()));
    }

    private static String growthWhy(LoopPair p) {
        String a = p.outer().getIterable().toString();
        String b = p.inner().getIterable().toString();
        return a.equals(b)
                ? "Каждый элемент " + a + " сравнивается со всеми элементами того же списка: число сравнений растёт как n²."
                : "Для каждого элемента " + a + " просматривается весь список " + b + ": число сравнений растёт как n².";
    }

    /**
     * Ловушка слайда 5: список сравнивается сам с собой, а в if вызывается действие над парой (merge(a, b)).
     * Тогда выполняется и merge(a, a) для каждого элемента, и каждая пара — дважды.
     */
    private static Optional<String> selfPairTrap(LoopPair p) {
        if (!p.outer().getIterable().toString().equals(p.inner().getIterable().toString())) {
            return Optional.empty();
        }
        return p.check().getThenStmt().findAll(MethodCallExpr.class).stream()
                .filter(call -> passes(call, p.outerVar()) && passes(call, p.innerVar()))
                .findFirst()
                .map(call -> {
                    String self = call.getNameAsString() + "(" + p.outerVar() + ", " + p.outerVar() + ")";
                    return " Ловушка: список сравнивается сам с собой, поэтому " + self
                            + " выполняется для каждого элемента (самослияние), а каждая пара разных элементов с равным ключом"
                            + " обрабатывается дважды — (" + p.outerVar() + ", " + p.innerVar() + ") и ("
                            + p.innerVar() + ", " + p.outerVar() + "). «Очевидный» индекс Map<ключ, элемент> теряет эти вызовы"
                            + " и молча меняет результат. Если двойная обработка — ошибка, её исправление меняет поведение"
                            + " и оформляется отдельно, как BUGFIX.";
                });
    }

    private static boolean passes(MethodCallExpr call, String variable) {
        return call.getArguments().stream().anyMatch(arg -> arg instanceof NameExpr n && n.getNameAsString().equals(variable));
    }

    /** Почему готовой заплатки нет — чтобы человек не принял её отсутствие за недосмотр. */
    private static String noPatchWhy(LoopPair p) {
        String condition = p.check().getCondition().toString();
        EqualityTest test = p.test();
        if (test.kind() == EqualityTest.Kind.SAME_REFERENCE) {
            return " Готовой заплатки нет: «" + condition + "» для объектов сравнивает ссылки,"
                    + " а индекс на HashMap сравнивает через equals — результат может измениться.";
        }
        if (test.kind() == EqualityTest.Kind.EQUALS_METHOD && test.bareVariables()) {
            return " Готовой заплатки нет: «" + condition + "» бросает NullPointerException на значении null,"
                    + " а индекс на HashMap — нет; такая замена молча изменит поведение.";
        }
        if (test.kind() == EqualityTest.Kind.EQUALS_METHOD && !test.outerFirst()) {
            return " Готовой заплатки нет: при ключе null исключение здесь возникает на элементе внутреннего списка"
                    + " посреди перебора — индекс не повторит его в тот же момент.";
        }
        return " Готовой заплатки нет: тело цикла сложнее поддерживаемых форм — исправление нужно писать вручную,"
                + " а проверка покажет, сохранилось ли поведение.";
    }

    private static int[] lines(ForEachStmt loop) {
        return loop.getRange().map(r -> new int[]{r.begin.line, r.end.line}).orElse(new int[]{0, 0});
    }
}
