package blueprints.inputs;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Как программа читает ввод — по её тексту, без предположений о формате.
 *
 * @param readsInput  есть ли обращение к System.in
 * @param lines       сколько строк давать на вход: по числу чтений строки (nextLine / readLine), от 1 до 5
 * @param commands    строки, с которыми программа сравнивает ввод ({@code case "add"}, {@code equals("exit")},
 *                    {@code case 3}) — по ним генерируются сценарии для программ-меню
 * @param exitCommand команда выхода из меню среди {@code commands} («0», «exit», «выход»…) или null. Её дописывают
 *                    в конец сценариев: иначе меню на конце ввода падает или, хуже, ждёт ввод бесконечно
 */
record ReadingProfile(boolean readsInput, int lines, List<String> commands, String exitCommand) {

    static final int MAX_LINES = 5;

    private static final Pattern LINE_READ = Pattern.compile("\\b(nextLine|readLine)\\s*\\(");
    private static final Pattern TOKEN_READ =
            Pattern.compile("\\bnext(Int|Long|Double|Float|Short|Byte|Boolean|BigInteger|BigDecimal)?\\s*\\(");
    private static final Pattern STRING_CASE = Pattern.compile("\\bcase\\s+\"([^\"\\\\]{1,20})\"");
    private static final Pattern STRING_EQUALS = Pattern.compile("\\.equals(?:IgnoreCase)?\\(\\s*\"([^\"\\\\]{1,20})\"\\s*\\)");
    /** {@code "exit".equals(cmd)} — так пишут, чтобы не получить NullPointerException. */
    private static final Pattern REVERSED_EQUALS = Pattern.compile("\"([^\"\\\\]{1,20})\"\\s*\\.equals(?:IgnoreCase)?\\(");
    private static final Pattern INT_CASE = Pattern.compile("\\bcase\\s+(-?\\d{1,4})\\s*[:,\\-]");
    private static final int MAX_COMMANDS = 12;

    /**
     * Слова, которыми учебные меню обычно завершаются, — в порядке предпочтения: слово надёжнее числа,
     * потому что {@code case 0} может оказаться обычным пунктом, а «выход» — почти наверняка выход.
     */
    private static final List<String> EXIT_WORDS = List.of(
            "exit", "quit", "выход", "выйти", "q", "bye", "stop", "стоп", "end", "конец", "0");

    static ReadingProfile of(String source) {
        String code = source == null ? "" : source;
        if (!code.contains("System.in")) {
            return new ReadingProfile(false, 0, List.of(), null);
        }
        int lineReads = count(LINE_READ, code);
        int reads = lineReads > 0 ? lineReads : count(TOKEN_READ, code);
        int lines = Math.max(1, Math.min(MAX_LINES, reads));
        // Команду выхода ищем среди всех сравнений, а не только первых MAX_COMMANDS: она часто последняя в меню.
        return new ReadingProfile(true, lines, commands(code, MAX_COMMANDS), exitCommand(commands(code, Integer.MAX_VALUE)));
    }

    private static List<String> commands(String code, int limit) {
        Set<String> found = new LinkedHashSet<>();
        for (Pattern pattern : List.of(STRING_CASE, STRING_EQUALS, INT_CASE, REVERSED_EQUALS)) {
            Matcher m = pattern.matcher(code);
            while (m.find() && found.size() < limit) {
                String value = m.group(1);
                if (!value.isBlank() && !value.contains("\n")) {
                    found.add(value);
                }
            }
        }
        return List.copyOf(found);
    }

    /** Первая по {@link #EXIT_WORDS} команда, которая встречается в коде; регистр не важен. */
    static String exitCommand(List<String> commands) {
        for (String word : EXIT_WORDS) {
            for (String command : commands) {
                if (command.strip().toLowerCase(Locale.ROOT).equals(word)) {
                    return command;
                }
            }
        }
        return null;
    }

    private static int count(Pattern pattern, String code) {
        Matcher m = pattern.matcher(code);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
