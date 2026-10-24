package blueprints.diag;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;

import java.util.*;

/** R3: недостижимость в замкнутом графе вызовов простого класса, без угадывания типов вызовов. */
final class DeadPrivateMethodRule {
    private static final Set<String> REFLECTION = Set.of("forName", "getClass", "getMethod", "getMethods", "getDeclaredMethod",
            "getDeclaredMethods", "invoke", "findStatic", "findVirtual", "findSpecial", "unreflect", "lookup", "privateLookupIn");
    private static final Set<String> CALLBACKS = Set.of("readObject", "writeObject", "readObjectNoData", "readResolve", "writeReplace", "finalize");

    List<Finding> find(DiagnosticContext context) {
        if (!closedSimpleClass(context)) return List.of();
        ClassOrInterfaceDeclaration owner = (ClassOrInterfaceDeclaration) context.unit.getType(0);
        List<MethodDeclaration> methods = owner.getMethods();
        Map<String, List<MethodDeclaration>> byName = new HashMap<>();
        methods.forEach(m -> byName.computeIfAbsent(m.getNameAsString(), key -> new ArrayList<>()).add(m));
        Set<MethodDeclaration> live = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<MethodDeclaration> queue = new ArrayDeque<>();
        for (MethodDeclaration method : methods) {
            if (!method.isPrivate() || byName.get(method.getNameAsString()).size() > 1
                    || CALLBACKS.contains(method.getNameAsString()) || method.isNative()) add(method, live, queue);
        }
        // Ссылка на метод сохраняет цель даже без немедленного вызова.
        context.unit.findAll(MethodReferenceExpr.class).forEach(ref ->
                byName.getOrDefault(ref.getIdentifier(), List.of()).forEach(m -> add(m, live, queue)));
        for (BodyDeclaration<?> member : owner.getMembers()) {
            if (!(member instanceof MethodDeclaration)) callsFrom(member, byName, live, queue);
        }
        // Для вызова через объект неизвестного типа сохраняем все одноимённые методы.
        for (MethodCallExpr call : context.unit.findAll(MethodCallExpr.class)) {
            if (!localCall(call, owner.getNameAsString())) {
                byName.getOrDefault(call.getNameAsString(), List.of()).forEach(m -> add(m, live, queue));
            }
        }
        while (!queue.isEmpty()) callsFrom(queue.removeFirst(), byName, live, queue);
        List<MethodDeclaration> dead = methods.stream().filter(m -> m.isPrivate() && !live.contains(m))
                .filter(m -> context.methodId(m).isPresent()).toList();
        if (dead.isEmpty()) return List.of();
        Optional<String> patched = removeGroup(context.source, dead);
        List<Finding> findings = new ArrayList<>();
        for (MethodDeclaration method : dead) {
            String id = "R3-" + (findings.size() + 1);
            List<Patch> patches = patched.map(source -> List.of(new Patch(id + "-remove", Patch.REFACTOR,
                    "Удалить недостижимую группу private-методов",
                    "Удаляются все " + dead.size() + " недостижимых метода, включая их взаимные вызовы. "
                            + "Статическое основание ограничено этим файлом и отсутствием рефлексии. Динамическая проверка может остаться неполной.",
                    source, new int[]{DiagnosticContext.lines(dead.getFirst())[0], DiagnosticContext.lines(dead.getLast())[1]}))).orElse(List.of());
            findings.add(context.finding("R3", method, "Недостижимый private-метод",
                    "К «" + method.getNameAsString() + "» нет пути от открытых методов, конструкторов и инициализаторов этого класса. "
                            + "Перегрузки, ссылки на методы и вызовы через неизвестный объект консервативно считаются достижимыми. "
                            + "Вывод действует для статических вызовов в этом файле; внешняя рефлексия не анализируется.",
                    List.of("Класс используется без внешней рефлексии, JNI и инструментирования байткода", "Анализ ограничен одним файлом"),
                    patches, Finding.DEAD_CODE, findings.size() + 1));
        }
        return findings;
    }

    private boolean closedSimpleClass(DiagnosticContext context) {
        if (context.unit.getTypes().size() != 1 || !(context.unit.getType(0) instanceof ClassOrInterfaceDeclaration owner)
                || owner.isInterface() || !owner.getExtendedTypes().isEmpty() || !owner.getImplementedTypes().isEmpty()
                || context.unit.findAll(TypeDeclaration.class).size() != 1) return false;
        if (!context.unit.findAll(AnnotationExpr.class).isEmpty() || !context.unit.findAll(ClassExpr.class).isEmpty()
                || context.unit.findAll(ObjectCreationExpr.class).stream().anyMatch(c -> c.getAnonymousClassBody().isPresent())) return false;
        if (context.unit.getImports().stream().anyMatch(i -> i.getNameAsString().startsWith("java.lang.reflect")
                || i.getNameAsString().startsWith("java.lang.invoke"))) return false;
        return context.unit.findAll(MethodCallExpr.class).stream().noneMatch(c -> REFLECTION.contains(c.getNameAsString()));
    }

    private void callsFrom(Node source, Map<String, List<MethodDeclaration>> byName,
                           Set<MethodDeclaration> live, Deque<MethodDeclaration> queue) {
        for (MethodCallExpr call : source.findAll(MethodCallExpr.class)) {
            // Арность не разрешаем: сохранение лишней перегрузки безопаснее удаления вызываемого кода.
            byName.getOrDefault(call.getNameAsString(), List.of()).forEach(m -> add(m, live, queue));
        }
    }

    private boolean localCall(MethodCallExpr call, String owner) {
        return call.getScope().isEmpty() || call.getScope().get().isThisExpr()
                || call.getScope().get().toString().equals(owner);
    }

    private void add(MethodDeclaration method, Set<MethodDeclaration> live, Deque<MethodDeclaration> queue) {
        if (live.add(method)) queue.addLast(method);
    }

    private Optional<String> removeGroup(SourceText source, List<MethodDeclaration> dead) {
        if (dead.stream().anyMatch(m -> source.textOf(m).isEmpty())) return Optional.empty();
        List<MethodDeclaration> reverse = new ArrayList<>(dead);
        reverse.sort(Comparator.comparingInt((MethodDeclaration m) -> DiagnosticContext.lines(m)[0])
                .thenComparingInt(m -> m.getBegin().orElseThrow().column).reversed());
        String result = source.text();
        for (MethodDeclaration method : reverse) {
            // Номера строк могут наблюдаться через stack trace: сохраняем строки и столбцы файла.
            String removed = source.textOf(method).orElseThrow();
            String whitespace = removed.replaceAll("[^\\r\\n\\t]", " ");
            result = new SourceText(result).replace(method, whitespace);
        }
        return Optional.of(result);
    }
}


