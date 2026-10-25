package blueprints.graph;

import blueprints.parse.SourceText;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Строит чертёж метода (уровень 1): операторы сверху вниз, контейнеры if/loop/switch/try с ветками.
 *
 * id узла = id родителя + ветка + вид + хэш нормализованного текста (у контейнера — только заголовка)
 * + номер повтора среди соседей. Позиции в id не входят: вставка оператора выше не меняет id остальных,
 * а правка тела цикла не меняет id самого цикла.
 *
 * Выражения не становятся узлами графа: узел показывает их текстом, а подробное дерево лежит в поле expr
 * ({@link ExprTreeBuilder}) и рисуется внутри узла — иначе 1000 строк дали бы десятки тысяч узлов.
 */
public final class MethodBlueprintBuilder {

    private static final int MAX_LABEL = 90;

    public Blueprint build(String methodId, String label, Node declaration, SourceText source) {
        Builder builder = new Builder(source,
                declaration.findCompilationUnit().map(ComplexityEstimator::new).orElse(null));
        bodyOf(declaration).ifPresent(body -> builder.addAll(body.getStatements(), methodId, "body"));
        return new Blueprint(methodId, label, new Graph(List.copyOf(builder.nodes), List.copyOf(builder.edges)),
                Collections.unmodifiableMap(builder.astById));
    }

    private static Optional<BlockStmt> bodyOf(Node declaration) {
        return switch (declaration) {
            case MethodDeclaration m -> m.getBody();
            case ConstructorDeclaration c -> Optional.of(c.getBody());
            case CompactConstructorDeclaration c -> Optional.of(c.getBody());
            case InitializerDeclaration i -> Optional.of(i.getBody());
            default -> Optional.empty();
        };
    }

    /** Обход операторов с накоплением узлов, рёбер порядка и связи id → дерево. */
    private static final class Builder {
        final List<GraphNode> nodes = new ArrayList<>();
        final List<GraphEdge> edges = new ArrayList<>();
        final Map<String, Node> astById = new LinkedHashMap<>();
        private final SourceText source;
        private final ExprTreeBuilder exprs;
        private final EmbeddedStatementBlocks embedded;
        /** Оценка сложности циклов (фрагментов); null — если у объявления нет файла. */
        private final ComplexityEstimator complexity;

        Builder(SourceText source, ComplexityEstimator complexity) {
            this.source = source;
            this.exprs = new ExprTreeBuilder(source);
            this.embedded = new EmbeddedStatementBlocks(source);
            this.complexity = complexity;
        }

        /** Операторы одной ветки: узлы по порядку, между соседями — ребро exec. */
        void addAll(List<Statement> statements, String parentId, String branch) {
            Map<String, Integer> seen = new HashMap<>();
            String previous = null;
            for (Statement statement : flatten(statements)) {
                String id = add(statement, parentId, branch, seen);
                if (id == null) {
                    continue;
                }
                if (previous != null) {
                    edges.add(new GraphEdge(previous + "->" + id, previous, id, EdgeKind.EXEC, null));
                }
                previous = id;
            }
        }

        /** Вложенные { } без заголовка не рисуем отдельной рамкой — их операторы идут в ту же ветку. */
        private static List<Statement> flatten(List<Statement> statements) {
            List<Statement> result = new ArrayList<>();
            for (Statement statement : statements) {
                if (statement instanceof BlockStmt block) {
                    result.addAll(flatten(block.getStatements()));
                } else if (!(statement instanceof EmptyStmt)) {
                    result.add(statement);
                }
            }
            return result;
        }

        private String add(Statement statement, String parentId, String branch, Map<String, Integer> seen) {
            return switch (statement) {
                case IfStmt s -> container(s, NodeKind.IF, "if (" + text(s.getCondition()) + ")", s.getCondition(), true,
                        parentId, branch, seen, branches(s));
                case WhileStmt s -> container(s, NodeKind.LOOP, "while (" + text(s.getCondition()) + ")", s.getCondition(), true,
                        parentId, branch, seen, List.of(new Child("body", "тело", List.of(s.getBody()))));
                case DoStmt s -> container(s, NodeKind.LOOP, "do … while (" + text(s.getCondition()) + ")", s.getCondition(), true,
                        parentId, branch, seen, List.of(new Child("body", "тело", List.of(s.getBody()))));
                case ForEachStmt s -> container(s, NodeKind.LOOP,
                        "for (" + text(s.getVariable()) + " : " + text(s.getIterable()) + ")", s, false,
                        parentId, branch, seen, List.of(new Child("body", "тело", List.of(s.getBody()))));
                case ForStmt s -> container(s, NodeKind.LOOP, forHeader(s), s, false,
                        parentId, branch, seen, List.of(new Child("body", "тело", List.of(s.getBody()))));
                case SwitchStmt s -> container(s, NodeKind.SWITCH, "switch (" + text(s.getSelector()) + ")", s, false,
                        parentId, branch, seen, cases(s));
                case TryStmt s -> container(s, NodeKind.TRY, tryHeader(s), s, false, parentId, branch, seen, tryParts(s));
                case LabeledStmt s -> add(s.getStatement(), parentId, branch, seen);
                case ReturnStmt s -> leaf(s, NodeKind.RETURN, true, parentId, branch, seen);
                case ThrowStmt s -> leaf(s, NodeKind.THROW, true, parentId, branch, seen);
                case YieldStmt s -> leaf(s, NodeKind.STMT, true, parentId, branch, seen);
                case BreakStmt s -> leaf(s, NodeKind.BREAK, false, parentId, branch, seen);
                case ContinueStmt s -> leaf(s, NodeKind.CONTINUE, false, parentId, branch, seen);
                case ExpressionStmt s -> leaf(s, NodeKind.STMT, true, parentId, branch, seen);
                case AssertStmt s -> leaf(s, NodeKind.STMT, true, parentId, branch, seen);
                case ExplicitConstructorInvocationStmt s -> leaf(s, NodeKind.STMT, true, parentId, branch, seen);
                default -> leaf(statement, NodeKind.RAW, false, parentId, branch, seen);
            };
        }

        /** Ветка контейнера: ключ, подпись и операторы внутри. */
        private record Child(String key, String label, List<Statement> statements) {}

        private String container(Statement statement, NodeKind kind, String header, Node editTarget, boolean editable,
                                 String parentId, String branch, Map<String, Integer> seen, List<Child> children) {
            String id = nextId(parentId, branch, kind, header, seen);
            String code = editable ? text(editTarget) : null;
            children = withEmbedded(statement, children);
            List<GraphBranch> branchList = children.stream().map(c -> new GraphBranch(c.key(), c.label())).toList();
            nodes.add(new GraphNode(id, kind, shorten(header), code, ProjectMapBuilder.lines(statement),
                    parentId, branch, List.of(), editable, branchList, exprs.forStatement(statement, id),
                    kind == NodeKind.LOOP && complexity != null ? complexity.ofLoop(statement) : null));
            astById.put(id, editTarget);
            for (Child child : children) {
                addAll(child.statements(), id, child.key());
            }
            return id;
        }

        private String leaf(Statement statement, NodeKind kind, boolean editable,
                            String parentId, String branch, Map<String, Integer> seen) {
            String code = text(statement);
            String id = nextId(parentId, branch, kind, code, seen);
            List<Child> children = withEmbedded(statement, List.of());
            List<GraphBranch> branches = children.isEmpty() ? null
                    : children.stream().map(c -> new GraphBranch(c.key(), c.label())).toList();
            nodes.add(new GraphNode(id, kind, shorten(code), code, ProjectMapBuilder.lines(statement),
                    parentId, branch, List.of(), editable, branches, exprs.forStatement(statement, id)));
            astById.put(id, statement);
            for (Child child : children) {
                addAll(child.statements(), id, child.key());
            }
            return id;
        }

        private List<Child> withEmbedded(Statement statement, List<Child> ordinary) {
            List<Child> children = new ArrayList<>(ordinary);
            embedded.of(statement).forEach(b -> children.add(new Child(b.key(), b.label(), b.statements())));
            return children;
        }

        private String nextId(String parentId, String branch, NodeKind kind, String text, Map<String, Integer> seen) {
            String base = parentId + "/" + branch + "." + kind.wire() + ":" + Integer.toHexString(normalize(text).hashCode());
            int repeat = seen.merge(base, 1, Integer::sum);
            return repeat == 1 ? base : base + "~" + repeat;
        }

        private List<Child> branches(IfStmt s) {
            List<Child> result = new ArrayList<>();
            result.add(new Child("then", "да", List.of(s.getThenStmt())));
            s.getElseStmt().ifPresent(e -> result.add(new Child("else", "нет", List.of(e))));
            return result;
        }

        private List<Child> cases(SwitchStmt s) {
            List<Child> result = new ArrayList<>();
            NodeList<SwitchEntry> entries = s.getEntries();
            for (int i = 0; i < entries.size(); i++) {
                SwitchEntry entry = entries.get(i);
                String arrow = entry.getType() == SwitchEntry.Type.STATEMENT_GROUP ? ":" : " ->";
                String label = entry.getLabels().isEmpty() ? "default" + arrow
                        : "case " + entry.getLabels().stream().map(this::text).collect(Collectors.joining(", ")) + arrow;
                result.add(new Child("case:" + i, label, entry.getStatements()));
            }
            return result;
        }

        private List<Child> tryParts(TryStmt s) {
            List<Child> result = new ArrayList<>();
            result.add(new Child("body", "try", List.of(s.getTryBlock())));
            for (int i = 0; i < s.getCatchClauses().size(); i++) {
                var clause = s.getCatchClauses().get(i);
                result.add(new Child("catch:" + i, "catch (" + text(clause.getParameter()) + ")", List.of(clause.getBody())));
            }
            s.getFinallyBlock().ifPresent(f -> result.add(new Child("finally", "finally", List.of(f))));
            return result;
        }

        private String forHeader(ForStmt s) {
            String init = s.getInitialization().stream().map(this::text).collect(Collectors.joining(", "));
            String compare = s.getCompare().map(this::text).orElse("");
            String update = s.getUpdate().stream().map(this::text).collect(Collectors.joining(", "));
            return "for (" + init + "; " + compare + "; " + update + ")";
        }

        private String tryHeader(TryStmt s) {
            if (s.getResources().isEmpty()) {
                return "try";
            }
            return "try (" + s.getResources().stream().map(this::text).collect(Collectors.joining("; ")) + ")";
        }

        /** Текст узла ровно как в файле (а не как его напечатал бы JavaParser). */
        private String text(Node node) {
            return node.getRange().map(source::slice).orElseGet(node::toString);
        }

        private static String normalize(String text) {
            return text.replaceAll("\\s+", " ").strip();
        }

        private static String shorten(String text) {
            String oneLine = normalize(text);
            return oneLine.length() <= MAX_LABEL ? oneLine : oneLine.substring(0, MAX_LABEL - 1) + "…";
        }
    }
}
