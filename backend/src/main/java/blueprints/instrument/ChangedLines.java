package blueprints.instrument;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Какие строки отличаются у эталона и кандидата: построчный diff через наибольшую общую подпоследовательность (LCS).
 *
 * Строки сравниваются без отступов и пробелов по краям: сдвиг отступа поведение не меняет.
 * Номера строк — с 1, как в редакторе и в JavaParser.
 *
 * @param inCandidate изменённые и добавленные строки кандидата
 * @param removed     строки эталона, которых нет в кандидате (удалённые или заменённые)
 */
public record ChangedLines(SortedSet<Integer> inCandidate, SortedSet<Integer> removed) {

    /**
     * Сколько клеток таблицы LCS считаем честно. Больше — середина файла целиком считается изменённой:
     * это завышает изменения (больше строк попадёт под замер), но никогда их не прячет.
     */
    private static final long MAX_TABLE_CELLS = 4_000_000;

    public ChangedLines {
        inCandidate = Collections.unmodifiableSortedSet(new TreeSet<>(inCandidate));
        removed = Collections.unmodifiableSortedSet(new TreeSet<>(removed));
    }

    public static ChangedLines between(String original, String candidate) {
        String[] a = lines(original);
        String[] b = lines(candidate);
        // Общие начало и конец отрезаем сразу: правка обычно маленькая, и таблица LCS остаётся крошечной.
        int prefix = 0;
        while (prefix < a.length && prefix < b.length && a[prefix].equals(b[prefix])) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < a.length - prefix && suffix < b.length - prefix
                && a[a.length - 1 - suffix].equals(b[b.length - 1 - suffix])) {
            suffix++;
        }
        SortedSet<Integer> added = new TreeSet<>();
        SortedSet<Integer> removed = new TreeSet<>();
        diffMiddle(a, b, prefix, a.length - suffix, b.length - suffix, removed, added);
        return new ChangedLines(added, removed);
    }

    /** Ничего не изменилось (с точностью до отступов). */
    public boolean none() {
        return inCandidate.isEmpty() && removed.isEmpty();
    }

    /** LCS на отрезках a[from, aEnd) и b[from, bEnd): строки вне общей подпоследовательности — изменённые. */
    private static void diffMiddle(String[] a, String[] b, int from, int aEnd, int bEnd,
                                   SortedSet<Integer> removed, SortedSet<Integer> added) {
        int n = aEnd - from;
        int m = bEnd - from;
        if ((long) n * m > MAX_TABLE_CELLS) {
            for (int i = from; i < aEnd; i++) removed.add(i + 1);
            for (int j = from; j < bEnd; j++) added.add(j + 1);
            return;
        }
        // common[i][j] — длина LCS хвостов a[from+i..] и b[from+j..].
        int[][] common = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                common[i][j] = a[from + i].equals(b[from + j])
                        ? common[i + 1][j + 1] + 1
                        : Math.max(common[i + 1][j], common[i][j + 1]);
            }
        }
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a[from + i].equals(b[from + j])) {
                i++;
                j++;
            } else if (common[i + 1][j] >= common[i][j + 1]) {
                removed.add(from + i + 1);
                i++;
            } else {
                added.add(from + j + 1);
                j++;
            }
        }
        for (; i < n; i++) removed.add(from + i + 1);
        for (; j < m; j++) added.add(from + j + 1);
    }

    /** Те же разделители строк, что у JavaParser: \r\n, \n и одиночный \r. */
    private static String[] lines(String text) {
        String[] lines = text.split("\r\n|\r|\n", -1);
        for (int k = 0; k < lines.length; k++) {
            lines[k] = lines[k].strip();
        }
        return lines;
    }
}
