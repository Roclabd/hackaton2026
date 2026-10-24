package blueprints.diag;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.SimpleName;

import java.util.HashSet;
import java.util.Set;

/**
 * Свободные имена для новых переменных заплатки. Занятым считается любое имя, встреченное в методе:
 * так новая переменная не перекроет ни локальную, ни поле, на которое ссылается метод.
 */
final class NameAllocator {

    private final Set<String> used = new HashSet<>();

    NameAllocator(Node declaration) {
        declaration.findAll(SimpleName.class).forEach(n -> used.add(n.getIdentifier()));
    }

    /** base, а если занято — base2, base3… Выданное имя тоже становится занятым. */
    String fresh(String base) {
        String candidate = base;
        for (int i = 2; used.contains(candidate); i++) {
            candidate = base + i;
        }
        used.add(candidate);
        return candidate;
    }
}
