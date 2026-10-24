package blueprints.inputs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Входы для проверки поведения. Работает для любой программы: смотрит только на то, как она читает ввод
 * (см. {@link ReadingProfile}), и не предполагает формат.
 *
 * Детерминированно (фиксированное зерно): одна и та же программа всегда получает те же входы —
 * повторная проверка воспроизводит результат.
 */
public final class InputGenerator {

    public static final String NO_INPUT = "Без ввода";
    public static final String NORMAL = "Обычные значения";
    public static final String WIDE = "Крупные числа";
    public static final String REPEATED = "Повторяющиеся значения";
    public static final String EMPTY = "Пустые списки";
    public static final String NULLS = "Значения null";
    public static final String LARGE = "Большие входы";
    public static final String EDGE = "Граничные значения";
    public static final String COMMANDS = "Команды из кода";
    public static final String CORPUS = "Из корпуса";

    private static final long SEED = 20261024L;
    /** Неверный формат и крайние числа: программа на Scanner на них часто падает — это тоже поведение. */
    private static final List<String> EDGE_TOKENS = List.of("0", "-1", "2147483647", "abc");

    private final List<Path> corpusRoots;

    public InputGenerator() {
        this(defaultCorpusRoots());
    }

    InputGenerator(List<Path> corpusRoots) {
        this.corpusRoots = corpusRoots;
    }

    public List<LabeledInput> generate(String fileName, String source) {
        ReadingProfile profile = ReadingProfile.of(source);
        if (!profile.readsInput()) {
            return List.of(new LabeledInput(NO_INPUT, ""));
        }
        Random random = new Random(SEED);
        Map<String, LabeledInput> unique = new LinkedHashMap<>(); // повторы входов ничего не проверяют
        int lines = profile.lines();
        add(unique, NORMAL, 12, () -> join(lines, i -> numbers(random, 1 + random.nextInt(6))));
        add(unique, REPEATED, 6, () -> {
            String value = String.valueOf(1 + random.nextInt(5));
            return join(lines, i -> repeat(value, 2 + random.nextInt(5)));
        });
        emptyLists(lines, random).forEach(text -> put(unique, EMPTY, text));
        nulls(lines, random).forEach(text -> put(unique, NULLS, text));
        add(unique, LARGE, 2, () -> join(lines, i -> numbers(random, 50)));
        // Обычные значения — 1…5, чтобы были совпадения; ветки вида «qty >= 10» до них не доходят.
        // Отдельное зерно: остальные группы остаются прежними.
        Random wide = new Random(SEED + 1);
        add(unique, WIDE, 4, () -> join(lines, i -> wideNumbers(wide, 1 + wide.nextInt(4))));
        EDGE_TOKENS.forEach(token -> put(unique, EDGE, join(lines, i -> token)));
        commandScenarios(profile.commands(), profile.exitCommand(), random).forEach(text -> put(unique, COMMANDS, text));
        corpusInputs(fileName).forEach(text -> put(unique, CORPUS, text));
        return List.copyOf(unique.values());
    }

    /** Пустые строки: все, только первая, только последняя, через одну. */
    private static List<String> emptyLists(int lines, Random random) {
        return List.of(
                join(lines, i -> ""),
                join(lines, i -> i == 0 ? "" : numbers(random, 1 + random.nextInt(4))),
                join(lines, i -> i == lines - 1 ? "" : numbers(random, 1 + random.nextInt(4))),
                join(lines, i -> i % 2 == 1 ? "" : numbers(random, 1 + random.nextInt(4))));
    }

    /** Слово «null» среди значений: везде, только в первой строке, вперемешку. */
    private static List<String> nulls(int lines, Random random) {
        List<String> result = new ArrayList<>();
        result.add(join(lines, i -> "null"));
        result.add(join(lines, i -> i == 0 ? "null" : "1"));
        result.add(join(lines, i -> i == 0 ? "1" : "null"));
        for (int k = 0; k < 3; k++) {
            result.add(join(lines, i -> withNulls(random, 1 + random.nextInt(5))));
        }
        return result;
    }

    /**
     * Для программ-меню: последовательности команд, которые встречаются в коде. Если в коде есть команда выхода,
     * она дописывается в конец каждого сценария — так сценарий завершается штатно, как у человека за клавиатурой,
     * а не падением или вечным ожиданием на конце ввода.
     */
    private static List<String> commandScenarios(List<String> commands, String exit, Random random) {
        if (commands.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        commands.forEach(command -> result.add(withExit(command, exit))); // каждая команда по одной
        for (int k = 0; k < 4; k++) {
            int length = 2 + random.nextInt(5);
            result.add(withExit(join(length, i -> commands.get(random.nextInt(commands.size()))), exit));
        }
        return result;
    }

    private static String withExit(String scenario, String exit) {
        return exit == null || endsWith(scenario, exit) ? scenario : scenario + "\n" + exit;
    }

    /** Последняя строка входа — команда выхода. */
    private static boolean endsWith(String text, String exit) {
        String[] lines = text.strip().split("\n");
        return lines[lines.length - 1].strip().equals(exit.strip());
    }

    /** Команда выхода из меню, найденная в коде («0», «exit», «выход»…), или пусто. */
    public static Optional<String> exitCommand(String source) {
        return Optional.ofNullable(ReadingProfile.of(source).exitCommand());
    }

    /** Вход, последняя строка которого — команда выхода: на нём меню должно завершиться штатно. */
    public static boolean endsWithExit(LabeledInput input, String exit) {
        return exit != null && !input.text().isBlank() && endsWith(input.text(), exit);
    }

    private List<String> corpusInputs(String fileName) {
        String name = fileName == null ? "" : fileName.replaceFirst("\\.java$", "");
        if (name.isBlank()) {
            return List.of();
        }
        for (Path root : corpusRoots) {
            Path dir = root.resolve("ours").resolve(name).resolve("inputs");
            if (Files.isDirectory(dir)) {
                return readAll(dir);
            }
        }
        return List.of();
    }

    private static List<String> readAll(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".txt")).sorted()
                    .map(InputGenerator::readQuietly).filter(text -> text != null).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String readQuietly(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static List<Path> defaultCorpusRoots() {
        List<Path> roots = new ArrayList<>();
        String env = System.getenv("CORPUS_DIR");
        if (env != null && !env.isBlank()) {
            roots.add(Path.of(env));
        }
        roots.add(Path.of("..", "corpus"));
        roots.add(Path.of("corpus"));
        return roots;
    }

    private static void add(Map<String, LabeledInput> unique, String group, int count, Supplier<String> make) {
        for (int i = 0; i < count; i++) {
            put(unique, group, make.get());
        }
    }

    private static void put(Map<String, LabeledInput> unique, String group, String text) {
        unique.putIfAbsent(text, new LabeledInput(group, text));
    }

    private static String join(int lines, java.util.function.IntFunction<String> line) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < lines; i++) {
            parts.add(line.apply(i));
        }
        return String.join("\n", parts);
    }

    private static String numbers(Random random, int count) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(String.valueOf(1 + random.nextInt(5)));
        }
        return String.join(" ", values);
    }

    /** Числа от 6 до 1000 вперемешку с круглыми порогами 10, 100, 1000. */
    private static String wideNumbers(Random random, int count) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(String.valueOf(random.nextInt(4) == 0 ? List.of(10, 100, 1000).get(random.nextInt(3)) : 6 + random.nextInt(995)));
        }
        return String.join(" ", values);
    }

    private static String withNulls(Random random, int count) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(random.nextInt(3) == 0 ? "null" : String.valueOf(1 + random.nextInt(5)));
        }
        return String.join(" ", values);
    }

    private static String repeat(String value, int times) {
        return String.join(" ", java.util.Collections.nCopies(times, value));
    }
}
