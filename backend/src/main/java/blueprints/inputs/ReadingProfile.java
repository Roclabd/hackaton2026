package blueprints.inputs;

import java.util.LinkedHashSet;
import java.util.List;
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
 */
record ReadingProfile(boolean readsInput, int lines, List<String> commands) {

    static final int MAX_LINES = 5;

    private static final Pattern LINE_READ = Pattern.compile("\\b(nextLine|readLine)\\s*\\(");
    private static final Pattern TOKEN_READ =
            Pattern.compile("\\bnext(Int|Long|Double|Float|Short|Byte|Boolean|BigInteger|BigDecimal)?\\s*\\(");
    private static final Pattern STRING_CASE = Pattern.compile("\\bcase\\s+\"([^\"\\\\]{1,20})\"");
    private static final Pattern STRING_EQUALS = Pattern.compile("\\.equals(?:IgnoreCase)?\\(\\s*\"([^\"\\\\]{1,20})\"\\s*\\)");
    private static final Pattern INT_CASE = Pattern.compile("\\bcase\\s+(-?\\d{1,4})\\s*[:,\\-]");
    private static final int MAX_COMMANDS = 12;

    static ReadingProfile of(String source) {
        String code = source == null ? "" : source;
        if (!code.contains("System.in")) {
            return new ReadingProfile(false, 0, List.of());
        }
        int lineReads = count(LINE_READ, code);
        int reads = lineReads > 0 ? lineReads : count(TOKEN_READ, code);
        int lines = Math.max(1, Math.min(MAX_LINES, reads));
        return new ReadingProfile(true, lines, commands(code));
    }

    private static List<String> commands(String code) {
        Set<String> found = new LinkedHashSet<>();
        for (Pattern pattern : List.of(STRING_CASE, STRING_EQUALS, INT_CASE)) {
            Matcher m = pattern.matcher(code);
            while (m.find() && found.size() < MAX_COMMANDS) {
                String value = m.group(1);
                if (!value.isBlank() && !value.contains("\n")) {
                    found.add(value);
                }
            }
        }
        return List.copyOf(found);
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
