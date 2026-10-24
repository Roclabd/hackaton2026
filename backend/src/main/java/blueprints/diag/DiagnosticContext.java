package blueprints.diag;

import blueprints.graph.ProjectMapBuilder;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Контекст находки и намеренно узкое определение типов без Symbol Solver. */
final class DiagnosticContext {
    final CompilationUnit unit;
    final SourceText source;
    private final Map<String, Node> declarations;

    DiagnosticContext(CompilationUnit unit, SourceText source) {
        this.unit = unit;
        this.source = source;
        declarations = new ProjectMapBuilder().declarations(unit);
    }

    Optional<String> methodId(Node node) {
        for (Node current = node; current != null; current = current.getParentNode().orElse(null)) {
            Node declaration = current;
            Optional<String> id = declarations.entrySet().stream()
                    .filter(e -> e.getValue() == declaration).map(Map.Entry::getKey).findFirst();
            if (id.isPresent()) return id;
            // Локальные/анонимные методы не приписываем внешнему методу карты.
            if (current instanceof CallableDeclaration<?>) return Optional.empty();
        }
        return Optional.empty();
    }

    Finding finding(String rule, Node node, String title, String why, List<String> conditions,
                    List<Patch> patches, String category, int ordinal) {
        return new Finding(rule + "-" + ordinal, rule, methodId(node).orElseThrow(), lines(node), title, why,
                null, null, conditions, patches, category);
    }

    List<Patch> bugfix(String id, Node node, String replacement, String title, String description) {
        if (source.textOf(node).isEmpty()) return List.of();
        int begin = lines(node)[0];
        return List.of(new Patch(id + "-fix", Patch.BUGFIX, title, description,
                source.replace(node, replacement), new int[]{begin, begin + (int) replacement.lines().count() - 1}));
    }

    static int[] lines(Node node) {
        return node.getRange().map(r -> new int[]{r.begin.line, r.end.line}).orElse(new int[]{0, 0});
    }

    static Expression unwrapped(Expression expression) {
        while (expression instanceof EnclosedExpr enclosed) expression = enclosed.getInner();
        return expression;
    }

    /** При нескольких одноимённых объявлениях отказываемся угадывать привязку. */
    Optional<Type> declaredType(NameExpr name) {
        Optional<CallableDeclaration> callable = name.findAncestor(CallableDeclaration.class);
        if (callable.isPresent()) {
            for (Node boundary = name.getParentNode().orElse(null); boundary != null && boundary != callable.get();
                 boundary = boundary.getParentNode().orElse(null)) {
                if (boundary instanceof LambdaExpr || boundary instanceof TypeDeclaration<?>) return Optional.empty();
            }
            List<VariableDeclarator> locals = callable.get().findAll(VariableDeclarator.class).stream()
                    .filter(v -> v.getNameAsString().equals(name.getNameAsString()) && localTo(v, callable.get())).toList();
            List<Parameter> parameters = callable.get().findAll(Parameter.class).stream()
                    .filter(p -> p.getNameAsString().equals(name.getNameAsString())).toList();
            if (locals.size() + parameters.size() > 1) return Optional.empty();
            if (locals.size() == 1) {
                VariableDeclarator local = locals.getFirst();
                return visible(local, name) ? Optional.of(local.getType()) : Optional.empty();
            }
            if (parameters.size() == 1 && parameters.getFirst().getParentNode().orElse(null) == callable.get()) {
                return Optional.of(parameters.getFirst().getType());
            }
        }
        // Поля не анализируем: без разрешения имён трудно исключить наследование и затенение.
        return Optional.empty();
    }

    private boolean localTo(VariableDeclarator variable, Node callable) {
        if (!(variable.getParentNode().orElse(null) instanceof VariableDeclarationExpr)) return false;
        for (Node parent = variable.getParentNode().orElse(null); parent != null; parent = parent.getParentNode().orElse(null)) {
            if (parent == callable) return true;
            if (parent instanceof CallableDeclaration<?> || parent instanceof TypeDeclaration<?> || parent instanceof LambdaExpr) return false;
        }
        return false;
    }

    private boolean visible(VariableDeclarator declaration, NameExpr use) {
        if (declaration.getBegin().isEmpty() || use.getBegin().isEmpty()
                || declaration.getBegin().get().isAfter(use.getBegin().get())) return false;
        for (Node node = declaration; node != null; node = node.getParentNode().orElse(null)) {
            if (node instanceof BlockStmt || node instanceof ForStmt || node instanceof ForEachStmt) {
                return node == use || node.isAncestorOf(use);
            }
        }
        return false;
    }

    String type(Expression value) {
        value = unwrapped(value);
        if (value instanceof NameExpr name) return declaredType(name).map(Type::asString).orElse("");
        if (value instanceof StringLiteralExpr || value instanceof TextBlockLiteralExpr) return "java.lang.String";
        if (value instanceof IntegerLiteralExpr || value instanceof CharLiteralExpr) return "int";
        if (value instanceof LongLiteralExpr) return "long";
        if (value instanceof DoubleLiteralExpr literal) {
            return literal.getValue().toLowerCase(java.util.Locale.ROOT).endsWith("f") ? "float" : "double";
        }
        if (value instanceof BooleanLiteralExpr) return "boolean";
        if (value instanceof CastExpr cast) return cast.getTypeAsString();
        if (value instanceof UnaryExpr unary && (unary.getOperator() == UnaryExpr.Operator.MINUS
                || unary.getOperator() == UnaryExpr.Operator.PLUS)) return type(unary.getExpression());
        if (value instanceof FieldAccessExpr field && field.getNameAsString().equals("length")
                && field.getScope() instanceof NameExpr name && declaredType(name).filter(Type::isArrayType).isPresent()) return "int";
        if (value instanceof BinaryExpr binary && integral(binary.getLeft()) && integral(binary.getRight())) {
            return switch (binary.getOperator()) {
                case PLUS, MINUS, MULTIPLY, DIVIDE, REMAINDER, BINARY_AND, BINARY_OR, XOR, LEFT_SHIFT,
                     SIGNED_RIGHT_SHIFT, UNSIGNED_RIGHT_SHIFT -> type(binary.getLeft()).equals("long")
                        || type(binary.getRight()).equals("long") ? "long" : "int";
                default -> "";
            };
        }
        return "";
    }

    boolean integral(Expression expression) {
        return List.of("byte", "short", "char", "int", "long").contains(type(expression));
    }

    boolean string(Expression expression) {
        String type = type(expression);
        if (type.equals("java.lang.String")) return true;
        if (!type.equals("String")) return false;
        if (unit.findAll(TypeParameter.class).stream().anyMatch(t -> t.getNameAsString().equals("String"))) return false;
        return unit.findAll(TypeDeclaration.class).stream().noneMatch(t -> t.getNameAsString().equals("String"))
                && unit.getImports().stream().noneMatch(i -> !i.isAsterisk()
                && i.getName().getIdentifier().equals("String") && !i.getNameAsString().equals("java.lang.String"))
                && unit.getImports().stream().noneMatch(i -> i.isAsterisk() && !i.isStatic() && !i.getNameAsString().equals("java.lang"));
    }

    Optional<FieldAccessExpr> arrayLength(Expression expression) {
        expression = unwrapped(expression);
        if (expression instanceof FieldAccessExpr field && field.getNameAsString().equals("length")
                && field.getScope() instanceof NameExpr name && declaredType(name).filter(Type::isArrayType).isPresent()) {
            return Optional.of(field);
        }
        return Optional.empty();
    }
}

