package blueprints.runner;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.List;
import java.util.Optional;

/**
 * Какой класс запускать и как назвать файл для javac. Решается по дереву разбора, а не по тексту.
 */
public final class MainClassFinder {

    private MainClassFinder() {}

    /**
     * Порядок: публичный верхнеуровневый класс с {@code public static void main(String[])},
     * затем любой верхнеуровневый с таким main, затем первый публичный тип, затем первый тип.
     *
     * @return полное имя класса (с пакетом, если он объявлен)
     * @throws IllegalArgumentException в файле нет ни одного типа
     */
    public static String find(CompilationUnit unit) {
        List<TypeDeclaration<?>> types = unit.getTypes();
        if (types.isEmpty()) {
            throw new IllegalArgumentException("В файле нет ни одного класса");
        }
        TypeDeclaration<?> chosen = first(types, t -> t.isPublic() && hasMain(t))
                .or(() -> first(types, MainClassFinder::hasMain))
                .or(() -> first(types, TypeDeclaration::isPublic))
                .orElse(types.get(0));
        String packagePrefix = unit.getPackageDeclaration().map(p -> p.getNameAsString() + ".").orElse("");
        return packagePrefix + chosen.getNameAsString();
    }

    private static Optional<TypeDeclaration<?>> first(List<TypeDeclaration<?>> types,
                                                      java.util.function.Predicate<TypeDeclaration<?>> test) {
        return types.stream().filter(test).findFirst();
    }

    /**
     * Есть ли у класса main, который запустит команда {@code java}: классический или вариант JDK 25
     * (без static и/или без аргументов). Без него программу не запустить — проверять поведение нечего.
     *
     * @param className полное имя, как вернул {@link #find}
     */
    public static boolean hasLaunchableMain(CompilationUnit unit, String className) {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        return unit.getTypes().stream()
                .filter(t -> t.getNameAsString().equals(simpleName))
                .flatMap(t -> t.getMethodsByName("main").stream())
                .anyMatch(MainClassFinder::isLaunchableMain);
    }

    private static boolean isLaunchableMain(MethodDeclaration method) {
        if (method.isPrivate() || !method.getType().isVoidType()) {
            return false;
        }
        return method.getParameters().isEmpty()
                || method.getParameters().size() == 1 && isStringArray(method.getParameter(0));
    }

    static boolean hasMain(TypeDeclaration<?> type) {
        return type.getMethodsByName("main").stream().anyMatch(MainClassFinder::isClassicMain);
    }

    private static boolean isClassicMain(MethodDeclaration method) {
        if (!method.hasModifier(Modifier.Keyword.PUBLIC) || !method.isStatic()
                || !method.getType().isVoidType() || method.getParameters().size() != 1) {
            return false;
        }
        return isStringArray(method.getParameter(0));
    }

    private static boolean isStringArray(Parameter parameter) {
        String type = parameter.getType().asString();
        boolean array = type.equals("String[]") || type.equals("java.lang.String[]");
        boolean varargs = parameter.isVarArgs() && (type.equals("String") || type.equals("java.lang.String"));
        return array || varargs;
    }
}
