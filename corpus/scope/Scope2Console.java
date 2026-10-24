import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Formatter;
import java.util.Scanner;

/**
 * Раздел 2 документа: ввод-вывод из консоли (System.out/err/in, Scanner, Formatter, StandardCharsets,
 * String.format, printf). Ввод читается только через has*-проверки, поэтому любой вход даёт вывод, а не падение.
 * Эталонный ввод — corpus/scope/input/Scope2Console.txt.
 */
public class Scope2Console {

    public static void main(String[] args) {
        // 2.System.in: чтение стандартного ввода через Scanner
        Scanner in = new Scanner(System.in);
        // 2.Scanner: hasNextInt и nextInt
        int count = in.hasNextInt() ? in.nextInt() : 3;
        // 2.Scanner: hasNextDouble и nextDouble
        double price = in.hasNextDouble() ? in.nextDouble() : 2.5;
        // 2.Scanner: hasNext и next (слово)
        String word = in.hasNext() ? in.next() : "слово";
        // 2.Scanner: nextLine — остаток текущей строки
        String rest = in.hasNextLine() ? in.nextLine() : "";
        // 2.Scanner: nextLine — целая строка (с кириллицей)
        String line = in.hasNextLine() ? in.nextLine() : "строка по умолчанию";
        // 2.Scanner: hasNextBoolean и nextBoolean
        boolean flag = in.hasNextBoolean() && in.nextBoolean();
        // 2.Scanner: hasNextLong и nextLong
        long big = in.hasNextLong() ? in.nextLong() : 0L;
        // 2.Scanner: цикл по оставшимся токенам (hasNext)
        int tokens = 0;
        StringBuilder tail = new StringBuilder();
        while (in.hasNext() && tokens < 20) {
            tail.append('[').append(in.next()).append(']');
            tokens++;
        }

        // 2.System.out: println, print и println без аргументов
        System.out.println("count=" + count);
        System.out.print("price=");
        System.out.print(price);
        System.out.println();
        System.out.println("word=" + word + " rest='" + rest + "' line='" + line + "'");
        System.out.println("flag=" + flag + " big=" + big + " tail=" + tail + " tokens=" + tokens);

        // 2.System.err: вывод в поток ошибок
        System.err.println("служебное сообщение в stderr");
        System.err.print("без перевода строки");
        System.err.println();

        // 2.printf: форматированный вывод
        System.out.printf("%d|%5d|%-5d|%05d%n", count, count, count, count);
        System.out.printf("%.2f|%8.3f|%e%n", price, price, price);
        System.out.printf("%s|%10s|%-10s|%c|%b%n", word, word, word, 'Я', flag);
        System.out.printf("%x|%X|%o|%08.3f|%,d%n", 255, 255, 8, price, 1234567);

        // 2.String.format: форматирование в строку
        String formatted = String.format("Итого: %d шт. по %.2f = %.2f", count, price, count * price);
        System.out.println(formatted);
        System.out.println(String.format("|%6.1f|%-6s|%3d|", 3.14159, "ab", 7));

        // 2.Formatter: форматирование в StringBuilder
        StringBuilder target = new StringBuilder();
        Formatter formatter = new Formatter(target);
        formatter.format("%s=%d;", "a", 1);
        formatter.format("%s=%d;", "b", 2);
        formatter.format("%5.1f%%", 12.345);
        formatter.close();
        System.out.println(target);

        // 2.StandardCharsets: кодировка UTF-8 и кириллица
        byte[] bytes = "Привет".getBytes(StandardCharsets.UTF_8);
        String decoded = new String(bytes, StandardCharsets.UTF_8);
        System.out.println(bytes.length + " " + decoded + " " + decoded.length());
        // 2.StandardCharsets: другие кодировки (ISO_8859_1, UTF_16)
        System.out.println("Привет".getBytes(StandardCharsets.UTF_16).length + " "
                + "abc".getBytes(StandardCharsets.ISO_8859_1).length + " "
                + StandardCharsets.UTF_8.name());

        // 2.System.out: PrintStream с явной кодировкой UTF-8
        PrintStream utf8 = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        utf8.println("Ёлка и ёж: кириллица в UTF-8");
        utf8.printf("%s:%d%n", "Ключ", 42);

        // 2.Scanner: разбор строки (Scanner над String)
        Scanner numbers = new Scanner("10 20 abc 30");
        int sum = 0;
        int bad = 0;
        while (numbers.hasNext()) {
            if (numbers.hasNextInt()) {
                sum += numbers.nextInt();
            } else {
                numbers.next();
                bad++;
            }
        }
        numbers.close();
        System.out.println("sum=" + sum + " bad=" + bad);

        // 2.Scanner: свой разделитель
        Scanner csv = new Scanner("a;b;;c").useDelimiter(";");
        int fields = 0;
        while (csv.hasNext()) {
            csv.next();
            fields++;
        }
        System.out.println("fields=" + fields);

        // 2.System: код выхода (System.exit) — после вывода, при «плохом» вводе
        if (count < 0) {
            System.out.println("отрицательное количество — выход с кодом 2");
            System.exit(2);
        }
        System.out.println("конец");
    }
}
