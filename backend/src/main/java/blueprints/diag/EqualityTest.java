package blueprints.diag;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.*;

import java.util.Optional;

/**
 * Условие-равенство между переменной внешнего цикла (x) и внутреннего (y).
 *
 * Поддерживаемые формы: Objects.equals(L, R), L.equals(R), L == R,
 * где каждая сторона — сама переменная (x), её поле (x.id) или метод без аргументов (x.id()).
 *
 * @param kind       как сравнивают — от этого зависит, есть ли эквивалентная заплатка
 * @param outerKey   сторона, построенная из x
 * @param innerKey   сторона, построенная из y
 * @param outerFirst x стоит слева: Objects.equals(x, y), x.id().equals(y.id())
 */
record EqualityTest(Kind kind, Expression outerKey, Expression innerKey, boolean outerFirst) {

    enum Kind {
        /** java.util.Objects.equals(L, R) — null-безопасно, как поиск в HashMap. */
        OBJECTS_EQUALS,
        /** L.equals(R) — NullPointerException, если L == null. */
        EQUALS_METHOD,
        /** L == R — для объектов сравнивает ссылки, а не значения. */
        SAME_REFERENCE
    }

    /** Ключи — сами переменные (x и y), а не их поля или методы. */
    boolean bareVariables() {
        return outerKey.isNameExpr() && innerKey.isNameExpr();
    }

    static Optional<EqualityTest> recognize(Expression condition, String outerVar, String innerVar) {
        Expression c = unwrap(condition);
        if (c instanceof MethodCallExpr call && call.getNameAsString().equals("equals")) {
            if (call.getArguments().size() == 2 && call.getScope().filter(EqualityTest::isJavaUtilObjects).isPresent()) {
                return of(Kind.OBJECTS_EQUALS, call.getArgument(0), call.getArgument(1), outerVar, innerVar);
            }
            if (call.getArguments().size() == 1 && call.getScope().isPresent()) {
                return of(Kind.EQUALS_METHOD, call.getScope().get(), call.getArgument(0), outerVar, innerVar);
            }
        }
        if (c instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.EQUALS) {
            return of(Kind.SAME_REFERENCE, binary.getLeft(), binary.getRight(), outerVar, innerVar);
        }
        return Optional.empty();
    }

    private static Optional<EqualityTest> of(Kind kind, Expression left, Expression right, String outerVar, String innerVar) {
        Expression l = unwrap(left);
        Expression r = unwrap(right);
        if (keyOf(l, outerVar) && keyOf(r, innerVar)) {
            return Optional.of(new EqualityTest(kind, l, r, true));
        }
        if (keyOf(l, innerVar) && keyOf(r, outerVar)) {
            return Optional.of(new EqualityTest(kind, r, l, false));
        }
        return Optional.empty();
    }

    /** x, x.id или x.id() — значение, которое однозначно получается из переменной цикла. */
    private static boolean keyOf(Expression e, String variable) {
        if (isName(e, variable)) {
            return true;
        }
        if (e instanceof FieldAccessExpr field) {
            return isName(field.getScope(), variable);
        }
        if (e instanceof MethodCallExpr call) {
            return call.getArguments().isEmpty() && call.getTypeArguments().isEmpty()
                    && call.getScope().filter(s -> isName(s, variable)).isPresent();
        }
        return false;
    }

    private static boolean isName(Expression e, String name) {
        return e instanceof NameExpr n && n.getNameAsString().equals(name);
    }

    private static Expression unwrap(Expression e) {
        Expression current = e;
        while (current instanceof EnclosedExpr enclosed) {
            current = enclosed.getInner();
        }
        return current;
    }

    /**
     * Objects — именно java.util.Objects: полное имя или импорт, и в файле нет своего класса Objects.
     * Без этого чужой Objects.equals с другой логикой дал бы неэквивалентную заплатку.
     */
    private static boolean isJavaUtilObjects(Expression scope) {
        String text = scope.toString();
        if (text.equals("java.util.Objects")) {
            return true;
        }
        if (!text.equals("Objects")) {
            return false;
        }
        Optional<CompilationUnit> unit = scope.findCompilationUnit();
        if (unit.isEmpty()) {
            return false;
        }
        boolean ownClass = unit.get().findFirst(com.github.javaparser.ast.body.TypeDeclaration.class,
                t -> t.getNameAsString().equals("Objects")).isPresent();
        boolean imported = unit.get().getImports().stream().anyMatch(EqualityTest::importsObjects);
        return imported && !ownClass;
    }

    private static boolean importsObjects(ImportDeclaration declaration) {
        if (declaration.isStatic()) {
            return false;
        }
        String name = declaration.getNameAsString();
        return declaration.isAsterisk() ? name.equals("java.util") : name.equals("java.util.Objects");
    }
}
