package blueprints.graph;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;

import java.util.*;

/**
 * Строит схему уровня 0 («Карта»): классы, поля, методы и рёбра «вызывает».
 *
 * Обходит только объявленные типы и их вложенные типы. Локальные и анонимные классы
 * внутри методов на карту не выносятся — они появятся на чертеже метода (уровень 1).
 */
public final class ProjectMapBuilder {

    public Graph build(CompilationUnit unit) {
        Collector collector = new Collector();
        for (TypeDeclaration<?> type : unit.getTypes()) {
            collector.addType(type, null, type.getNameAsString());
        }
        List<GraphEdge> calls = new CallResolver(collector.methods, collector.types).resolve();
        return new Graph(List.copyOf(collector.nodes), calls);
    }

    /**
     * id метода на карте → его объявление в дереве (метод, конструктор или блок инициализации).
     * Нужен чертежу метода и командам правки: по id с карты найти, что именно рисовать и менять.
     */
    public Map<String, Node> declarations(CompilationUnit unit) {
        Collector collector = new Collector();
        for (TypeDeclaration<?> type : unit.getTypes()) {
            collector.addType(type, null, type.getNameAsString());
        }
        return Collections.unmodifiableMap(collector.declarations);
    }

    /** Собирает узлы при обходе дерева и параллельно запоминает методы для поиска вызовов. */
    private static final class Collector {
        final List<GraphNode> nodes = new ArrayList<>();
        final List<CallResolver.MethodInfo> methods = new ArrayList<>();
        final Map<String, CallResolver.TypeInfo> types = new LinkedHashMap<>();
        final Map<String, Node> declarations = new LinkedHashMap<>();
        private final Set<String> usedIds = new HashSet<>();

        void addType(TypeDeclaration<?> type, String parentId, String typeName) {
            String id = unique(NodeIds.type(typeName));
            types.put(typeName, typeInfo(type, typeName));
            nodes.add(node(id, NodeKind.CLASS, typeKeyword(type) + " " + type.getNameAsString(), null, type, parentId));

            if (type instanceof RecordDeclaration record) {
                record.getParameters().forEach(p -> addRecordComponent(p, id, typeName));
            }
            for (BodyDeclaration<?> member : type.getMembers()) {
                switch (member) {
                    case TypeDeclaration<?> nested -> addType(nested, id, typeName + "." + nested.getNameAsString());
                    case FieldDeclaration field -> addFields(field, id, typeName);
                    case MethodDeclaration method -> addMethod(method, id, typeName);
                    case ConstructorDeclaration constructor -> addConstructor(constructor, id, typeName);
                    case InitializerDeclaration initializer -> addInitializer(initializer, id, typeName);
                    default -> { /* константы enum и прочее на карту не выносим */ }
                }
            }
        }

        private void addFields(FieldDeclaration field, String parentId, String typeName) {
            String prefix = field.isStatic() ? "static " : "";
            for (var variable : field.getVariables()) {
                String id = unique(NodeIds.field(typeName, variable.getNameAsString()));
                String label = prefix + variable.getTypeAsString() + " " + variable.getNameAsString();
                nodes.add(node(id, NodeKind.FIELD, label, field.toString().strip(), field, parentId));
            }
        }

        private void addRecordComponent(Parameter component, String parentId, String typeName) {
            String id = unique(NodeIds.field(typeName, component.getNameAsString()));
            String label = component.getTypeAsString() + " " + component.getNameAsString();
            nodes.add(node(id, NodeKind.FIELD, label, component.toString(), component, parentId));
        }

        private void addMethod(MethodDeclaration method, String parentId, String typeName) {
            List<String> parameterTypes = parameterTypes(method.getParameters());
            String id = unique(NodeIds.method(typeName, method.getNameAsString(), parameterTypes));
            String prefix = method.isStatic() ? "static " : "";
            String label = prefix + method.getTypeAsString() + " " + method.getNameAsString() + "(" + String.join(", ", parameterTypes) + ")";
            nodes.add(node(id, NodeKind.METHOD, label, null, method, parentId));
            declarations.put(id, method);
            methods.add(new CallResolver.MethodInfo(id, typeName, method.getNameAsString(), String.join(",", parameterTypes),
                    method.getParameters().size(), hasVarargs(method.getParameters()), method));
        }

        private void addConstructor(ConstructorDeclaration constructor, String parentId, String typeName) {
            List<String> parameterTypes = parameterTypes(constructor.getParameters());
            String id = unique(NodeIds.method(typeName, "<init>", parameterTypes));
            String label = "new " + constructor.getNameAsString() + "(" + String.join(", ", parameterTypes) + ")";
            nodes.add(node(id, NodeKind.METHOD, label, null, constructor, parentId));
            declarations.put(id, constructor);
            methods.add(new CallResolver.MethodInfo(id, typeName, "<init>", String.join(",", parameterTypes),
                    constructor.getParameters().size(), hasVarargs(constructor.getParameters()), constructor));
        }

        private void addInitializer(InitializerDeclaration initializer, String parentId, String typeName) {
            String id = unique(NodeIds.method(typeName, "<clinit>", List.of()));
            String label = initializer.isStatic() ? "static { … }" : "{ … }";
            nodes.add(node(id, NodeKind.METHOD, label, null, initializer, parentId));
            declarations.put(id, initializer);
            // Блок сам вызывать нельзя, но он вызывает методы — регистрируем как источник рёбер (аудит v2).
            methods.add(new CallResolver.MethodInfo(id, typeName, "<clinit>", "", 0, false, initializer));
        }

        /** Предки (как записаны в extends/implements, с квалификатором) и типы полей — для поиска целей вызовов. */
        private static CallResolver.TypeInfo typeInfo(TypeDeclaration<?> type, String typeName) {
            String superclass = null;
            List<String> interfaces = new ArrayList<>();
            boolean isInterface = false;
            switch (type) {
                case ClassOrInterfaceDeclaration c when c.isInterface() -> {
                    isInterface = true;
                    c.getExtendedTypes().forEach(t -> interfaces.add(t.getNameWithScope()));
                }
                case ClassOrInterfaceDeclaration c -> {
                    superclass = c.getExtendedTypes().isEmpty() ? null : c.getExtendedTypes(0).getNameWithScope();
                    c.getImplementedTypes().forEach(t -> interfaces.add(t.getNameWithScope()));
                }
                case EnumDeclaration e -> e.getImplementedTypes().forEach(t -> interfaces.add(t.getNameWithScope()));
                case RecordDeclaration r -> r.getImplementedTypes().forEach(t -> interfaces.add(t.getNameWithScope()));
                default -> { }
            }
            Map<String, String> fieldTypes = new HashMap<>();
            for (FieldDeclaration field : type.getFields()) {
                field.getVariables().forEach(v -> fieldTypes.put(v.getNameAsString(), CallResolver.typeNameOf(v.getType())));
            }
            if (type instanceof RecordDeclaration record) {
                record.getParameters().forEach(p -> fieldTypes.put(p.getNameAsString(), CallResolver.typeNameOf(p.getType())));
            }
            return new CallResolver.TypeInfo(typeName, superclass, List.copyOf(interfaces), isInterface, fieldTypes);
        }

        private String unique(String id) {
            String candidate = id;
            for (int repeat = 2; !usedIds.add(candidate); repeat++) {
                candidate = id + "~" + repeat;
            }
            return candidate;
        }

        private static GraphNode node(String id, NodeKind kind, String label, String code, Node source, String parentId) {
            return new GraphNode(id, kind, label, code, lines(source), parentId, null, List.of(), false, null);
        }
    }

    private static List<String> parameterTypes(List<Parameter> parameters) {
        return parameters.stream()
                .map(p -> NodeIds.normalizeType(p.getTypeAsString()) + (p.isVarArgs() ? "..." : ""))
                .toList();
    }

    private static boolean hasVarargs(List<Parameter> parameters) {
        return !parameters.isEmpty() && parameters.getLast().isVarArgs();
    }

    static int[] lines(Node node) {
        return node.getRange()
                .map(r -> new int[]{r.begin.line, r.end.line})
                .orElse(new int[]{0, 0});
    }

    private static String typeKeyword(TypeDeclaration<?> type) {
        return switch (type) {
            case ClassOrInterfaceDeclaration c when c.isInterface() -> "interface";
            case ClassOrInterfaceDeclaration c when c.hasModifier(Modifier.Keyword.ABSTRACT) -> "abstract class";
            case EnumDeclaration e -> "enum";
            case RecordDeclaration r -> "record";
            case AnnotationDeclaration a -> "@interface";
            default -> "class";
        };
    }
}
