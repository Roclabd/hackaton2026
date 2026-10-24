import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Статистика текста: читает строки до конца ввода и считает слова, буквы, повторы, палиндромы.
 * Написано «в лоб»: вложенные циклы, склейка строк через += и list.contains в цикле.
 */
public class TextStats {

    static List<String> words(String text) {
        List<String> result = new ArrayList<>();
        String current = "";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-') {
                current += Character.toLowerCase(c);
            } else {
                if (!current.isEmpty()) {
                    result.add(current);
                }
                current = "";
            }
        }
        if (!current.isEmpty()) {
            result.add(current);
        }
        return result;
    }

    // Уникальные слова в порядке появления — через contains (квадратичная сложность)
    static List<String> unique(List<String> words) {
        List<String> seen = new ArrayList<>();
        for (String w : words) {
            if (!seen.contains(w)) {
                seen.add(w);
            }
        }
        return seen;
    }

    // Частота каждого слова — вложенный цикл
    static int[] frequencies(List<String> uniqueWords, List<String> all) {
        int[] freq = new int[uniqueWords.size()];
        for (int i = 0; i < uniqueWords.size(); i++) {
            for (int j = 0; j < all.size(); j++) {
                if (uniqueWords.get(i).equals(all.get(j))) {
                    freq[i]++;
                }
            }
        }
        return freq;
    }

    static boolean isPalindrome(String w) {
        int i = 0;
        int j = w.length() - 1;
        while (i < j) {
            if (w.charAt(i) != w.charAt(j)) {
                return false;
            }
            i++;
            j--;
        }
        return w.length() > 2;
    }

    static String reverse(String s) {
        String r = "";
        for (int i = s.length() - 1; i >= 0; i--) {
            r += s.charAt(i);
        }
        return r;
    }

    // Пары слов-анаграмм (сравнение «каждый с каждым»)
    static List<String> anagrams(List<String> words) {
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            for (int j = i + 1; j < words.size(); j++) {
                String a = words.get(i);
                String b = words.get(j);
                if (a.length() == b.length() && !a.equals(b) && sameLetters(a, b)) {
                    pairs.add(a + "/" + b);
                }
            }
        }
        return pairs;
    }

    static boolean sameLetters(String a, String b) {
        char[] x = a.toCharArray();
        char[] y = b.toCharArray();
        java.util.Arrays.sort(x);
        java.util.Arrays.sort(y);
        return java.util.Arrays.equals(x, y);
    }

    static int vowels(String text) {
        String vowels = "аеёиоуыэюяaeiou";
        int count = 0;
        for (char c : text.toLowerCase().toCharArray()) {
            if (vowels.indexOf(c) >= 0) {
                count++;
            }
        }
        return count;
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String text = "";
        int lines = 0;
        int longestLine = 0;
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            lines++;
            if (line.length() > longestLine) {
                longestLine = line.length();
            }
            text += line + "\n";
        }
        if (lines == 0) {
            text = "Шалаш и казак видели потоп. Кот ток пил, а топ кот ел. Мир рим мир!";
            System.out.println("Ввод пуст — анализирую пример.");
        }

        List<String> all = words(text);
        List<String> uniq = unique(all);
        int[] freq = frequencies(uniq, all);

        System.out.println("Строк: " + lines + ", самая длинная: " + longestLine + " симв.");
        System.out.println("Слов: " + all.size() + ", уникальных: " + uniq.size());
        System.out.println("Гласных: " + vowels(text));

        // Три самых частых слова (выбором максимума три раза)
        boolean[] used = new boolean[uniq.size()];
        for (int k = 0; k < 3 && k < uniq.size(); k++) {
            int best = -1;
            for (int i = 0; i < uniq.size(); i++) {
                if (!used[i] && (best == -1 || freq[i] > freq[best])) {
                    best = i;
                }
            }
            used[best] = true;
            System.out.println((k + 1) + ") " + uniq.get(best) + " — " + freq[best]);
        }

        String palindromes = "";
        for (String w : uniq) {
            if (isPalindrome(w)) {
                palindromes += w + " ";
            }
        }
        System.out.println("Палиндромы: " + (palindromes.isEmpty() ? "нет" : palindromes.trim()));
        System.out.println("Анаграммы: " + anagrams(uniq));

        String longest = "";
        for (String w : uniq) {
            if (w.length() > longest.length()) {
                longest = w;
            }
        }
        System.out.println("Самое длинное слово: " + longest + " (наоборот: " + reverse(longest) + ")");

        int[] lengths = new int[11];
        for (String w : all) {
            lengths[Math.min(10, w.length())]++;
        }
        StringBuilder sb = new StringBuilder("Длины:");
        for (int len = 1; len < lengths.length; len++) {
            if (lengths[len] > 0) {
                sb.append(' ').append(len == 10 ? "10+" : String.valueOf(len)).append('×').append(lengths[len]);
            }
        }
        System.out.println(sb);
    }
}
