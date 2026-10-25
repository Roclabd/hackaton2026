import java.util.List;

public class BaitR8 {

    // ПРИМАНКА: число страниц — целое, результат целочисленного деления присваивается int.
    static int pages(int total, int perPage) {
        int pages = total / perPage;
        return total % perPage == 0 ? pages : pages + 1;
    }

    // ПРИМАНКА: деление уже в double — левый операнд приведён до деления.
    static double ratio(int part, int whole) {
        double r = (double) part / whole;
        return r;
    }

    // ПРИМАНКА: один из операндов — double-литерал.
    static double half(int n) {
        return n / 2.0;
    }

    // ПРИМАНКА: целочисленное деление внутри умножения — округление вниз до ведра нужно намеренно.
    static double bucketStart(int value, int width) {
        double start = (value / width) * width;
        return start;
    }

    // ПРИМАНКА: метод возвращает long — расширения до double нет.
    static long middle(long lo, long hi) {
        return (lo + hi) / 2;
    }

    // ПРИМАНКА: среднее по списку — сумма накоплена в double, деление на size() уже дробное.
    static double averageOf(List<Integer> xs) {
        double sum = 0;
        for (int x : xs) sum += x;
        return xs.isEmpty() ? 0 : sum / xs.size();
    }

    // ПРИМАНКА: целые часы нужны именно целыми — результат идёт в int через тернарный оператор.
    static int wholeHours(int minutes) {
        int hours = minutes >= 0 ? minutes / 60 : 0;
        return hours;
    }

    // ПРИМАНКА: Math.floorDiv — явное целочисленное деление, результат int.
    static int floorBlocks(int offset, int size) {
        return Math.floorDiv(offset, size);
    }

    public static void main(String[] args) {
        System.out.println(pages(11, 5));
        System.out.println(ratio(1, 3));
        System.out.println(half(5));
        System.out.println(bucketStart(17, 5));
        System.out.println(middle(1, 8));
        System.out.println(averageOf(List.of(1, 2)));
        System.out.println(wholeHours(130));
        System.out.println(floorBlocks(-7, 3));
    }
}
