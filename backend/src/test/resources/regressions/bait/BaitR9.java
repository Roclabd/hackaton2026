import java.util.List;
import java.util.stream.Collectors;

public class BaitR9 {

    // ПРИМАНКА: StringBuilder вместо склейки.
    static String joinBuilder(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append(p).append(';');
        }
        return sb.toString();
    }

    // ПРИМАНКА: String.join и Collectors.joining — склейки в цикле нет.
    static String joinApi(List<String> parts) {
        String a = String.join(", ", parts);
        String b = parts.stream().map(String::toUpperCase).collect(Collectors.joining("|"));
        return a + " / " + b;
    }

    // ПРИМАНКА: строка объявлена внутри цикла — каждый раз новая и короткая, рост не накапливается.
    static int printLabels(List<Integer> ids) {
        int printed = 0;
        for (int id : ids) {
            String line = "#";
            line += id;
            System.out.println(line);
            printed++;
        }
        return printed;
    }

    // ПРИМАНКА: в цикле переменная перезаписывается, а не дописывается.
    static String lastLabel(List<Integer> ids) {
        String label = "";
        for (int id : ids) {
            label = "Item " + id;
        }
        return label;
    }

    // ПРИМАНКА: в цикле строка только укорачивается (trim/substring), не склеивается.
    static String stripPrefix(String s) {
        String rest = s;
        while (rest.startsWith(">")) {
            rest = rest.substring(1);
        }
        return rest;
    }

    // ПРИМАНКА: склейка одна, после цикла; в цикле считается только длина.
    static String summary(List<String> words) {
        int total = 0;
        for (String w : words) {
            total += w.length();
        }
        String result = "";
        result += "total=" + total;
        return result;
    }

    // ПРИМАНКА: ровно три итерации с фиксированной границей — копирования n² нет, строка из трёх символов.
    static String rule() {
        String sep = "";
        for (int i = 0; i < 3; i++) {
            sep += "-";
        }
        return sep;
    }

    public static void main(String[] args) {
        System.out.println(joinBuilder(List.of("a", "b")));
        System.out.println(joinApi(List.of("a", "b")));
        System.out.println(printLabels(List.of(1, 2)));
        System.out.println(lastLabel(List.of(1, 2)));
        System.out.println(stripPrefix(">>x"));
        System.out.println(summary(List.of("ab", "c")));
        System.out.println(rule());
    }
}
