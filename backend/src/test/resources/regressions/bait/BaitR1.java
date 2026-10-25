import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaitR1 {

    record Order(int id, List<Integer> itemIds) { }
    record Cell(int row, int col) { }

    // ПРИМАНКА: обход строк и столбцов матрицы — вложенные циклы без сравнения переменных двух циклов.
    static int gridSum(int[][] grid) {
        int sum = 0;
        for (int[] row : grid) {
            for (int value : row) {
                sum += value;
            }
        }
        return sum;
    }

    // ПРИМАНКА: внутренний цикл идёт по дочерней коллекции внешнего элемента (o.itemIds()) — это обход дерева, а не n².
    static int countItem(List<Order> orders, int wanted) {
        int count = 0;
        for (Order o : orders) {
            for (Integer item : o.itemIds()) {
                if (item == wanted) {
                    count++;
                }
            }
        }
        return count;
    }

    // ПРИМАНКА: условие сравнивает переменную цикла с параметром, а не переменные двух циклов.
    static int countTarget(List<Integer> a, List<Integer> b, int target) {
        int hits = 0;
        for (Integer x : a) {
            for (Integer y : b) {
                if (x + y == target + 0 && y > 0) {
                    hits++;
                }
            }
        }
        return hits;
    }

    // ПРИМАНКА: декартово произведение — результат сам по себе n² пар, индексом это не ускорить.
    static List<Cell> allCells(List<Integer> rows, List<Integer> cols) {
        List<Cell> cells = new ArrayList<>();
        for (Integer r : rows) {
            for (Integer c : cols) {
                cells.add(new Cell(r, c));
            }
        }
        return cells;
    }

    // ПРИМАНКА: сравнение «меньше», а не равенство: подсчёт пар (x, y) с x < y индексом на HashMap не заменяется.
    static int countLess(List<Integer> a, List<Integer> b) {
        int pairs = 0;
        for (Integer x : a) {
            for (Integer y : b) {
                if (x < y) {
                    pairs++;
                }
            }
        }
        return pairs;
    }

    // ПРИМАНКА: совпадения уже ищутся через индекс — второго цикла нет.
    static int matchesWithIndex(List<Integer> a, List<Integer> b) {
        Map<Integer, Integer> freq = new HashMap<>();
        for (Integer y : b) {
            freq.merge(y, 1, Integer::sum);
        }
        int total = 0;
        for (Integer x : a) {
            total += freq.getOrDefault(x, 0);
        }
        return total;
    }

    public static void main(String[] args) {
        System.out.println(gridSum(new int[][]{{1, 2}, {3, 4}}));
        System.out.println(countItem(List.of(new Order(1, List.of(5, 6)), new Order(2, List.of(5))), 5));
        System.out.println(countTarget(List.of(1, 2), List.of(3, 4), 5));
        System.out.println(allCells(List.of(1, 2), List.of(1, 2)).size());
        System.out.println(countLess(List.of(1, 5), List.of(2, 6)));
        System.out.println(matchesWithIndex(List.of(1, 2, 2), List.of(2, 2, 3)));
    }
}
