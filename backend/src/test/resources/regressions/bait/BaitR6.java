import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BaitR6 {

    // ПРИМАНКА: идиома чтения — присваивание внутри сравнения с null, а не всё условие.
    static int countLines(String text) throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader(text));
        String line;
        int n = 0;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) n++;
        }
        return n;
    }

    // ПРИМАНКА: присваивание внутри сравнения «> 0» — условие целиком boolean-сравнение.
    static int drain(Iterator<Integer> it) {
        int value;
        int total = 0;
        while (it.hasNext() && (value = it.next()) > 0) {
            total += value;
        }
        return total;
    }

    // ПРИМАНКА: сравнение «==», а не присваивание, в if и в тернарном операторе.
    static String sign(int x) {
        if (x == 0) return "zero";
        return x > 0 == true ? "plus" : "minus";
    }

    // ПРИМАНКА: условие do-while — сравнение результата чтения символа.
    static int countChars(String text) throws IOException {
        StringReader in = new StringReader(text);
        int c;
        int n = -1;
        do {
            c = in.read();
            n++;
        } while (c != -1);
        return n;
    }

    // ПРИМАНКА: присваивание в теле, а в условии — только проверка флага.
    static boolean anyNegative(List<Integer> xs) {
        boolean found = false;
        for (int x : xs) {
            if (x < 0) found = true;
        }
        if (found) return true;
        return false;
    }

    // ПРИМАНКА: лямбда в условии содержит сравнение, присваивания нет вовсе.
    static boolean removeZeros(List<Integer> xs) {
        List<Integer> copy = new ArrayList<>(xs);
        if (copy.removeIf(x -> x == 0)) return true;
        return false;
    }

    public static void main(String[] args) throws IOException {
        System.out.println(countLines("a\n\nb\n"));
        System.out.println(drain(List.of(3, 4, -1, 5).iterator()));
        System.out.println(sign(-2));
        System.out.println(countChars("abc"));
        System.out.println(anyNegative(List.of(1, -1)));
        System.out.println(removeZeros(List.of(0, 1)));
    }
}
