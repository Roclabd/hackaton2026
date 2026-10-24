package blueprints.diag;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.IfStmt;

/**
 * Что нашло правило R1: внешний цикл, вложенный в него цикл и if, сравнивающий их переменные.
 *
 * @param methodId    id метода на карте
 * @param declaration объявление метода (нужно, чтобы искать локальные переменные и занятые имена)
 */
record LoopPair(String methodId, Node declaration, ForEachStmt outer, ForEachStmt inner,
                IfStmt check, EqualityTest test) {

    String outerVar() {
        return outer.getVariableDeclarator().getNameAsString();
    }

    String innerVar() {
        return inner.getVariableDeclarator().getNameAsString();
    }
}
