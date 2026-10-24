import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Раздел 5 документа (расширенный уровень, бонус): пользовательские классы и интерфейсы, record, enum,
 * дженерики, лямбды и java.util.function, Stream API и Collectors.
 * Ввод — целые числа через пробел; если их нет, берутся числа по умолчанию.
 */
public class Scope5Bonus {

    // 5.Интерфейсы: интерфейс с абстрактным, default и static методами
    interface Shape {
        double area();

        String name();

        default String describe() {
            return name() + String.format("[%.2f]", area());
        }

        static Shape unit() {
            return new Square(1);
        }
    }

    // 5.Классы: абстрактный класс
    abstract static class Base implements Shape {
        private final String name;

        Base(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }

    // 5.Классы: наследование и переопределение
    static class Square extends Base {
        private final double side;

        Square(double side) {
            super("Square");
            this.side = side;
        }

        @Override
        public double area() {
            return side * side;
        }
    }

    static class Circle extends Base {
        private final double radius;

        Circle(double radius) {
            super("Circle");
            this.radius = radius;
        }

        @Override
        public double area() {
            return Math.PI * radius * radius;
        }

        @Override
        public String describe() {
            return "круг " + super.describe();
        }
    }

    // 5.Классы: внутренний (не статический) класс
    class Counter {
        int value;

        void bump() {
            value += step;
        }
    }

    int step = 2;

    // 5.record: компактный конструктор, методы, static-фабрика
    record Pair(String key, int value) {
        Pair {
            if (value < 0) {
                throw new IllegalArgumentException("value < 0");
            }
        }

        static Pair of(String key, int value) {
            return new Pair(key, value);
        }

        Pair withValue(int newValue) {
            return new Pair(key, newValue);
        }
    }

    // 5.record: реализует интерфейс
    record Rect(double w, double h) implements Shape {
        @Override
        public double area() {
            return w * h;
        }

        @Override
        public String name() {
            return "Rect";
        }
    }

    // 5.enum: поля, конструктор, абстрактный метод в константах
    enum Op {
        ADD("+") {
            @Override
            int apply(int a, int b) {
                return a + b;
            }
        },
        MUL("*") {
            @Override
            int apply(int a, int b) {
                return a * b;
            }
        };

        private final String symbol;

        Op(String symbol) {
            this.symbol = symbol;
        }

        abstract int apply(int a, int b);

        String symbol() {
            return symbol;
        }
    }

    // 5.Дженерики: обобщённый класс
    static class Box<T> {
        private T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        <R> Box<R> map(Function<T, R> f) {
            return new Box<>(f.apply(value));
        }
    }

    // 5.Дженерики: обобщённый интерфейс
    interface Stack2<T> {
        void push(T item);

        T pop();

        boolean isEmpty();
    }

    // 5.Дженерики: реализация обобщённого интерфейса
    static class ListStack<T> implements Stack2<T> {
        private final List<T> items = new ArrayList<>();

        @Override
        public void push(T item) {
            items.add(item);
        }

        @Override
        public T pop() {
            return items.remove(items.size() - 1);
        }

        @Override
        public boolean isEmpty() {
            return items.isEmpty();
        }
    }

    // 5.Дженерики: обобщённый метод с ограничением типа
    static <T extends Comparable<T>> T maxOf(List<T> items) {
        T best = items.get(0);
        for (T item : items) {
            if (item.compareTo(best) > 0) {
                best = item;
            }
        }
        return best;
    }

    // 5.Дженерики: wildcard
    static double sumOf(List<? extends Number> numbers) {
        double total = 0;
        for (Number n : numbers) {
            total += n.doubleValue();
        }
        return total;
    }

    // 5.Классы: sealed-интерфейс и записи — основа для switch по образцам
    sealed interface Expr permits Num, Add, Neg {}

    record Num(int value) implements Expr {}

    record Add(Expr left, Expr right) implements Expr {}

    record Neg(Expr inner) implements Expr {}

    // 5.switch по образцам: switch-выражение с sealed и деконструкцией record
    static int eval(Expr e) {
        return switch (e) {
            case Num n -> n.value();
            case Add(Expr l, Expr r) -> eval(l) + eval(r);
            case Neg(Expr inner) -> -eval(inner);
        };
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        List<Integer> numbers = new ArrayList<>();
        while (in.hasNext() && numbers.size() < 30) {
            if (in.hasNextInt()) {
                numbers.add(in.nextInt());
            } else {
                in.next();
            }
        }
        if (numbers.isEmpty()) {
            numbers.addAll(Arrays.asList(5, 3, 8, 3, 1, 9, 5));
        }
        classesAndInterfaces();
        records();
        enums();
        generics(numbers);
        lambdas(numbers);
        streams(numbers);
        patterns();
    }

    static void classesAndInterfaces() {
        // 5.Классы: полиморфизм через интерфейс
        List<Shape> shapes = List.of(new Square(2), new Circle(1), new Rect(2, 3), Shape.unit());
        for (Shape shape : shapes) {
            System.out.println(shape.describe());
        }
        // 5.Классы: анонимный класс, реализующий интерфейс
        Shape anonymous = new Shape() {
            @Override
            public double area() {
                return 42;
            }

            @Override
            public String name() {
                return "Anon";
            }
        };
        System.out.println(anonymous.describe());
        // 5.Классы: внутренний класс (нужен экземпляр внешнего)
        Scope5Bonus outer = new Scope5Bonus();
        Scope5Bonus.Counter counter = outer.new Counter();
        counter.bump();
        counter.bump();
        System.out.println(counter.value);
        // 5.Классы: локальный класс внутри метода
        class Local {
            String hello() {
                return "локальный";
            }
        }
        System.out.println(new Local().hello());
    }

    static void records() {
        // 5.record: создание, доступ к компонентам, equals/hashCode/toString
        Pair pair = Pair.of("k", 3);
        Pair other = pair.withValue(4);
        System.out.println(pair + " " + other + " " + pair.key() + " " + pair.value() + " " + pair.equals(Pair.of("k", 3))
                + " " + (pair.hashCode() == Pair.of("k", 3).hashCode()));
        try {
            Pair.of("bad", -1);
        } catch (IllegalArgumentException e) {
            System.out.println("record: " + e.getMessage());
        }
        // 5.record: локальный record
        record Point(int x, int y) {}
        System.out.println(new Point(1, 2));
    }

    static void enums() {
        // 5.enum: константы с поведением, values, valueOf
        for (Op op : Op.values()) {
            System.out.println(op + " " + op.symbol() + " " + op.apply(3, 4) + " " + op.ordinal());
        }
        System.out.println(Op.valueOf("MUL").apply(5, 6));
        // 5.enum: switch-выражение по enum
        for (Op op : Op.values()) {
            String text = switch (op) {
                case ADD -> "сложение";
                case MUL -> "умножение";
            };
            System.out.println(text);
        }
    }

    static void generics(List<Integer> numbers) {
        // 5.Дженерики: Box<T> и map
        Box<Integer> box = new Box<>(21);
        Box<String> text = box.map(v -> "v" + v * 2);
        System.out.println(box.get() + " " + text.get());
        // 5.Дженерики: ListStack<T>
        Stack2<String> stack = new ListStack<>();
        stack.push("a");
        stack.push("b");
        System.out.println(stack.pop() + stack.pop() + stack.isEmpty());
        // 5.Дженерики: ограниченный обобщённый метод и wildcard
        System.out.println(maxOf(numbers) + " " + maxOf(List.of("pear", "apple", "zebra")) + " " + sumOf(numbers)
                + " " + sumOf(List.of(1.5, 2.5)));
        // 5.Дженерики: Map<String, List<Integer>>
        Map<String, List<Integer>> groups = new TreeMap<>();
        for (Integer value : numbers) {
            groups.computeIfAbsent(value % 2 == 0 ? "even" : "odd", k -> new ArrayList<>()).add(value);
        }
        System.out.println(groups);
    }

    static void lambdas(List<Integer> numbers) {
        // 5.Лямбды: Function, BiFunction, Predicate, Supplier, Consumer, UnaryOperator, BinaryOperator
        Function<Integer, Integer> twice = x -> x * 2;
        BiFunction<Integer, Integer, Integer> plus = (x, y) -> x + y;
        Predicate<Integer> even = x -> x % 2 == 0;
        Supplier<String> greeting = () -> "привет";
        UnaryOperator<String> shout = s -> s.toUpperCase() + "!";
        BinaryOperator<Integer> maxOp = (x, y) -> x > y ? x : y;
        StringBuilder sink = new StringBuilder();
        Consumer<String> collect = s -> sink.append(s).append(';');
        collect.accept("a");
        collect.accept("b");
        System.out.println(twice.apply(21) + " " + plus.apply(2, 3) + " " + even.test(4) + " " + greeting.get()
                + " " + shout.apply("ура") + " " + maxOp.apply(3, 9) + " " + sink);
        // 5.Лямбды: композиция (andThen, compose, negate, and)
        System.out.println(twice.andThen(x -> x + 1).apply(5) + " " + twice.compose((Integer x) -> x + 1).apply(5)
                + " " + even.negate().test(4) + " " + even.and(x -> x > 2).test(4));
        // 5.Лямбды: ссылки на методы
        Function<String, Integer> length = String::length;
        Supplier<List<String>> maker = ArrayList::new;
        BiFunction<String, String, Boolean> same = String::equals;
        List<String> made = maker.get();
        made.add("x");
        System.out.println(length.apply("abcd") + " " + made + " " + same.apply("a", "a"));
        // 5.Лямбды: захват переменной и removeIf / forEach / replaceAll / sort с лямбдой
        int threshold = 4;
        List<Integer> copy = new ArrayList<>(numbers);
        copy.removeIf(x -> x < threshold);
        copy.replaceAll(x -> x * 10);
        copy.sort((x, y) -> y - x);
        StringBuilder printed = new StringBuilder();
        copy.forEach(x -> printed.append(x).append(' '));
        System.out.println(printed.toString().trim());
        // 5.Лямбды: Comparator.comparing и thenComparing
        List<String> words = new ArrayList<>(List.of("pear", "fig", "apple", "kiwi", "plum"));
        words.sort(Comparator.comparing(String::length).thenComparing(Comparator.reverseOrder()));
        System.out.println(words);
        // 5.Лямбды: Optional с лямбдами (map, filter, ifPresent, orElseGet)
        Optional<String> opt = Optional.of("значение");
        System.out.println(opt.map(String::length).orElse(0) + " " + opt.filter(s -> s.startsWith("x")).isPresent()
                + " " + Optional.<String>empty().orElseGet(() -> "запас"));
        opt.ifPresent(s -> System.out.println("есть " + s));
    }

    static void streams(List<Integer> numbers) {
        // 5.Stream: filter, map, collect(toList), sorted, distinct, limit
        List<Integer> doubledEvens = numbers.stream().filter(x -> x % 2 == 0).map(x -> x * 2).collect(Collectors.toList());
        List<Integer> top = numbers.stream().distinct().sorted(Comparator.reverseOrder()).limit(3).toList();
        System.out.println(doubledEvens + " " + top);
        // 5.Stream: mapToInt, sum, average, max, count, summaryStatistics
        int sum = numbers.stream().mapToInt(Integer::intValue).sum();
        double avg = numbers.stream().mapToInt(x -> x).average().orElse(0);
        long count = numbers.stream().filter(x -> x > 3).count();
        IntSummaryStatistics stats = numbers.stream().mapToInt(x -> x).summaryStatistics();
        System.out.println(sum + " " + String.format("%.3f", avg) + " " + count + " " + stats.getMin() + " " + stats.getMax());
        // 5.Stream: reduce, anyMatch, allMatch, noneMatch, findFirst
        int product = numbers.stream().limit(4).reduce(1, (a, b) -> a * b);
        Optional<Integer> firstBig = numbers.stream().filter(x -> x > 7).findFirst();
        System.out.println(product + " " + numbers.stream().anyMatch(x -> x > 8) + " " + numbers.stream().allMatch(x -> x > 0)
                + " " + numbers.stream().noneMatch(x -> x > 100) + " " + firstBig.orElse(-1));
        // 5.Collectors: joining, groupingBy, counting, partitioningBy, toMap
        String joined = numbers.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
        Map<Boolean, List<Integer>> parts = numbers.stream().collect(Collectors.partitioningBy(x -> x > 4));
        Map<Integer, Long> counts = numbers.stream().collect(Collectors.groupingBy(x -> x, TreeMap::new, Collectors.counting()));
        Map<Integer, String> asMap = numbers.stream().distinct().collect(Collectors.toMap(x -> x, x -> "n" + x, (a, b) -> a, TreeMap::new));
        System.out.println(joined + " " + parts + " " + counts + " " + asMap);
        // 5.Stream: IntStream.range, rangeClosed, boxed, flatMap, Stream.of, iterate
        System.out.println(IntStream.range(0, 5).sum() + " " + IntStream.rangeClosed(1, 5).boxed().collect(Collectors.toList())
                + " " + Stream.of("a,b", "c").flatMap(s -> Arrays.stream(s.split(","))).collect(Collectors.joining())
                + " " + Stream.iterate(1, x -> x * 3).limit(5).toList());
        // 5.Stream: groupingBy со средним и mapping
        Map<String, List<Integer>> byParity = numbers.stream().collect(
                Collectors.groupingBy(x -> x % 2 == 0 ? "even" : "odd", TreeMap::new, Collectors.mapping(x -> x * x, Collectors.toList())));
        System.out.println(byParity);
        // 5.Stream: sorted с компаратором и peek-free цепочка по строкам
        System.out.println(Stream.of("pear", "fig", "apple").sorted(Comparator.comparing(String::length)).toList());
    }

    static void patterns() {
        // 5.switch по образцам: вычисление дерева выражений
        Expr expr = new Add(new Num(5), new Neg(new Add(new Num(1), new Num(2))));
        System.out.println(eval(expr) + " " + expr);
        // 5.switch по образцам: switch-оператор по типу Object с guard (when)
        Object[] things = {1, "строка", 2.5, List.of(1, 2), 150};
        for (Object thing : things) {
            String kind = switch (thing) {
                case Integer i when i > 100 -> "большое целое " + i;
                case Integer i -> "целое " + i;
                case String s -> "строка длины " + s.length();
                case Double d -> "дробное " + d;
                default -> "другое " + thing;
            };
            System.out.println(kind);
        }
        // 5.Текстовые блоки
        String block = """
                Строка один
                  строка два
                """;
        System.out.println(block.lines().count() + " " + block.length());
        // 5.switch-выражение с yield
        int code = 3;
        String level = switch (code) {
            case 1, 2 -> "low";
            case 3 -> {
                String tmp = "mid";
                yield tmp + "dle";
            }
            default -> "high";
        };
        System.out.println(level);
    }
}
