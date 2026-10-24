import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Учебная программа «Библиотека».
 * Меню в цикле на Scanner, выход по команде «0».
 * Книги можно добавлять, выдавать читателям, возвращать и искать.
 */
public class Library {

    // Жанры книг
    enum Genre {
        FICTION("Художественная"),
        SCIENCE("Научная"),
        HISTORY("История"),
        CHILDREN("Детская");

        private final String title;

        Genre(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        static Genre fromNumber(int n) {
            switch (n) {
                case 1:
                    return FICTION;
                case 2:
                    return SCIENCE;
                case 3:
                    return HISTORY;
                default:
                    return CHILDREN;
            }
        }
    }

    // Книга
    static class Book {
        private static int nextId = 1;

        private final int id;
        private final String title;
        private final String author;
        private final int year;
        private final Genre genre;
        private String borrowedBy; // null, если книга на полке

        Book(String title, String author, int year, Genre genre) {
            this.id = nextId++;
            this.title = title;
            this.author = author;
            this.year = year;
            this.genre = genre;
        }

        boolean isAvailable() {
            return borrowedBy == null;
        }

        @Override
        public String toString() {
            String status = isAvailable() ? "на полке" : "у читателя " + borrowedBy;
            return "#" + id + " «" + title + "», " + author + " (" + year + "), " + genre.getTitle() + " — " + status;
        }
    }

    // Читатель
    static class Reader {
        private final String name;
        private final List<Book> books = new ArrayList<>();

        Reader(String name) {
            this.name = name;
        }

        int count() {
            return books.size();
        }
    }

    // Сама библиотека
    static class Catalog {
        private final List<Book> books = new ArrayList<>();
        private final Map<String, Reader> readers = new HashMap<>();
        private static final int MAX_BOOKS_PER_READER = 3;

        void addBook(Book book) {
            books.add(book);
        }

        Book findById(int id) {
            for (Book b : books) {
                if (b.id == id) {
                    return b;
                }
            }
            return null;
        }

        List<Book> search(String text) {
            List<Book> result = new ArrayList<>();
            String needle = text.toLowerCase();
            for (Book b : books) {
                if (b.title.toLowerCase().contains(needle) || b.author.toLowerCase().contains(needle)) {
                    result.add(b);
                }
            }
            return result;
        }

        String borrow(int id, String readerName) {
            Book book = findById(id);
            if (book == null) {
                return "Книга с номером " + id + " не найдена";
            }
            if (!book.isAvailable()) {
                return "Книга уже выдана: " + book.borrowedBy;
            }
            Reader reader = readers.get(readerName);
            if (reader == null) {
                reader = new Reader(readerName);
                readers.put(readerName, reader);
            }
            if (reader.count() >= MAX_BOOKS_PER_READER) {
                return "У читателя " + readerName + " уже " + reader.count() + " книги — больше нельзя";
            }
            book.borrowedBy = readerName;
            reader.books.add(book);
            return "Выдано: " + book.title + " → " + readerName;
        }

        String giveBack(int id) {
            Book book = findById(id);
            if (book == null || book.isAvailable()) {
                return "Эта книга не выдавалась";
            }
            Reader reader = readers.get(book.borrowedBy);
            reader.books.remove(book);
            book.borrowedBy = null;
            return "Возвращено: " + book.title;
        }

        // Статистика по жанрам: сколько книг каждого жанра
        Map<Genre, Integer> byGenre() {
            Map<Genre, Integer> counts = new HashMap<>();
            for (Genre g : Genre.values()) {
                counts.put(g, 0);
            }
            for (Book b : books) {
                counts.put(b.genre, counts.get(b.genre) + 1);
            }
            return counts;
        }

        // Самая старая книга
        Book oldest() {
            Book best = null;
            for (int i = 0; i < books.size(); i++) {
                if (best == null || books.get(i).year < best.year) {
                    best = books.get(i);
                }
            }
            return best;
        }
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("===== БИБЛИОТЕКА =====");
        System.out.println("1. Показать все книги");
        System.out.println("2. Добавить книгу");
        System.out.println("3. Выдать книгу");
        System.out.println("4. Вернуть книгу");
        System.out.println("5. Поиск");
        System.out.println("6. Статистика");
        System.out.println("0. Выход");
        System.out.print("Ваш выбор: ");
    }

    private static int readInt(Scanner sc, String prompt) {
        System.out.print(prompt);
        String line = sc.nextLine().trim();
        try {
            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            System.out.println("Нужно число, а введено: " + line);
            return -1;
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Catalog catalog = new Catalog();
        catalog.addBook(new Book("Война и мир", "Толстой", 1869, Genre.FICTION));
        catalog.addBook(new Book("Краткая история времени", "Хокинг", 1988, Genre.SCIENCE));
        catalog.addBook(new Book("Капитанская дочка", "Пушкин", 1836, Genre.FICTION));
        catalog.addBook(new Book("Маленький принц", "Экзюпери", 1943, Genre.CHILDREN));

        boolean running = true;
        while (running) {
            printMenu();
            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1":
                    for (Book b : catalog.books) {
                        System.out.println(b);
                    }
                    break;
                case "2": {
                    System.out.print("Название: ");
                    String title = sc.nextLine().trim();
                    System.out.print("Автор: ");
                    String author = sc.nextLine().trim();
                    int year = readInt(sc, "Год: ");
                    int g = readInt(sc, "Жанр (1-4): ");
                    if (title.isEmpty() || year < 0) {
                        System.out.println("Книга не добавлена");
                    } else {
                        catalog.addBook(new Book(title, author, year, Genre.fromNumber(g)));
                        System.out.println("Добавлено. Всего книг: " + catalog.books.size());
                    }
                    break;
                }
                case "3": {
                    int id = readInt(sc, "Номер книги: ");
                    System.out.print("Имя читателя: ");
                    String name = sc.nextLine().trim();
                    System.out.println(catalog.borrow(id, name));
                    break;
                }
                case "4":
                    System.out.println(catalog.giveBack(readInt(sc, "Номер книги: ")));
                    break;
                case "5": {
                    System.out.print("Что ищем: ");
                    String text = sc.nextLine();
                    List<Book> found = catalog.search(text);
                    if (found.isEmpty()) {
                        System.out.println("Ничего не найдено");
                    }
                    for (Book b : found) {
                        System.out.println("  " + b);
                    }
                    break;
                }
                case "6": {
                    Map<Genre, Integer> stats = catalog.byGenre();
                    for (Genre g : Genre.values()) {
                        System.out.println(g.getTitle() + ": " + stats.get(g));
                    }
                    Book old = catalog.oldest();
                    System.out.println("Самая старая: " + (old == null ? "-" : old.title));
                    int available = 0;
                    for (Book b : catalog.books) {
                        if (b.isAvailable()) available++;
                    }
                    System.out.printf("На полке %d из %d (%.1f%%)%n", available, catalog.books.size(),
                            100.0 * available / catalog.books.size());
                    break;
                }
                case "0":
                    running = false;
                    System.out.println("До свидания!");
                    break;
                default:
                    System.out.println("Неизвестная команда: " + choice);
            }
        }
    }
}
