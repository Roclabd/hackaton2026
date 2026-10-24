package blueprints.parse;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

/** Мелкие правила Java-файла, нужные в нескольких местах. */
public final class JavaFiles {

    private JavaFiles() {}

    /**
     * Имя, под которым файл отдаётся javac: публичный верхнеуровневый тип (javac требует совпадения
     * с именем файла), иначе первый тип, иначе «Main».
     */
    public static String primaryTypeName(CompilationUnit unit) {
        return unit.getTypes().stream()
                .filter(TypeDeclaration::isPublic)
                .findFirst()
                .or(() -> unit.getTypes().stream().findFirst())
                .map(TypeDeclaration::getNameAsString)
                .orElse("Main");
    }
}
