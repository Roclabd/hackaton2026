package blueprints.runner;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.Set;

/** Явное чтение/печать стека требует настоящего java main, без кадров рабочего процесса. */
public final class StackSensitive {

    private static final Set<String> METHODS = Set.of("printStackTrace", "getStackTrace", "fillInStackTrace", "dumpStack");

    private StackSensitive() {}

    public static boolean needsPlainJvm(CompilationUnit unit) {
        // Без разрешения типов выбираем консервативно: одноимённый пользовательский метод тоже пойдёт в отдельную JVM.
        return unit.findAll(MethodCallExpr.class).stream().anyMatch(call -> METHODS.contains(call.getNameAsString()))
                || unit.findAll(MethodReferenceExpr.class).stream().anyMatch(ref -> METHODS.contains(ref.getIdentifier()))
                || unit.findAll(ClassOrInterfaceType.class).stream().anyMatch(type -> type.getNameAsString().equals("StackWalker"))
                || unit.findAll(NameExpr.class).stream().anyMatch(name -> name.getNameAsString().equals("StackWalker"))
                || unit.findAll(FieldAccessExpr.class).stream().anyMatch(field -> field.getNameAsString().equals("StackWalker"))
                || unit.getImports().stream().anyMatch(imported -> imported.getNameAsString().equals("java.lang.StackWalker")
                        || imported.getNameAsString().startsWith("java.lang.StackWalker."));
    }
}
