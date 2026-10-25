import java.util.List;

public class BaitR4 {

    // ПРИМАНКА: «потолок» сужается до самого строгого лимита — maximumAllowed по смыслу ДОЛЖЕН уменьшаться.
    static int tightestCeiling(int[] limits, int start) {
        int maximumAllowed = start;
        for (int limit : limits) {
            if (limit < maximumAllowed) maximumAllowed = limit;
        }
        return maximumAllowed;
    }

    // ПРИМАНКА: бюджет урезается до самой дешёвой подходящей цены — maxBudget хранит верхнюю границу, а она падает.
    static int capBudget(int[] offers, int maxBudget) {
        for (int price : offers) {
            if (price < maxBudget) maxBudget = price;
        }
        return maxBudget;
    }

    // ПРИМАНКА: нейтральное имя best в методе без направления в имени — искать «самое дешёвое» правильно через «<».
    static int findCheapest(int[] prices) {
        int best = prices[0];
        for (int p : prices) {
            if (p < best) best = p;
        }
        return best;
    }

    // ПРИМАНКА: best в методе minDistance — направление «минимум» и сравнение «<» совпадают.
    static int minDistance(int[] d) {
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < d.length; i++) {
            if (d[i] < best) best = d[i];
        }
        return best;
    }

    // ПРИМАНКА: обычный максимум, сравнение направлено верно.
    static int maxOf(List<Integer> xs) {
        int max = Integer.MIN_VALUE;
        for (int x : xs) {
            if (x > max) max = x;
        }
        return max;
    }

    // ПРИМАНКА: «последний меньший» — переменная названа по смыслу lastSmaller, имени экстремума нет.
    static int lastSmaller(int[] a, int pivot) {
        int lastSmaller = -1;
        for (int v : a) {
            if (v < pivot) lastSmaller = v;
        }
        return lastSmaller;
    }

    // ПРИМАНКА: у if есть else — это разбиение, а не накопление экстремума.
    static int splitCount(int[] a, int max) {
        int low = 0;
        for (int v : a) {
            if (v < max) max = v; else low++;
        }
        return low + max;
    }

    public static void main(String[] args) {
        System.out.println(tightestCeiling(new int[]{50, 30, 40}, 100));
        System.out.println(capBudget(new int[]{70, 20}, 60));
        System.out.println(findCheapest(new int[]{5, 2, 9}));
        System.out.println(minDistance(new int[]{4, 1, 7}));
        System.out.println(maxOf(List.of(-3, -1, -7)));
        System.out.println(lastSmaller(new int[]{1, 9, 2}, 5));
        System.out.println(splitCount(new int[]{1, 9, 2}, 5));
    }
}
