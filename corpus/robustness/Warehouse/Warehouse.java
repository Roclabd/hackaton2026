import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.TreeMap;

/**
 * Учёт товаров на складе.
 * Команды вводятся словами: add, remove, move, list, report, find, exit.
 * Пример: «add Молоко FOOD 10 89.90»
 */
public class Warehouse {

    enum Category {
        FOOD("Продукты", 0.10),
        TOOLS("Инструменты", 0.20),
        CLOTHES("Одежда", 0.20),
        BOOKS("Книги", 0.0);

        final String russian;
        final double vat;

        Category(String russian, double vat) {
            this.russian = russian;
            this.vat = vat;
        }

        static Optional<Category> parse(String text) {
            for (Category c : values()) {
                if (c.name().equalsIgnoreCase(text) || c.russian.equalsIgnoreCase(text)) {
                    return Optional.of(c);
                }
            }
            return Optional.empty();
        }
    }

    // Товар — неизменяемая запись; при изменении количества создаём новую
    record Item(String name, Category category, int quantity, double price) {
        Item {
            if (quantity < 0) {
                throw new IllegalArgumentException("Количество не может быть отрицательным: " + quantity);
            }
            name = name.trim();
        }

        Item withQuantity(int q) {
            return new Item(name, category, q, price);
        }

        double total() {
            return quantity * price;
        }

        double totalWithVat() {
            return total() * (1 + category.vat);
        }
    }

    // Запись журнала операций
    record Operation(int number, String kind, String item, int delta) {
        @Override
        public String toString() {
            return String.format("%3d. %-7s %-12s %+d", number, kind, item, delta);
        }
    }

    static class Stock {
        private final Map<String, Item> items = new LinkedHashMap<>();
        private final List<Operation> journal = new ArrayList<>();
        private int counter = 0;

        String add(String name, Category category, int quantity, double price) {
            Item existing = items.get(name);
            Item updated;
            if (existing == null) {
                updated = new Item(name, category, quantity, price);
            } else {
                updated = existing.withQuantity(existing.quantity() + quantity);
            }
            items.put(name, updated);
            journal.add(new Operation(++counter, "приход", name, quantity));
            return "Принято: " + name + ", теперь " + updated.quantity() + " шт.";
        }

        String remove(String name, int quantity) {
            Item existing = items.get(name);
            if (existing == null) {
                return "Нет такого товара: " + name;
            }
            int left = existing.quantity() - quantity;
            if (left < 0) {
                return "Недостаточно: есть " + existing.quantity() + ", просят " + quantity;
            }
            if (left == 0) {
                items.remove(name);
            } else {
                items.put(name, existing.withQuantity(left));
            }
            journal.add(new Operation(++counter, "расход", name, -quantity));
            return "Отгружено " + quantity + " шт. товара " + name;
        }

        List<Item> sortedByValue() {
            List<Item> list = new ArrayList<>(items.values());
            list.sort(Comparator.comparingDouble(Item::total).reversed().thenComparing(Item::name));
            return list;
        }

        Map<Category, Double> valueByCategory() {
            Map<Category, Double> result = new EnumMap<>(Category.class);
            for (Item item : items.values()) {
                result.merge(item.category(), item.totalWithVat(), Double::sum);
            }
            return result;
        }

        // Товары, которых мало (меньше порога)
        List<String> lowStock(int threshold) {
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, Item> e : items.entrySet()) {
                if (e.getValue().quantity() < threshold) {
                    names.add(e.getKey());
                }
            }
            return names;
        }
    }

    private static String handle(Stock stock, String line) {
        String[] parts = line.trim().split("\\s+");
        String command = parts[0].toLowerCase();
        try {
            return switch (command) {
                case "add" -> {
                    if (parts.length < 5) {
                        yield "Формат: add <название> <категория> <кол-во> <цена>";
                    }
                    Optional<Category> category = Category.parse(parts[2]);
                    if (category.isEmpty()) {
                        yield "Неизвестная категория: " + parts[2];
                    }
                    yield stock.add(parts[1], category.get(), Integer.parseInt(parts[3]), Double.parseDouble(parts[4]));
                }
                case "remove" -> parts.length < 3 ? "Формат: remove <название> <кол-во>"
                        : stock.remove(parts[1], Integer.parseInt(parts[2]));
                case "list" -> {
                    StringBuilder sb = new StringBuilder();
                    for (Item item : stock.sortedByValue()) {
                        sb.append(String.format("%-12s %-12s %5d x %8.2f = %10.2f%n",
                                item.name(), item.category().russian, item.quantity(), item.price(), item.total()));
                    }
                    yield sb.length() == 0 ? "Склад пуст" : sb.toString().stripTrailing();
                }
                case "report" -> {
                    Map<Category, Double> byCat = stock.valueByCategory();
                    double sum = 0;
                    StringBuilder sb = new StringBuilder("Стоимость с НДС:\n");
                    for (var e : byCat.entrySet()) {
                        sb.append("  ").append(e.getKey().russian).append(": ")
                                .append(String.format("%.2f", e.getValue())).append('\n');
                        sum += e.getValue();
                    }
                    sb.append("Итого: ").append(String.format("%.2f", sum)).append('\n');
                    sb.append("Мало на складе: ").append(stock.lowStock(5));
                    yield sb.toString();
                }
                case "journal" -> {
                    StringBuilder sb = new StringBuilder();
                    stock.journal.forEach(op -> sb.append(op).append('\n'));
                    yield sb.toString().stripTrailing();
                }
                case "find" -> {
                    if (parts.length < 2) {
                        yield "Формат: find <часть названия>";
                    }
                    Map<String, Integer> found = new TreeMap<>();
                    for (Item item : stock.items.values()) {
                        if (item.name().toLowerCase().contains(parts[1].toLowerCase())) {
                            found.put(item.name(), item.quantity());
                        }
                    }
                    yield found.isEmpty() ? "Не найдено" : found.toString();
                }
                default -> "Неизвестная команда «" + command + "». Команды: add, remove, list, report, journal, find, exit";
            };
        } catch (NumberFormatException e) {
            return "Ошибка числа: " + e.getMessage();
        } catch (IllegalArgumentException e) {
            return "Ошибка: " + e.getMessage();
        }
    }

    public static void main(String[] args) {
        Stock stock = new Stock();
        stock.add("Молоко", Category.FOOD, 20, 89.90);
        stock.add("Молоток", Category.TOOLS, 4, 550.0);
        stock.add("Куртка", Category.CLOTHES, 7, 4999.0);

        Scanner in = new Scanner(System.in);
        System.out.println("Склад готов. Введите команду (exit — выход):");
        int processed = 0;
        while (in.hasNextLine()) {
            String line = in.nextLine();
            if (line.isBlank()) {
                continue;
            }
            if (line.trim().equalsIgnoreCase("exit")) {
                break;
            }
            System.out.println("> " + handle(stock, line));
            processed++;
        }
        System.out.println("Обработано команд: " + processed);
        System.out.println("Позиций на складе: " + stock.items.size());
    }
}
