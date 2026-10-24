import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.TreeMap;

/**
 * Симуляция кофейни за один день — без ввода, все данные в коде.
 * Заказы, очередь к бариста, акции, итоговый отчёт. Используются sealed-интерфейс,
 * record-шаблоны в switch, текстовый блок, блоки инициализации, локальные классы.
 */
public class CoffeeShop {

    // ---------- Меню ----------

    sealed interface MenuItem permits Drink, Dessert, Combo {}

    record Drink(String name, int volumeMl, int price, boolean hot) implements MenuItem {}

    record Dessert(String name, int grams, int price) implements MenuItem {}

    record Combo(Drink drink, Dessert dessert, int discountPercent) implements MenuItem {}

    static int priceOf(MenuItem item) {
        return switch (item) {
            case Drink d when d.volumeMl() > 400 -> d.price() + 40;
            case Drink d -> d.price();
            case Dessert(String name, int grams, int price) -> grams > 150 ? price + 20 : price;
            case Combo(Drink d, Dessert s, int discount) -> (priceOf(d) + priceOf(s)) * (100 - discount) / 100;
        };
    }

    static String describe(MenuItem item) {
        if (item instanceof Combo c) {
            return "комбо «" + c.drink().name() + " + " + c.dessert().name() + "»";
        } else if (item instanceof Drink d && d.hot()) {
            return "горячий " + d.name();
        } else if (item instanceof Drink d) {
            return "холодный " + d.name();
        }
        return ((Dessert) item).name();
    }

    // ---------- Кольцевой буфер последних заказов (свой Iterable) ----------

    static final class Ring<T> implements Iterable<T> {
        private final Object[] data;
        private int start;
        private int size;

        Ring(int capacity) {
            data = new Object[capacity];
        }

        void push(T value) {
            int end = (start + size) % data.length;
            data[end] = value;
            if (size < data.length) {
                size++;
            } else {
                start = (start + 1) % data.length;
            }
        }

        @Override
        public Iterator<T> iterator() {
            return new Iterator<>() {
                private int i = 0;

                @Override
                public boolean hasNext() {
                    return i < size;
                }

                @SuppressWarnings("unchecked")
                @Override
                public T next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return (T) data[(start + i++) % data.length];
                }
            };
        }
    }

    // ---------- Состояние кофейни ----------

    static final Map<String, MenuItem> MENU = new TreeMap<>();
    static int beansGrams;

    static {
        Drink latte = new Drink("латте", 350, 220, true);
        Drink raf = new Drink("раф", 450, 260, true);
        Drink lemonade = new Drink("лимонад", 500, 180, false);
        Dessert cake = new Dessert("чизкейк", 160, 250);
        Dessert cookie = new Dessert("печенье", 60, 90);
        MENU.put("латте", latte);
        MENU.put("раф", raf);
        MENU.put("лимонад", lemonade);
        MENU.put("чизкейк", cake);
        MENU.put("печенье", cookie);
        MENU.put("завтрак", new Combo(latte, cookie, 15));
        beansGrams = 1000;
    }

    private final List<String> log = new ArrayList<>();
    private int served;

    {
        log.add("Кофейня открыта");
    }

    record Order(int minute, String customer, String item) {}

    static final String REPORT_TEMPLATE = """
            ===== Отчёт за день =====
            Обслужено: %d
            Выручка: %d руб.
            Средний чек: %.2f руб.
            Остаток зёрен: %d г
            """;

    int process(List<Order> orders) {
        Queue<Order> queue = new ArrayDeque<>(orders);
        Ring<String> recent = new Ring<>(3);
        int revenue = 0;
        int baristaFreeAt = 0;
        int maxWait = 0;
        String slowest = "-";

        while (!queue.isEmpty()) {
            Order o = queue.poll();
            MenuItem item = MENU.get(o.item());
            if (item == null) {
                log.add(o.customer() + ": нет в меню «" + o.item() + "»");
                continue;
            }
            int beans = item instanceof Drink d && d.hot() ? 18 : item instanceof Combo ? 18 : 0;
            if (beans > beansGrams) {
                log.add(o.customer() + ": зёрна закончились");
                continue;
            }
            beansGrams -= beans;
            int start = Math.max(o.minute(), baristaFreeAt);
            int cook = item instanceof Dessert ? 1 : 4;
            baristaFreeAt = start + cook;
            int wait = baristaFreeAt - o.minute();
            if (wait > maxWait) {
                maxWait = wait;
                slowest = o.customer();
            }
            int price = priceOf(item);
            // Счастливый час: с 120-й по 180-ю минуту скидка 10 %
            if (o.minute() >= 120 && o.minute() < 180) {
                price -= price / 10;
            }
            revenue += price;
            served++;
            recent.push(o.customer());
            log.add(String.format("%02d:%02d %s — %s, %d руб., ждал %d мин", 8 + o.minute() / 60, o.minute() % 60,
                    o.customer(), describe(item), price, wait));
        }
        StringBuilder last = new StringBuilder();
        for (String name : recent) {
            last.append(last.length() == 0 ? "" : ", ").append(name);
        }
        log.add("Последние гости: " + last);
        log.add("Дольше всех ждал: " + slowest + " (" + maxWait + " мин)");
        return revenue;
    }

    static List<Order> generateOrders() {
        String[] names = {"Аня", "Боря", "Вика", "Гоша", "Даша", "Егор", "Женя", "Зоя"};
        String[] items = {"латте", "раф", "чизкейк", "завтрак", "лимонад", "печенье", "какао"};
        List<Order> orders = new ArrayList<>();
        int seed = 7;
        int minute = 0;
        for (int i = 0; i < 24; i++) {
            seed = (seed * 31 + 11) & 0xFFFF; // простой детерминированный генератор
            minute += 3 + seed % 17;
            orders.add(new Order(minute, names[(seed >> 5) % names.length], items[(seed >> 3) % items.length]));
        }
        return orders;
    }

    public static void main(String[] args) {
        CoffeeShop shop = new CoffeeShop();
        List<Order> orders = generateOrders();
        int revenue = shop.process(orders);
        shop.log.forEach(System.out::println);

        // Локальный record и локальный класс — подсчёт популярности
        record Count(String item, int times) {}
        class Counter {
            final Map<String, Integer> map = new TreeMap<>();

            void add(String key) {
                map.merge(key, 1, Integer::sum);
            }
        }
        Counter counter = new Counter();
        for (var o : orders) {
            counter.add(o.item());
        }
        List<Count> counts = new ArrayList<>();
        counter.map.forEach((k, v) -> counts.add(new Count(k, v)));
        counts.sort((a, b) -> b.times() != a.times() ? b.times() - a.times() : a.item().compareTo(b.item()));
        System.out.println("Популярность: " + counts);

        double avg = shop.served == 0 ? 0 : (double) revenue / shop.served;
        System.out.print(String.format(REPORT_TEMPLATE, shop.served, revenue, avg, beansGrams));

        // Шифр Цезаря для «секретного» кода дня — арифметика над char
        String secret = "kofe";
        StringBuilder coded = new StringBuilder();
        for (char c : secret.toCharArray()) {
            coded.append((char) ('a' + Math.floorMod(c - 'a' + revenue % 26, 26)));
        }
        System.out.println("Код дня: " + coded + " (" + Integer.toBinaryString(revenue & 0xFF) + ")");
    }
}
