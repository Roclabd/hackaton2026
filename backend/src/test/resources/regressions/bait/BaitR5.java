public class BaitR5 {

    // ПРИМАНКА: выборочная дисперсия — делитель n - 1 (поправка Бесселя) верен по определению.
    static double sampleVariance(int[] a) {
        double mean = 0;
        for (int v : a) mean += v;
        mean /= a.length;
        double sq = 0;
        for (int v : a) sq += (v - mean) * (v - mean);
        return sq / (a.length - 1);
    }

    // ПРИМАНКА: средний интервал между соседними отметками — интервалов ровно length - 1.
    static double computeMean(int[] stamps) {
        double span = stamps[stamps.length - 1] - stamps[0];
        return span / (stamps.length - 1);
    }

    // ПРИМАНКА: среднее без первого элемента (разминочного замера) — элементов length - 1.
    static double averageExcludingFirst(int[] a) {
        double sum = 0;
        for (int i = 1; i < a.length; i++) sum += a[i];
        return sum / (a.length - 1);
    }

    // ПРИМАНКА: обычное среднее с делителем length.
    static double average(int[] a) {
        double sum = 0;
        for (int v : a) sum += v;
        return sum / a.length;
    }

    // ПРИМАНКА: length - 1 — индекс последнего элемента, а не делитель среднего.
    static double mean(int[] a) {
        double total = 0;
        for (int i = 0; i <= a.length - 1; i++) total += a[i];
        return total / a.length;
    }

    public static void main(String[] args) {
        int[] a = {2, 4, 4, 4, 5, 5, 7, 9};
        System.out.println(sampleVariance(a));
        System.out.println(computeMean(new int[]{0, 10, 20, 35}));
        System.out.println(averageExcludingFirst(a));
        System.out.println(average(a));
        System.out.println(mean(a));
    }
}
