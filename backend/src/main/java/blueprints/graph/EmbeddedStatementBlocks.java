package blueprints.graph;

import blueprints.parse.SourceText;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Находит тела операторов, спрятанные в собственных выражениях одного узла чертежа. */
final class EmbeddedStatementBlocks {
    record Branch(String key, String label, List<Statement> statements) {}

    private final SourceText source;

    EmbeddedStatementBlocks(SourceText source) {
        this.source = source;
    }

    List<Branch> of(Statement statement) {
        List<Branch> result = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        statement.getChildNodes().forEach(child -> visit(child, result, seen));
        return result;
    }

    private void visit(Node node, List<Branch> result, Map<String, Integer> seen) {
        if (node instanceof LambdaExpr lambda) {
            if (lambda.getBody() instanceof BlockStmt body) {
                String params = lambda.getParameters().stream().map(this::text).collect(Collectors.joining(", "));
                result.add(new Branch(key("lambda", params, seen), "лямбда (" + params + ") → · тело", body.getStatements()));
            } else {
                lambda.getExpressionBody().ifPresent(body -> visit(body, result, seen));
            }
            return;
        }
        if (node instanceof SwitchExpr choice) {
            String selector = text(choice.getSelector());
            String key = key("switch", selector, seen);
            visit(choice.getSelector(), result, seen);
            for (int i = 0; i < choice.getEntries().size(); i++) {
                SwitchEntry entry = choice.getEntries().get(i);
                entry.getGuard().ifPresent(guard -> visit(guard, result, seen));
                if (entry.getType() == SwitchEntry.Type.EXPRESSION) {
                    // Значение вроде «10» не является самостоятельным оператором Java; его показывает expr.
                    entry.getStatements().forEach(s -> s.getChildNodes().forEach(child -> visit(child, result, seen)));
                    continue;
                }
                String labels = entry.getLabels().stream().map(this::text).collect(Collectors.joining(", "));
                String guard = entry.getGuard().map(g -> " when " + text(g)).orElse("");
                String arrow = entry.getType() == SwitchEntry.Type.STATEMENT_GROUP ? ":" : " ->";
                String label = (entry.getLabels().isEmpty() ? "default" : "case " + labels) + guard + arrow;
                result.add(new Branch(key + ":case:" + i, "switch (" + selector + ") · " + label, entry.getStatements()));
            }
            return;
        }
        // Обычные тела управления раскроет builder. Объявления классов принадлежат своим методам.
        if (node instanceof Statement || node instanceof BodyDeclaration<?>) return;
        node.getChildNodes().forEach(child -> visit(child, result, seen));
    }

    private static String key(String kind, String header, Map<String, Integer> seen) {
        String base = kind + ":" + Integer.toHexString(header.replaceAll("\\s+", " ").strip().hashCode());
        int repeat = seen.merge(base, 1, Integer::sum);
        return repeat == 1 ? base : base + "~" + repeat;
    }

    private String text(Node node) {
        return node.getRange().map(source::slice).orElseGet(node::toString);
    }
}
