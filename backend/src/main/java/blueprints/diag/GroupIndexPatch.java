package blueprints.diag;

import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Заплатка «индекс групп» для формы со слайда 5:
 *
 *   for (a : items) for (b : items) if (a.id().equals(b.id())) merge(a, b);
 *
 * Индекс «ключ → элементы b в исходном порядке» (Map&lt;K, List&lt;U&gt;&gt;), а не «ключ → один элемент»:
 * так тело if выполняется для тех же пар и в том же порядке, включая пару (a, a).
 * «Очевидный» Map&lt;K, U&gt; эти вызовы теряет и молча меняет результат (docs/эксперименты/Slide5Trap_результат.md).
 *
 * Для L.equals(R) сохраняется и исключение: при ключе x == null оригинал падает с NullPointerException
 * на первом же y — заплатка вызывает тот же x.id().equals(x.id()), чтобы текст исключения совпал слово в слово.
 */
final class GroupIndexPatch {

    Optional<PatchProposal> build(LoopPair p, SourceText source, String patchId) {
        EqualityTest test = p.test();
        // x.id().equals(y.id()): NPE только на ключе x — его повторяем. Для y.id().equals(x.id()) исключение
        // возникало бы на элементе b посреди перебора — индекс этого не повторит. x.equals(y) — по заданию без заплатки.
        boolean nullSafe = test.kind() == EqualityTest.Kind.OBJECTS_EQUALS;
        boolean equalsOnOuterKey = test.kind() == EqualityTest.Kind.EQUALS_METHOD
                && test.outerFirst() && !test.outerKey().isNameExpr();
        if (!(nullSafe || equalsOnOuterKey) || !LoopShape.plainNestedIf(p) || !LoopShape.standsInBlock(p.outer())) {
            return Optional.empty();
        }
        Optional<String> a = LoopShape.simpleName(p.outer().getIterable());
        Optional<String> b = LoopShape.simpleName(p.inner().getIterable());
        Type elementType = p.inner().getVariableDeclarator().getType();
        Statement then = p.check().getThenStmt();
        // List<var> и List<int> не пишутся; тело, которое трогает сами списки, ломает предпосылку «списки не меняются».
        if (a.isEmpty() || b.isEmpty() || !elementType.isReferenceType() || elementType.isVarType()
                || mentions(then, a.get()) || mentions(then, b.get()) || source.textOf(p.outer()).isEmpty()) {
            return Optional.empty();
        }

        NameAllocator names = new NameAllocator(p.declaration());
        String index = names.fresh(indexName(test));
        String key = names.fresh("k");
        String outerKey = test.outerKey().toString();

        CodeLines code = new CodeLines()
                .add(0, "java.util.Map<Object, java.util.List<" + elementType + ">> " + index + " = null;")
                .add(0, "for (" + p.outer().getVariable() + " : " + a.get() + ") {")
                .add(1, "if (" + index + " == null) {")
                .add(2, index + " = new java.util.LinkedHashMap<>();")
                .add(2, "for (" + p.inner().getVariable() + " : " + b.get() + ") {")
                .add(3, index + ".computeIfAbsent(" + test.innerKey() + ", " + key
                        + " -> new java.util.ArrayList<>()).add(" + p.innerVar() + ");")
                .add(2, "}")
                .add(1, "}");
        if (equalsOnOuterKey) {
            code.add(1, "if (!" + index + ".isEmpty()) {")
                    .add(2, outerKey + ".equals(" + outerKey + "); // как в оригинале: NullPointerException, если ключ null")
                    .add(1, "}");
        }
        code.add(1, "for (" + p.inner().getVariable() + " : " + index + ".getOrDefault(" + outerKey
                + ", java.util.List.of())) {");
        for (Statement s : LoopShape.statements(then)) {
            code.addBlock(2, s.toString());
        }
        code.add(1, "}").add(0, "}");

        int firstLine = p.outer().getBegin().orElseThrow().line;
        String replacement = code.render(source.indentOf(firstLine), source.lineSeparator());
        Patch patch = new Patch(patchId, Patch.REFACTOR, "Индекс групп по ключу вместо перебора",
                "Один проход по " + b.get() + " раскладывает элементы по ключу " + test.innerKey()
                        + " в исходном порядке; для каждого " + p.outerVar() + " перебираются только элементы с тем же ключом. "
                        + "Тело if выполняется для тех же пар и в том же порядке.",
                source.replace(p.outer(), replacement),
                new int[]{firstLine, firstLine + code.size() - 1});
        return Optional.of(new PatchProposal(patch, "n + m", conditions(p, a.get(), b.get()),
                " Заплатка строит индекс групп «ключ → элементы в исходном порядке» и выполняет тело if для тех же пар"
                        + " в том же порядке" + (equalsOnOuterKey ? "; NullPointerException на ключе null сохранён" : "")
                        + ". m — число выполнений тела: при уникальных ключах m = n, если все ключи равны — n²."));
    }

    private static List<String> conditions(LoopPair p, String a, String b) {
        EqualityTest test = p.test();
        List<String> conditions = new ArrayList<>();
        conditions.add(a.equals(b)
                ? "Список " + a + " не меняется внутри цикла"
                : "Списки " + a + " и " + b + " не меняются внутри цикла");
        conditions.add("Тело if не меняет " + test.outerKey() + " и " + test.innerKey() + " у элементов");
        if (!test.bareVariables()) {
            conditions.add(test.outerKey() + " и " + test.innerKey()
                    + " без побочных эффектов: повторный вызов даёт то же значение");
        }
        conditions.add("У ключей согласованы equals и hashCode");
        conditions.add(a.equals(b)
                ? a + " — переменная: вычисляется один раз"
                : a + " и " + b + " — переменные: вычисляются один раз");
        return conditions;
    }

    /** byId для x.id() / x.getId() / x.id, byValue для самих элементов. */
    private static String indexName(EqualityTest test) {
        String member = switch (test.innerKey()) {
            case MethodCallExpr call -> call.getNameAsString();
            case FieldAccessExpr field -> field.getNameAsString();
            default -> "value";
        };
        if (member.startsWith("get") && member.length() > 3) {
            member = member.substring(3);
        }
        return "by" + Character.toUpperCase(member.charAt(0)) + member.substring(1);
    }

    private static boolean mentions(Statement statement, String name) {
        return statement.findAll(NameExpr.class).stream().anyMatch(n -> n.getNameAsString().equals(name));
    }
}
