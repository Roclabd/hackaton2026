package blueprints.graph;

import blueprints.parse.SourceParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProjectMapBuilderTest {

    private static final Path MINILANG = Path.of("../corpus/ours/MiniLang/MiniLang.java");

    private final ProjectMapBuilder builder = new ProjectMapBuilder();

    @Test
    void miniLangGivesClassesMethodsAndCalls() throws Exception {
        Graph map = builder.build(SourceParser.parseOrThrow(Files.readString(MINILANG, StandardCharsets.UTF_8)));

        assertTrue(count(map, NodeKind.CLASS) >= 30, "в MiniLang больше 30 классов");
        assertTrue(count(map, NodeKind.METHOD) >= 50, "и больше 50 методов");
        assertNotNull(find(map, "M:MiniLang#main(String[])"), "main найден по стабильному id");
        assertNotNull(find(map, "C:MiniLang.Lexer"), "вложенный класс — с полным именем");
        assertFalse(map.edges().isEmpty(), "есть рёбра вызовов");
        assertTrue(map.edges().stream().anyMatch(e -> e.from().equals("M:MiniLang#main(String[])")
                && e.to().startsWith("M:MiniLang#run(")), "main вызывает run");
    }

    @Test
    void idsAreUnique() throws Exception {
        Graph map = builder.build(SourceParser.parseOrThrow(Files.readString(MINILANG, StandardCharsets.UTF_8)));
        Set<String> seen = new HashSet<>();
        for (GraphNode node : map.nodes()) {
            assertTrue(seen.add(node.id()), "повтор id: " + node.id());
        }
    }

    @Test
    void insertingCodeAboveDoesNotChangeIds() {
        String before = """
                class Shop {
                    int total;
                    void add(int x) { total += x; }
                    int sum() { return total; }
                }
                """;
        String after = """
                class Shop {
                    static final String NAME = "магазин";
                    void hello() { System.out.println(NAME); }
                    int total;
                    void add(int x) { total += x; }
                    int sum() { return total; }
                }
                """;
        Set<String> idsBefore = ids(builder.build(SourceParser.parseOrThrow(before)));
        Set<String> idsAfter = ids(builder.build(SourceParser.parseOrThrow(after)));
        assertTrue(idsAfter.containsAll(idsBefore), "старые id сохранились: " + idsBefore + " → " + idsAfter);
    }

    @Test
    void callOnVariableWithCommonJdkNameIsNotLinked() {
        String source = """
                import java.util.*;
                class Box {
                    List<Integer> items = new ArrayList<>();
                    void add(int x) { items.add(x); }
                }
                """;
        Graph map = builder.build(SourceParser.parseOrThrow(source));
        assertTrue(map.edges().isEmpty(), "items.add — это метод List, а не Box.add: " + map.edges());
    }

    // ---- Регрессии аудита v2 (P2): рёбра вызовов, примеры взяты из отчёта как есть ----

    @Test
    void superCallGoesToParentNotOverride() {
        Graph map = build("class A { void f(){} } class B extends A { void f(){} void run(){super.f();} }");
        assertEquals(Set.of("M:B#run()->M:A#f()|вызов"), edges(map, "M:B#run()"));
    }

    @Test
    void unqualifiedCallFindsInheritedMethod() {
        Graph map = build("class A { void helper(){} } class B extends A { void run(){ helper(); } }");
        assertEquals(Set.of("M:B#run()->M:A#helper()|вызов"), edges(map, "M:B#run()"));
    }

    @Test
    void sameArityOverloadsAreGuessesNotExactCalls() {
        Graph map = build("class A { void f(int x){} void f(String x){} void run(){f(1);} }");
        assertEquals(Set.of("M:A#run()->M:A#f(int)|вызов?", "M:A#run()->M:A#f(String)|вызов?"), edges(map, "M:A#run()"));
    }

    @Test
    void callsInsideLocalClassAreNotAttributedToEnclosingMethod() {
        Graph map = build("class A { void target(){} void make(){class L {void later(){target();}}} }");
        assertEquals(Set.of(), edges(map, "M:A#make()"));
    }

    @Test
    void callsInsideAnonymousClassAreNotAttributedToEnclosingMethod() {
        Graph map = build("class A { void target(){} void make(){ Runnable r = new Runnable(){ public void run(){ target(); } }; } }");
        assertEquals(Set.of(), edges(map, "M:A#make()"));
    }

    @Test
    void callsInsideLambdaBelongToEnclosingMethod() {
        Graph map = build("class A { void target(){} void make(){ Runnable r = () -> target(); } }");
        assertEquals(Set.of("M:A#make()->M:A#target()|вызов"), edges(map, "M:A#make()"));
    }

    @Test
    void staticInitializerCallsAreOnTheMap() {
        Graph map = build("class A { static {target();} static void target(){} }");
        assertEquals(Set.of("M:A#<clinit>()->M:A#target()|вызов"), edges(map, "M:A#<clinit>()"));
    }

    // ---- Регрессии аудита v3: ложная точность рёбер, примеры из отчёта ----

    @Test
    void g1QualifiedParentIsResolvedLexically() {
        Graph map = build("""
                class Base { void hit(){} }
                class Nest {
                  static class Base { void hit(){} }
                  static class Child extends Nest.Base { void test(){super.hit();} }
                }
                """);
        assertEquals(Set.of("M:Nest.Child#test()->M:Nest.Base#hit()|вызов"), edges(map, "M:Nest.Child#test()"));
    }

    @Test
    void g1QualifiedConstructorGoesToNestedClass() {
        Graph map = build("class Item { Item(){} } class Nest { static class Item { Item(){} } void make(){ new Nest.Item(); } }");
        assertEquals(Set.of("M:Nest#make()->M:Nest.Item#<init>()|вызов"), edges(map, "M:Nest#make()"));
    }

    @Test
    void g2QualifiedThisGoesToOuterClass() {
        Graph map = build("class Home { void hit(){} class Room { void hit(){} void test(){Home.this.hit();} } }");
        assertEquals(Set.of("M:Home.Room#test()->M:Home#hit()|вызов"), edges(map, "M:Home.Room#test()"));
    }

    @Test
    void g3InheritedOverloadIsNotDropped() {
        Graph map = build("""
                class Parent { void choose(String s){} }
                class Child extends Parent { void choose(int n){} void test(){choose("hello");} }
                """);
        assertEquals(Set.of("M:Child#test()->M:Child#choose(int)|вызов?", "M:Child#test()->M:Parent#choose(String)|вызов?"),
                edges(map, "M:Child#test()"));
    }

    @Test
    void g3OverrideHidesParentWithSameSignature() {
        Graph map = build("class Parent { void f(){} } class Child extends Parent { void f(){} void test(){ f(); } }");
        assertEquals(Set.of("M:Child#test()->M:Child#f()|вызов"), edges(map, "M:Child#test()"));
    }

    @Test
    void g4InterfaceDefaultIsFoundBeforeOuterClass() {
        Graph map = build("""
                interface Defaults { default void hit(){} }
                class Home { void hit(){} class Room implements Defaults { void test(){hit();} } }
                """);
        assertEquals(Set.of("M:Home.Room#test()->M:Defaults#hit()|вызов"), edges(map, "M:Home.Room#test()"));
    }

    @Test
    void g4UnknownParentStopsFallbackToOuterClass() {
        Graph map = build("""
                class Home {
                  String getMessage(){ return "outer"; }
                  static class Failure extends Exception { Failure(){ super("parent"); } String text(){ return getMessage(); } }
                }
                """);
        assertEquals(Set.of(), edges(map, "M:Home.Failure#text()"), "getMessage — у Throwable, а не у Home");
    }

    @Test
    void g5VariableShadowsClassName() {
        Graph map = build("class Target { void hit(){} } class Actual { void hit(){} } class Use { void test(Actual Target){Target.hit();} }");
        assertEquals(Set.of("M:Use#test(Actual)->M:Actual#hit()|вызов"), edges(map, "M:Use#test(Actual)"));
    }

    @Test
    void m1CallsOnProjectTypedVariablesAreFoundDespiteCommonNames() {
        Graph map = build("""
                import java.util.*;
                class Env { Object get(String k){ return null; } }
                class Interp { Env env = new Env(); List<String> names = new ArrayList<>();
                  Object look(String k){ names.add(k); return env.get(k); } }
                """);
        assertEquals(Set.of("M:Interp#look(String)->M:Env#get(String)|вызов"), edges(map, "M:Interp#look(String)"),
                "env.get найден по типу поля, names.add (List из JDK) — нет");
    }

    private Graph build(String source) {
        return builder.build(SourceParser.parseOrThrow(source));
    }

    /** Исходящие рёбра узла в виде «откуда->куда|подпись» — так ошибка в тесте читается сразу. */
    private static Set<String> edges(Graph map, String fromId) {
        Set<String> result = new HashSet<>();
        map.edges().stream().filter(e -> e.from().equals(fromId))
                .forEach(e -> result.add(e.from() + "->" + e.to() + "|" + e.label()));
        return result;
    }

    private static long count(Graph map, NodeKind kind) {
        return map.nodes().stream().filter(n -> n.kind() == kind).count();
    }

    private static GraphNode find(Graph map, String id) {
        return map.nodes().stream().filter(n -> n.id().equals(id)).findFirst().orElse(null);
    }

    private static Set<String> ids(Graph map) {
        return new HashSet<>(map.nodes().stream().map(GraphNode::id).toList());
    }
}
