package blueprints.instrument;

import blueprints.parse.SourceParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;

/** Поля, импорты и сигнатуры не имеют счётчиков: изменение рядом с оператором не должно их скрывать. */
final class DeclarationChanges {

    private DeclarationChanges() {}

    static boolean between(String original, String candidate) {
        return !declarations(original).equals(declarations(candidate));
    }

    static boolean sameCode(String original, String candidate) {
        return uncommented(original).equals(uncommented(candidate));
    }

    private static String declarations(String source) {
        CompilationUnit unit = uncommented(source);
        // Сначала собираем узлы: удаление внешнего тела может отсоединить вложенные объявления.
        var methods = unit.findAll(MethodDeclaration.class);
        var constructors = unit.findAll(ConstructorDeclaration.class);
        var compact = unit.findAll(CompactConstructorDeclaration.class);
        var initializers = unit.findAll(InitializerDeclaration.class);
        methods.forEach(method -> method.removeBody());
        constructors.forEach(constructor -> constructor.setBody(new BlockStmt()));
        compact.forEach(constructor -> constructor.setBody(new BlockStmt()));
        initializers.forEach(initializer -> initializer.setBody(new BlockStmt()));
        return unit.toString();
    }

    private static CompilationUnit uncommented(String source) {
        CompilationUnit unit = SourceParser.parseOrThrow(source);
        unit.getAllContainedComments().forEach(comment -> comment.remove());
        unit.removeComment();
        return unit;
    }
}
