import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Анализ заказов интернет-магазина: Stream API, лямбды, Comparator.
 * Ввод (необязательный): минимальная сумма заказа и город для отбора.
 */
public class Orders {

    enum Status { NEW, PAID, SHIPPED, CANCELLED }

    record Product(String name, String category, double price) {}

    record Line(Product product, int qty) {
        double sum() {
            return product.price() * qty;
        }
    }

    static final class Order {
        private final int id;
        private final String customer;
        private final String city;
        private final List<Line> lines = new ArrayList<>();
        private Status status = Status.NEW;

        Order(int id, String customer, String city) {
            this.id = id;
            this.customer = customer;
            this.city = city;
        }

        Order add(Product p, int qty) {
            lines.add(new Line(p, qty));
            return this;
        }

        Order status(Status s) {
            this.status = s;
            return this;
        }

        double total() {
            return lines.stream().mapToDouble(Line::sum).sum();
        }

        int id() { return id; }
        String customer() { return customer; }
        String city() { return city; }
        Status getStatus() { return status; }
        List<Line> lines() { return lines; }
    }

    /** Обобщённая пара — для «топов». */
    static final class Pair<A, B extends Comparable<B>> implements Comparable<Pair<A, B>> {
        final A first;
        final B second;

        Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public int compareTo(Pair<A, B> other) {
            return second.compareTo(other.second);
        }

        @Override
        public String toString() {
            return first + "=" + second;
        }
    }

    static List<Order> sampleOrders() {
        Product phone = new Product("Телефон", "Электроника", 24990);
        Product cable = new Product("Кабель", "Электроника", 490);
        Product tea = new Product("Чай", "Продукты", 350);
        Product mug = new Product("Кружка", "Посуда", 590);
        Product book = new Product("Книга", "Книги", 899);
        List<Order> orders = new ArrayList<>();
        orders.add(new Order(1, "Анна", "Москва").add(phone, 1).add(cable, 2).status(Status.PAID));
        orders.add(new Order(2, "Борис", "Казань").add(tea, 5).add(mug, 2).status(Status.SHIPPED));
        orders.add(new Order(3, "Анна", "Москва").add(book, 3));
        orders.add(new Order(4, "Вера", "Ростов").add(phone, 2).status(Status.CANCELLED));
        orders.add(new Order(5, "Глеб", "Ростов").add(cable, 10).add(tea, 1).status(Status.PAID));
        orders.add(new Order(6, "Борис", "Казань").add(book, 1).add(mug, 4).status(Status.PAID));
        orders.add(new Order(7, "Дина", "Москва").add(tea, 12).status(Status.SHIPPED));
        return orders;
    }

    static <T, K extends Comparable<K>> Map<K, Long> countBy(List<T> items, Function<T, K> key) {
        return items.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.counting()));
    }

    static Optional<Order> biggest(List<Order> orders, Predicate<Order> filter) {
        return orders.stream().filter(filter).max(Comparator.comparingDouble(Order::total));
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        double minTotal = 0;
        String city = null;
        if (sc.hasNextDouble()) {
            minTotal = sc.nextDouble();
        }
        if (sc.hasNext()) {
            city = sc.next();
        }
        final double threshold = minTotal;
        final String wantedCity = city;

        List<Order> orders = sampleOrders();
        Predicate<Order> active = o -> o.getStatus() != Status.CANCELLED;
        Predicate<Order> bigEnough = o -> o.total() >= threshold;
        Predicate<Order> inCity = o -> wantedCity == null || o.city().equalsIgnoreCase(wantedCity);

        List<Order> selected = orders.stream()
                .filter(active.and(bigEnough).and(inCity))
                .sorted(Comparator.comparing(Order::customer).thenComparing(Order::total, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        System.out.printf("Отбор: сумма от %.2f, город %s — заказов %d%n", threshold,
                wantedCity == null ? "любой" : wantedCity, selected.size());
        selected.forEach(o -> System.out.printf("  #%d %-6s %-7s %-9s %10.2f%n",
                o.id(), o.customer(), o.city(), o.getStatus(), o.total()));

        Map<String, Double> byCustomer = selected.stream()
                .collect(Collectors.groupingBy(Order::customer, TreeMap::new, Collectors.summingDouble(Order::total)));
        System.out.println("По покупателям: " + byCustomer);

        Map<String, Integer> unitsByCategory = selected.stream()
                .flatMap(o -> o.lines().stream())
                .collect(Collectors.groupingBy(l -> l.product().category(), TreeMap::new,
                        Collectors.summingInt(Line::qty)));
        System.out.println("Штук по категориям: " + unitsByCategory);

        System.out.println("Статусы: " + countBy(orders, o -> o.getStatus().name()));

        DoubleSummaryStatistics stats = selected.stream().mapToDouble(Order::total).summaryStatistics();
        if (stats.getCount() > 0) {
            System.out.printf("Мин %.2f, макс %.2f, среднее %.2f%n", stats.getMin(), stats.getMax(), stats.getAverage());
        } else {
            System.out.println("Статистики нет: пустой отбор");
        }

        String top = biggest(orders, active)
                .map(o -> "Крупнейший заказ: #" + o.id() + " (" + o.customer() + ")")
                .orElse("Крупнейшего нет");
        System.out.println(top);

        List<Pair<String, Double>> pairs = byCustomer.entrySet().stream()
                .map(e -> new Pair<>(e.getKey(), e.getValue()))
                .sorted(Comparator.reverseOrder())
                .limit(3)
                .toList();
        System.out.println("Топ-3: " + pairs);

        String productNames = orders.stream()
                .flatMap(o -> o.lines().stream().map(l -> l.product().name()))
                .distinct()
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
        System.out.println("Товары: " + productNames);

        int[] squares = IntStream.rangeClosed(1, selected.size()).map(i -> i * i).toArray();
        System.out.println("Контрольная сумма: " + IntStream.of(squares).sum());

        boolean anyMoscow = orders.stream().anyMatch(o -> "Москва".equals(o.city()));
        boolean allPaid = selected.stream().allMatch(o -> o.getStatus() == Status.PAID);
        System.out.println("Есть Москва: " + anyMoscow + ", все оплачены: " + allPaid);
    }
}
