import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Раздел 1 документа «Требования к поддержке Java»: базовые конструкции языка (java.lang).
 * Каждая конструкция помечена комментарием с номером пункта: «// 1.Операторы: тернарный».
 * Программа детерминирована: единственный ввод — необязательное целое число (по умолчанию 7).
 */
public class Scope1Lang {

    // 1.Структура: константа (static final)
    static final int LIMIT = 5;
    // 1.Структура: константа-строка
    static final String GREETING = "Привет";
    // 1.Структура: static-поле
    static int created = 0;
    // 1.Структура: поле экземпляра (final)
    private final int id;
    // 1.Структура: поле экземпляра (изменяемое)
    private String label;

    // 1.Структура: static-блок инициализации
    static {
        created = 100;
    }

    // 1.Структура: блок инициализации экземпляра
    {
        created += 1;
    }

    // 1.Структура: конструктор с параметрами
    Scope1Lang(int id, String label) {
        this.id = id;
        this.label = label;
    }

    // 1.Структура: конструктор, вызывающий другой конструктор (this(...))
    Scope1Lang(int id) {
        this(id, "без имени");
    }

    // 1.Классы-помощники: Object — переопределённый toString
    @Override
    public String toString() {
        return "Scope1Lang#" + id + "[" + label + "]";
    }

    // 1.Классы-помощники: Object — переопределённый equals
    @Override
    public boolean equals(Object other) {
        // 1.Операторы: instanceof с образцом (pattern matching)
        if (other instanceof Scope1Lang that) {
            return id == that.id;
        }
        return false;
    }

    // 1.Классы-помощники: Object — переопределённый hashCode
    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    // 1.Структура: метод экземпляра с параметром и возвращаемым значением
    String rename(String newLabel) {
        String old = label;
        label = newLabel;
        return old;
    }

    // 1.Классы-помощники: Comparable
    static class Point implements Comparable<Point> {
        final int x;
        final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public int compareTo(Point other) {
            if (x != other.x) {
                return Integer.compare(x, other.x);
            }
            return Integer.compare(y, other.y);
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + ")";
        }
    }

    // 1.Классы-помощники: Iterable
    static class Range implements Iterable<Integer> {
        private final int from;
        private final int to;

        Range(int from, int to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<Integer>() {
                private int next = from;

                @Override
                public boolean hasNext() {
                    return next < to;
                }

                @Override
                public Integer next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return next++;
                }
            };
        }
    }

    // 1.Классы-помощники: Enum
    enum Level {
        LOW(1), MEDIUM(5), HIGH(10);

        private final int weight;

        Level(int weight) {
            this.weight = weight;
        }

        int weight() {
            return weight;
        }

        Level next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int raw = in.hasNextInt() ? in.nextInt() : 7;
        // n всегда от 0 до 9: циклы ниже не зависят от «страшных» входов
        int n = Math.floorMod(raw, 10);
        System.out.println(GREETING + ", n=" + n);
        primitives();
        wrappers();
        boxing(n);
        arrays(n);
        arithmetic(n);
        logical(n);
        bitwise(n);
        comparison(n);
        ternary(n);
        instanceofAndCast(n);
        concatenation(n);
        branching(n);
        switches(n);
        loops(n);
        labels(n);
        simpleTry(n);
        structure(n);
        helpers(n);
    }

    static void primitives() {
        // 1.Типы: примитив int
        int i = 42;
        // 1.Типы: примитив long
        long l = 1L << 40;
        // 1.Типы: примитив double
        double d = 3.5;
        // 1.Типы: примитив float
        float f = 1.25f;
        // 1.Типы: примитив short (с сужением)
        short s = (short) 40000;
        // 1.Типы: примитив byte (с сужением)
        byte b = (byte) 200;
        // 1.Типы: примитив char
        char c = 'Ж';
        // 1.Типы: примитив boolean
        boolean flag = true;
        System.out.println(i + " " + l + " " + d + " " + f + " " + s + " " + b + " " + c + " " + flag);
        // 1.Типы: литералы 16-ричный, двоичный, с подчёркиваниями
        int hex = 0xFF;
        int bin = 0b1010;
        int big = 1_000_000;
        System.out.println(hex + " " + bin + " " + big);
        // 1.Структура: вывод типа локальной переменной (var)
        var inferred = 10L;
        System.out.println(inferred * 2);
    }

    static void wrappers() {
        // 1.Типы: обёртка Integer
        Integer wi = 127;
        // 1.Типы: обёртка Long
        Long wl = 5L;
        // 1.Типы: обёртка Double
        Double wd = 2.5;
        // 1.Типы: обёртка Float
        Float wf = 1.5f;
        // 1.Типы: обёртка Short
        Short ws = 7;
        // 1.Типы: обёртка Byte
        Byte wb = 3;
        // 1.Типы: обёртка Character
        Character wc = 'x';
        // 1.Типы: обёртка Boolean
        Boolean wo = Boolean.TRUE;
        // 1.Типы: Number — общий предок обёрток
        Number num = wi;
        System.out.println(num.doubleValue() + " " + num.intValue() + " " + wl + " " + wd + " " + wf);
        System.out.println(ws + " " + wb + " " + wc + " " + wo);
        // 1.Типы: сравнение обёрток (equals и кэш малых значений)
        Integer same = 127;
        System.out.println(wi.equals(same) + " " + (wi == same));
        // 1.Типы: статические методы обёрток
        System.out.println(Integer.parseInt("123") + Integer.valueOf(7) + " " + Long.parseLong("99")
                + " " + Double.compare(1.5, 2.5) + " " + Character.isDigit('7') + " " + Boolean.parseBoolean("true")
                + " " + Integer.MAX_VALUE + " " + Integer.toBinaryString(10));
    }

    static void boxing(int n) {
        // 1.Операторы: автоупаковка int -> Integer
        Integer boxed = n;
        // 1.Операторы: автораспаковка Integer -> int в арифметике
        int unboxed = boxed + 1;
        // 1.Операторы: упаковка и распаковка в составном присваивании
        Integer sum = 0;
        for (int k = 0; k < n; k++) {
            sum += k;
        }
        // 1.Операторы: распаковка null даёт NullPointerException
        Integer nothing = null;
        String outcome;
        try {
            int broken = nothing;
            outcome = "no " + broken;
        } catch (NullPointerException e) {
            outcome = "NPE";
        }
        System.out.println(boxed + " " + unboxed + " " + sum + " " + outcome);
    }

    static void arrays(int n) {
        // 1.Типы: одномерный массив с инициализатором
        int[] numbers = {5, 3, 9, 1, n};
        // 1.Типы: одномерный массив нужной длины
        double[] weights = new double[3];
        weights[1] = 2.5;
        // 1.Типы: массив строк
        String[] words = {"b", "a", "c"};
        // 1.Типы: многомерный массив (прямоугольный)
        int[][] grid = new int[3][4];
        for (int r = 0; r < grid.length; r++) {
            for (int c = 0; c < grid[r].length; c++) {
                grid[r][c] = r * c;
            }
        }
        // 1.Типы: многомерный массив (рваный)
        int[][] jagged = new int[3][];
        for (int r = 0; r < jagged.length; r++) {
            jagged[r] = new int[r + 1];
            Arrays.fill(jagged[r], r);
        }
        // 1.Типы: многомерный массив с инициализатором
        int[][] fixed = {{1, 2}, {3, 4}};
        // 1.Типы: трёхмерный массив
        int[][][] cube = new int[2][2][2];
        cube[1][1][1] = 8;
        // 1.Типы: массив символов
        char[] letters = "abc".toCharArray();
        // 1.Классы-помощники: System.arraycopy
        int[] copy = new int[numbers.length];
        System.arraycopy(numbers, 0, copy, 0, numbers.length);
        // 1.Типы: клонирование массива
        int[] cloned = numbers.clone();
        Arrays.sort(cloned);
        System.out.println(Arrays.toString(numbers) + Arrays.toString(weights) + Arrays.toString(words));
        System.out.println(Arrays.deepToString(grid) + Arrays.deepToString(jagged) + Arrays.deepToString(fixed));
        System.out.println(Arrays.deepToString(cube) + new String(letters) + Arrays.toString(copy) + Arrays.toString(cloned));
        System.out.println(numbers.length + " " + grid[2][3] + " " + fixed[1][0]);
    }

    static void arithmetic(int n) {
        // 1.Операторы: арифметические + - * / %
        int a = 17;
        int sum = a + n;
        int diff = a - n;
        int prod = a * n;
        int quot = a / (n + 1);
        int rest = a % (n + 1);
        // 1.Операторы: целочисленное деление и остаток отрицательных
        int negQuot = -17 / 5;
        int negRest = -17 % 5;
        // 1.Операторы: деление дробных
        double ratio = a / 4.0;
        // 1.Операторы: унарные минус и плюс
        int negated = -a;
        int positive = +a;
        // 1.Операторы: инкремент и декремент (префиксный и постфиксный)
        int counter = n;
        int post = counter++;
        int pre = ++counter;
        counter--;
        --counter;
        // 1.Операторы: составное присваивание
        int compound = 10;
        compound += 5;
        compound -= 3;
        compound *= 2;
        compound /= 4;
        compound %= 4;
        // 1.Операторы: переполнение int
        int overflow = Integer.MAX_VALUE + 1;
        System.out.println(sum + " " + diff + " " + prod + " " + quot + " " + rest + " " + negQuot + " " + negRest);
        System.out.println(ratio + " " + negated + " " + positive + " " + post + " " + pre + " " + counter);
        System.out.println(compound + " " + overflow);
    }

    static void logical(int n) {
        boolean even = n % 2 == 0;
        boolean small = n < 5;
        // 1.Операторы: логические && || !
        System.out.println((even && small) + " " + (even || small) + " " + !even);
        // 1.Операторы: логическое исключающее ИЛИ ^
        System.out.println(even ^ small);
        // 1.Операторы: нескоростные логические & и |
        System.out.println((even & small) + " " + (even | small));
        // 1.Операторы: короткое замыкание (правая часть не вычисляется)
        int[] calls = {0};
        boolean lazy = even || touch(calls);
        boolean eager = even | touch(calls);
        System.out.println(lazy + " " + eager + " " + calls[0]);
    }

    static boolean touch(int[] calls) {
        calls[0]++;
        return true;
    }

    static void bitwise(int n) {
        int x = 0b1100 + n;
        int y = 0b1010;
        // 1.Операторы: побитовые & | ^ ~
        System.out.println((x & y) + " " + (x | y) + " " + (x ^ y) + " " + (~x));
        // 1.Операторы: сдвиги << >> >>>
        System.out.println((x << 2) + " " + (-x >> 1) + " " + (-x >>> 28));
        // 1.Операторы: составные побитовые присваивания
        int z = x;
        z &= 0xF;
        z |= 0x10;
        z ^= 0x3;
        z <<= 1;
        z >>= 1;
        z >>>= 1;
        System.out.println(z);
    }

    static void comparison(int n) {
        // 1.Операторы: сравнения == != < > <= >=
        System.out.println((n == 3) + " " + (n != 3) + " " + (n < 3) + " " + (n > 3) + " " + (n <= 3) + " " + (n >= 3));
        // 1.Операторы: сравнение дробных (NaN не равен себе)
        double nan = 0.0 / 0.0;
        System.out.println((nan == nan) + " " + (nan != nan) + " " + (1.0 / 0.0));
        // 1.Операторы: сравнение строк (== и equals)
        String s1 = "abc";
        String s2 = new String("abc");
        System.out.println((s1 == s2) + " " + s1.equals(s2));
    }

    static void ternary(int n) {
        // 1.Операторы: тернарный
        String parity = n % 2 == 0 ? "чёт" : "нечет";
        // 1.Операторы: вложенный тернарный
        String size = n < 3 ? "мало" : n < 7 ? "средне" : "много";
        // 1.Операторы: тернарный внутри аргумента вызова
        System.out.println(parity + " " + size + " " + (n > 4 ? n * 2 : n / 2));
    }

    static void instanceofAndCast(int n) {
        Object[] things = {"text", n, 2.5, 'c', new int[]{1}, null};
        for (Object thing : things) {
            // 1.Операторы: instanceof
            if (thing instanceof String) {
                // 1.Операторы: приведение ссылочного типа
                String s = (String) thing;
                System.out.println("String " + s.length());
            } else if (thing instanceof Integer number) {
                // 1.Операторы: instanceof с образцом
                System.out.println("Integer " + (number + 1));
            } else if (thing instanceof Number) {
                System.out.println("Number " + ((Number) thing).intValue());
            } else if (thing instanceof int[]) {
                System.out.println("int[] " + ((int[]) thing).length);
            } else {
                System.out.println("other " + thing);
            }
        }
        // 1.Операторы: приведение примитивов (сужение и расширение)
        double d = 3.99;
        int truncated = (int) d;
        long widened = n;
        char next = (char) ('a' + n);
        long product = (long) n * Integer.MAX_VALUE;
        byte wrapped = (byte) (130 + n);
        System.out.println(truncated + " " + widened + " " + next + " " + product + " " + wrapped);
    }

    static void concatenation(int n) {
        // 1.Операторы: конкатенация строк (число после строки)
        String a = "n=" + n + 1;
        // 1.Операторы: конкатенация (сложение до строки)
        String b = n + 1 + "=n";
        // 1.Операторы: конкатенация символов
        String c = "" + 'a' + 'b';
        String d = 'a' + 'b' + "";
        // 1.Операторы: конкатенация с null
        String missing = null;
        String e = "x" + missing;
        // 1.Операторы: += для строки
        String f = "ab";
        f += 'c';
        f += 1;
        f += 2.5;
        System.out.println(a + " " + b + " " + c + " " + d + " " + e + " " + f);
    }

    static void branching(int n) {
        // 1.Поток: if без else
        if (n > 5) {
            System.out.println("больше пяти");
        }
        // 1.Поток: if / else
        if (n % 2 == 0) {
            System.out.println("чётное");
        } else {
            System.out.println("нечётное");
        }
        // 1.Поток: цепочка if / else if / else
        if (n == 0) {
            System.out.println("ноль");
        } else if (n < 4) {
            System.out.println("малое");
        } else if (n < 8) {
            System.out.println("среднее");
        } else {
            System.out.println("большое");
        }
        // 1.Поток: вложенный if
        if (n > 2) {
            if (n < 6) {
                System.out.println("между 3 и 5");
            }
        }
        // 1.Поток: if без фигурных скобок
        if (n == 7) System.out.println("семь");
        else System.out.println("не семь");
    }

    static void switches(int n) {
        // 1.Поток: switch (старый, с двоеточием, break и проваливанием)
        switch (n) {
            case 0:
                System.out.println("ноль");
                break;
            case 1:
            case 2:
                System.out.println("один или два");
            case 3:
                System.out.println("проваливание до трёх");
                break;
            default:
                System.out.println("иначе");
        }
        // 1.Поток: switch по строке
        String word = n % 2 == 0 ? "even" : "odd";
        switch (word) {
            case "even":
                System.out.println("E");
                break;
            case "odd":
                System.out.println("O");
                break;
        }
        // 1.Поток: switch по char
        switch ((char) ('a' + n % 3)) {
            case 'a' -> System.out.println("буква a");
            case 'b' -> System.out.println("буква b");
            default -> System.out.println("другая буква");
        }
        // 1.Поток: switch (стрелочный, оператор) с блоком
        switch (n % 4) {
            case 0 -> System.out.println("кратно четырём");
            case 1, 2 -> {
                int twice = n * 2;
                System.out.println("один или два по модулю: " + twice);
            }
            case 3 -> System.out.println("остаток три");
            default -> throw new IllegalStateException("недостижимо: " + n);
        }
    }

    static void loops(int n) {
        // 1.Поток: for
        int total = 0;
        for (int i = 0; i < n; i++) {
            total += i;
        }
        // 1.Поток: for с несколькими переменными
        for (int i = 0, j = 10; i < j; i += 3, j--) {
            total += i * j;
        }
        // 1.Поток: for с пустыми частями
        int guard = 0;
        for (;;) {
            if (++guard > 3) {
                break;
            }
        }
        // 1.Поток: for-each по массиву
        int[] data = {4, 8, 15, 16, 23, 42};
        int dataSum = 0;
        for (int value : data) {
            dataSum += value;
        }
        // 1.Поток: for-each по Iterable
        int rangeSum = 0;
        for (int value : new Range(0, n)) {
            rangeSum += value;
        }
        // 1.Поток: while
        int w = n;
        while (w > 0) {
            w -= 3;
            total++;
        }
        // 1.Поток: do-while (тело выполняется хотя бы раз)
        int d = 0;
        do {
            d++;
        } while (d < n);
        // 1.Поток: break в цикле
        int found = -1;
        for (int i = 0; i < data.length; i++) {
            if (data[i] > 15) {
                found = i;
                break;
            }
        }
        // 1.Поток: continue в цикле
        int oddSum = 0;
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                continue;
            }
            oddSum += i;
        }
        System.out.println(total + " " + guard + " " + dataSum + " " + rangeSum + " " + w + " " + d + " " + found + " " + oddSum);
    }

    static void labels(int n) {
        // 1.Поток: break с меткой выходит из внешнего цикла
        int hits = 0;
        outer:
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                if (i * j > n + 3) {
                    break outer;
                }
                hits++;
            }
        }
        // 1.Поток: continue с меткой
        int skipped = 0;
        rows:
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (j > i) {
                    skipped++;
                    continue rows;
                }
            }
        }
        // 1.Поток: метка на блоке
        int blockValue = 1;
        block:
        {
            if (n > 3) {
                break block;
            }
            blockValue = 2;
        }
        System.out.println(hits + " " + skipped + " " + blockValue);
    }

    static void simpleTry(int n) {
        // 1.Поток: try/catch/finally
        try {
            // 1.Поток: throw (JVM бросает ArithmeticException)
            int value = 10 / (n - 3);
            System.out.println("частное " + value);
        } catch (ArithmeticException e) {
            System.out.println("catch " + e.getMessage());
        } finally {
            System.out.println("finally");
        }
        // 1.Поток: throw собственного оператора throw
        try {
            check(n);
            System.out.println("check пройден");
        } catch (IllegalArgumentException e) {
            System.out.println("поймано: " + e.getMessage());
        }
    }

    static void check(int n) {
        if (n > 8) {
            // 1.Поток: throw new
            throw new IllegalArgumentException("слишком большое " + n);
        }
    }

    // 1.Структура: метод с несколькими параметрами и возвращаемым значением
    static int clamp(int value, int low, int high) {
        // 1.Поток: return из середины метода
        if (value < low) {
            return low;
        }
        if (value > high) {
            return high;
        }
        return value;
    }

    // 1.Структура: метод void с ранним return
    static void announce(int value) {
        if (value < 0) {
            return;
        }
        System.out.println("значение " + value);
    }

    // 1.Структура: рекурсивный метод
    static long factorial(int k) {
        return k <= 1 ? 1 : k * factorial(k - 1);
    }

    // 1.Структура: метод с varargs
    static int sumAll(int... values) {
        int total = 0;
        for (int v : values) {
            total += v;
        }
        return total;
    }

    static void structure(int n) {
        // 1.Структура: локальная переменная и константа (final)
        final int base = 100;
        int shifted = base + n;
        // 1.Структура: вызов статического метода
        int clamped = clamp(shifted, 0, 105);
        announce(clamped);
        announce(-1);
        // 1.Структура: вызов конструктора (new)
        Scope1Lang first = new Scope1Lang(1, "первый");
        Scope1Lang second = new Scope1Lang(2);
        // 1.Структура: вызов метода экземпляра
        String old = second.rename("второй");
        // 1.Структура: static-поле через имя класса
        Scope1Lang.created += 10;
        System.out.println(first + " " + second + " " + old + " " + created + " " + LIMIT);
        System.out.println(factorial(5) + " " + sumAll() + " " + sumAll(1, 2, 3));
        System.out.println(first.equals(new Scope1Lang(1)) + " " + first.equals(second) + " "
                + (first.hashCode() == new Scope1Lang(1).hashCode()));
    }

    static void helpers(int n) {
        // 1.Классы-помощники: String
        String text = "  Hello, Java World  ";
        String trimmed = text.trim();
        System.out.println(trimmed.length() + " " + trimmed.charAt(0) + " " + trimmed.substring(7) + " "
                + trimmed.indexOf("Java") + " " + trimmed.toUpperCase() + " " + trimmed.replace('l', 'L')
                + " " + trimmed.contains("World") + " " + trimmed.startsWith("Hello") + " " + "b".compareTo("a"));
        System.out.println(String.join("-", trimmed.split(" ")) + " " + String.valueOf(n) + " " + "ab".repeat(2)
                + " " + "".isEmpty() + " " + "  ".isBlank() + " " + "a,b,,c".split(",").length);
        // 1.Классы-помощники: StringBuilder
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(i).append(',');
        }
        sb.insert(0, "[").append("]");
        sb.setCharAt(0, '<');
        if (sb.length() > 2) {
            sb.deleteCharAt(1);
        }
        System.out.println(sb + " " + sb.length() + " " + new StringBuilder("abc").reverse());
        // 1.Классы-помощники: Math
        System.out.println(Math.abs(-n) + " " + Math.max(n, 5) + " " + Math.min(n, 5) + " " + Math.pow(2, n)
                + " " + Math.sqrt(16) + " " + Math.floor(2.7) + " " + Math.ceil(2.1) + " " + Math.round(2.5)
                + " " + Math.floorMod(-7, 3) + " " + Math.hypot(3, 4));
        // 1.Классы-помощники: System (arraycopy, getProperty, lineSeparator)
        int[] src = {1, 2, 3, 4};
        int[] dst = new int[4];
        System.arraycopy(src, 1, dst, 0, 3);
        System.out.println(Arrays.toString(dst) + " " + System.lineSeparator().isEmpty() + " " + System.getProperty("no.such.property", "dflt"));
        // 1.Классы-помощники: Comparable
        Point p1 = new Point(1, 2);
        Point p2 = new Point(1, 3);
        System.out.println(p1.compareTo(p2) + " " + p2.compareTo(p1) + " " + p1.compareTo(new Point(1, 2)));
        // 1.Классы-помощники: Iterable
        StringBuilder range = new StringBuilder();
        for (Integer v : new Range(2, 6)) {
            range.append(v);
        }
        System.out.println(range);
        // 1.Классы-помощники: CharSequence
        CharSequence cs = new StringBuilder("sequence");
        System.out.println(cs.length() + " " + cs.charAt(2) + " " + cs.subSequence(0, 3) + " " + countVowels(cs)
                + " " + countVowels("education"));
        // 1.Классы-помощники: Enum (values, valueOf, ordinal, name, compareTo)
        Level level = Level.values()[n % 3];
        System.out.println(level + " " + level.ordinal() + " " + level.name() + " " + level.weight() + " "
                + level.next() + " " + Level.valueOf("HIGH").compareTo(Level.LOW));
        // 1.Классы-помощники: Enum в switch
        switch (level) {
            case LOW -> System.out.println("низкий");
            case MEDIUM -> System.out.println("средний");
            case HIGH -> System.out.println("высокий");
        }
        // 1.Классы-помощники: Object (getClass, equals)
        Object o = new Point(0, 0);
        System.out.println(o.getClass().getSimpleName() + " " + o.equals(o));
    }

    static int countVowels(CharSequence text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if ("aeiou".indexOf(text.charAt(i)) >= 0) {
                count++;
            }
        }
        return count;
    }
}
