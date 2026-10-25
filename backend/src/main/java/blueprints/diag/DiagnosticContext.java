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
            if (locals.isEmpty() && parameters.isEmpty()) {
                return enclosingType(name).flatMap(type -> fieldType(type, name.getNameAsString()));
            }
        }
        return Optional.empty();
    }

    /**
     * Поле, объявленное в самом классе, где стоит имя. По правилам Java собственное поле класса скрывает и поля
     * внешнего класса, и унаследованные, поэтому здесь нет угадывания. Поля предков и внешних классов не ищем:
     * там без разрешения имён легко ошибиться.
     */
    private static Optional<TypeDeclaration<?>> enclosingType(Node node) {
        for (Node parent = node.getParentNode().orElse(null); parent != null; parent = parent.getParentNode().orElse(null)) {
            if (parent instanceof TypeDeclaration<?> type) return Optional.of(type);
            if (parent instanceof com.github.javaparser.ast.expr.ObjectCreationExpr) return Optional.empty(); // анонимный класс
        }
        return Optional.empty();
    }

    /** Тип поля {@code name}, объявленного прямо в {@code type}; при нескольких объявлениях — пусто. */
    private static Optional<Type> fieldType(TypeDeclaration<?> type, String name) {
        List<VariableDeclarator> fields = type.getFields().stream()
                .flatMap(f -> f.getVariables().stream())
                .filter(v -> v.getNameAsString().equals(name)).toList();
        return fields.size() == 1 ? Optional.of(fields.getFirst().getType()) : Optional.empty();
    }

    /**
     * {@code x.поле} или {@code this.поле}: тип x — класс из этого же файла (ровно один с таким именем, без
     * параметров типа), поле объявлено прямо в нём. Иначе — пусто: наследование и дженерики не разбираем.
     */
    private Optional<Type> fieldAccessType(FieldAccessExpr access) {
        Expression scope = unwrapped(access.getScope());
        if (scope instanceof ThisExpr thisExpr && thisExpr.getTypeName().isEmpty()) {
            return enclosingType(access).flatMap(type -> fieldType(type, access.getNameAsString()));
        }
        if (!(scope instanceof NameExpr owner)) return Optional.empty();
        Optional<Type> ownerType = declaredType(owner).filter(Type::isClassOrInterfaceType);
        if (ownerType.isEmpty() || ownerType.get().asClassOrInterfaceType().getTypeArguments().isPresent()) {
            return Optional.empty();
        }
        String className = ownerType.get().asClassOrInterfaceType().getNameAsString();
        List<TypeDeclaration> classes = unit.findAll(TypeDeclaration.class).stream()
                .filter(t -> t.getNameAsString().equals(className)).toList();
        if (classes.size() != 1) return Optional.empty();
        return fieldType(classes.getFirst(), access.getNameAsString());
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
        if (value instanceof FieldAccessExpr field) return fieldAccessType(field).map(Type::asString).orElse("");
        if (value instanceof MethodCallExpr call && call.getArguments().isEmpty()
                && call.getScope().map(DiagnosticContext::unwrapped).orElse(null) instanceof NameExpr scope) {
            // list.size() и s.length() — int, если переменная объявлена стандартной коллекцией или строкой.
            if (call.getNameAsString().equals("size") && collection(scope)) return "int";
            if (call.getNameAsString().equals("length") && string(scope)) return "int";
        }
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

    private static final List<String> COLLECTIONS = List.of("Collection", "List", "ArrayList", "LinkedList", "Set",
            "HashSet", "LinkedHashSet", "TreeSet", "Map", "HashMap", "LinkedHashMap", "TreeMap", "Queue", "Deque",
            "ArrayDeque", "PriorityQueue");

    /** Объявлена стандартной коллекцией java.util, и в файле нет своего типа с тем же именем. */
    private boolean collection(NameExpr name) {
        return standardType(name, COLLECTIONS).isPresent();
    }

    /**
     * Объявленный тип переменной, если это один из стандартных типов {@code names} (List, Map…) и в файле нет
     * своего типа с тем же простым именем — иначе пусто.
     */
    Optional<com.github.javaparser.ast.type.ClassOrInterfaceType> standardType(NameExpr name, java.util.Collection<String> names) {
        Optional<com.github.javaparser.ast.type.ClassOrInterfaceType> type = declaredType(name)
                .filter(Type::isClassOrInterfaceType).map(Type::asClassOrInterfaceType)
                .filter(t -> names.contains(t.getNameAsString()));
        return type.filter(t -> unit.findAll(TypeDeclaration.class).stream()
                .noneMatch(own -> own.getNameAsString().equals(t.getNameAsString())));
    }

    /**
     * Цикл ввода: {@code while (true)} или цикл, который на каждом шаге читает ввод. Его итерации — команды
     * пользователя, а не элементы данных: одна сортировка на команду — не рост n².
     */
    static boolean inputLoop(com.github.javaparser.ast.stmt.Statement loop) {
        Optional<Expression> condition = loop instanceof com.github.javaparser.ast.stmt.WhileStmt w ? Optional.of(w.getCondition())
                : loop instanceof com.github.javaparser.ast.stmt.DoStmt d ? Optional.of(d.getCondition())
                : loop instanceof ForStmt f ? f.getCompare() : Optional.empty();
        if (!(loop instanceof ForEachStmt) && (condition.isEmpty() || condition.get() instanceof BooleanLiteralExpr b && b.getValue())) {
            return true;
        }
        return loop.findAll(MethodCallExpr.class).stream().anyMatch(call -> call.getNameAsString().matches(
                "next|nextLine|nextInt|nextLong|nextDouble|readLine|hasNext|hasNextLine|hasNextInt"));
    }

    /** Ближайший цикл, внутри которого стоит узел, — в пределах того же метода (лямбды и классы — граница). */
    static Optional<com.github.javaparser.ast.stmt.Statement> enclosingLoop(Node node) {
        for (Node parent = node.getParentNode().orElse(null); parent != null; parent = parent.getParentNode().orElse(null)) {
            if (parent instanceof ForStmt || parent instanceof ForEachStmt
                    || parent instanceof com.github.javaparser.ast.stmt.WhileStmt || parent instanceof com.github.javaparser.ast.stmt.DoStmt) {
                return Optional.of((com.github.javaparser.ast.stmt.Statement) parent);
            }
            if (parent instanceof CallableDeclaration<?> || parent instanceof LambdaExpr || parent instanceof TypeDeclaration<?>) {
                return Optional.empty();
            }
        }
        return Optional.empty();
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

