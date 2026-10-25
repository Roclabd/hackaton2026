import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Фильмотека: учёт фильмов, режиссёров, оценок и списков «к просмотру».
 * Команды вводятся по одной в строке, аргументы — через пробел.
 */
public class Mono1 {

    enum Genre {
        DRAMA("Драма") {
            @Override
            int recommendedAge() { return 12; }
        },
        COMEDY("Комедия") {
            @Override
            int recommendedAge() { return 6; }
        },
        THRILLER("Триллер") {
            @Override
            int recommendedAge() { return 16; }
        },
        ANIMATION("Мультфильм") {
            @Override
            int recommendedAge() { return 0; }
        },
        DOCUMENTARY("Документальный") {
            @Override
            int recommendedAge() { return 0; }
        };

        private final String title;

        Genre(String title) {
            this.title = title;
        }

        abstract int recommendedAge();

        String title() {
            return title;
        }

        static Optional<Genre> parse(String raw) {
            for (Genre g : values()) {
                if (g.name().equalsIgnoreCase(raw) || g.title.equalsIgnoreCase(raw)) {
                    return Optional.of(g);
                }
            }
            return Optional.empty();
        }
    }

    record Director(int id, String name, String country) { }

    record Movie(int id, String title, int year, int minutes, Genre genre, int directorId, List<String> tags) {
        Movie {
            if (minutes <= 0) throw new IllegalArgumentException("Длительность должна быть положительной");
            tags = List.copyOf(tags);
        }
    }

    record Rating(int movieId, String user, int stars) { }

    static class LibraryException extends RuntimeException {
        LibraryException(String message) {
            super(message);
        }
    }

    static class Library {
        private final List<Movie> movies = new ArrayList<>();
        private final List<Director> directors = new ArrayList<>();
        private final List<Rating> ratings = new ArrayList<>();
        private final Map<String, Integer> genreCounts = new HashMap<>();
        private final Map<String, Set<Integer>> watchlists = new HashMap<>();
        private int nextMovieId = 1;
        private int nextDirectorId = 1;

        Director addDirector(String name, String country) {
            Director d = new Director(nextDirectorId++, name, country);
            directors.add(d);
            return d;
        }

        Movie addMovie(String title, int year, int minutes, Genre genre, int directorId, List<String> tags) {
            if (findDirector(directorId).isEmpty()) {
                throw new LibraryException("Нет режиссёра с id " + directorId);
            }
            Movie m = new Movie(nextMovieId++, title, year, minutes, genre, directorId, tags);
            movies.add(m);
            genreCounts.merge(genre.name(), 1, Integer::sum);
            return m;
        }

        Optional<Director> findDirector(int id) {
            return directors.stream().filter(d -> d.id() == id).findFirst();
        }

        Optional<Movie> findMovie(int id) {
            return movies.stream().filter(m -> m.id() == id).findFirst();
        }

        void rate(int movieId, String user, int stars) {
            if (stars < 1 || stars > 5) throw new LibraryException("Оценка от 1 до 5");
            findMovie(movieId).orElseThrow(() -> new LibraryException("Нет фильма " + movieId));
            ratings.removeIf(r -> r.movieId() == movieId && r.user().equals(user));
            ratings.add(new Rating(movieId, user, stars));
        }

        /** Сколько фильмов в жанре. */
        int genreCount(String genre) {
            // ЗАЛОЖЕНО: R13 — для жанра без фильмов get вернёт null, распаковка в int бросит NullPointerException.
            int count = genreCounts.get(genre);
            return count;
        }

        /** Безопасный вариант для отчёта — по всем жанрам. */
        Map<Genre, Integer> genreTable() {
            Map<Genre, Integer> table = new LinkedHashMap<>();
            for (Genre g : Genre.values()) {
                table.put(g, genreCounts.getOrDefault(g.name(), 0));
            }
            return table;
        }

        /** Фильмы с именами режиссёров. */
        List<String> moviesWithDirectors() {
            List<String> lines = new ArrayList<>();
            for (Movie m : movies) {
                // ЗАЛОЖЕНО: R1 — для каждого фильма перебирается весь список режиссёров; нужен индекс Map<id, Director>.
                for (Director d : directors) {
                    if (m.directorId() == d.id()) {
                        lines.add(m.title() + " (" + m.year() + ") — " + d.name());
                    }
                }
            }
            return lines;
        }

        /** Средняя оценка фильма. */
        double averageRating(int movieId) {
            int totalStars = 0;
            int ratedCount = 0;
            for (Rating r : ratings) {
                if (r.movieId() == movieId) {
                    totalStars += r.stars();
                    ratedCount++;
                }
            }
            if (ratedCount == 0) return 0;
            // ЗАЛОЖЕНО: R8 — оба операнда int, дробная часть теряется до расширения в double (4 и 5 дают 4.0, а не 4.5).
            double avg = totalStars / ratedCount;
            return avg;
        }

        /** Средняя оценка по всем фильмам режиссёра — правильная версия. */
        double directorScore(int directorId) {
            Set<Integer> ids = movies.stream().filter(m -> m.directorId() == directorId)
                    .map(Movie::id).collect(Collectors.toSet());
            int sum = 0;
            int n = 0;
            for (Rating r : ratings) {
                if (ids.contains(r.movieId())) {
                    sum += r.stars();
                    n++;
                }
            }
            return n == 0 ? 0.0 : (double) sum / n;
        }

        /** Топ-N фильмов по средней оценке. */
        List<Movie> top(int n) {
            Map<Integer, Double> score = new HashMap<>();
            for (Movie m : movies) {
                score.put(m.id(), directorScoreOfMovie(m));
            }
            return movies.stream()
                    .sorted(Comparator.comparingDouble((Movie m) -> score.get(m.id())).reversed())
                    // ЗАЛОЖЕНО-НЕ-ШАБЛОН: ошибка на единицу — пользователь просит N фильмов, а выводится N - 1.
                    .limit(Math.max(0, n - 1))
                    .toList();
        }

        private double directorScoreOfMovie(Movie m) {
            double sum = 0;
            int n = 0;
            for (Rating r : ratings) {
                if (r.movieId() == m.id()) {
                    sum += r.stars();
                    n++;
                }
            }
            return n == 0 ? 0 : sum / n;
        }

        /** Самый длинный фильм в жанре. */
        Optional<Movie> longest(Genre genre) {
            Movie best = null;
            int longestMinutes = Integer.MIN_VALUE;
            for (Movie m : movies) {
                if (m.genre() == genre && m.minutes() > longestMinutes) {
                    longestMinutes = m.minutes();
                    best = m;
                }
            }
            return Optional.ofNullable(best);
        }

        /** Самое длинное название — длина не бывает отрицательной. */
        int longestTitle() {
            int maxLen = 0;
            for (Movie m : movies) {
                if (m.title().length() > maxLen) maxLen = m.title().length();
            }
            return maxLen;
        }

        /** Все теги без повторов, в порядке появления. */
        List<String> allTags() {
            Set<String> seen = new HashSet<>();
            List<String> result = new ArrayList<>();
            for (Movie m : movies) {
                for (String tag : m.tags()) {
                    if (seen.add(tag)) {
                        result.add(tag);
                    }
                }
            }
            return result;
        }

        /** Фильмы с тегом — внутренний цикл по тегам самого фильма. */
        List<Movie> withTag(String wanted) {
            List<Movie> result = new ArrayList<>();
            for (Movie m : movies) {
                for (String tag : m.tags()) {
                    if (tag.equalsIgnoreCase(wanted)) {
                        result.add(m);
                        break;
                    }
                }
            }
            return result;
        }

        void addToWatchlist(String user, int movieId) {
            findMovie(movieId).orElseThrow(() -> new LibraryException("Нет фильма " + movieId));
            watchlists.computeIfAbsent(user, u -> new HashSet<>()).add(movieId);
        }

        List<Movie> watchlist(String user) {
            Set<Integer> ids = watchlists.getOrDefault(user, Set.of());
            List<Movie> result = new ArrayList<>();
            for (Movie m : movies) {
                if (ids.contains(m.id())) result.add(m);
            }
            result.sort(Comparator.comparing(Movie::title));
            return result;
        }

        /** Экспорт каталога одной строкой для сохранения. */
        String exportCatalog() {
            String report = "";
            // ЗАЛОЖЕНО: R9 — строка склеивается в цикле, каждый += копирует всё накопленное.
            for (Movie m : movies) {
                report += m.id() + ";" + m.title() + ";" + m.year() + ";" + m.genre().name() + "\n";
            }
            return report;
        }

        /** Отчёт по режиссёрам — уже через StringBuilder и индекс. */
        String directorReport() {
            Map<Integer, List<Movie>> byDirector = movies.stream()
                    .collect(Collectors.groupingBy(Movie::directorId, TreeMap::new, Collectors.toList()));
            StringBuilder sb = new StringBuilder();
            for (Director d : directors) {
                List<Movie> own = byDirector.getOrDefault(d.id(), List.of());
                sb.append(d.name()).append(" [").append(d.country()).append("]: ").append(own.size()).append('\n');
            }
            return sb.toString();
        }

        /** Сколько страниц займёт список при выводе по pageSize. */
        int pages(int pageSize) {
            int full = movies.size() / pageSize;
            return movies.size() % pageSize == 0 ? full : full + 1;
        }

        /** Доля жанра в процентах. */
        double genreShare(Genre g) {
            if (movies.isEmpty()) return 0;
            int part = genreCounts.getOrDefault(g.name(), 0);
            return 100.0 * part / movies.size();
        }

        List<Movie> page(int number, int pageSize) {
            int from = (number - 1) * pageSize;
            if (from < 0 || from >= movies.size()) return List.of();
            return movies.subList(from, Math.min(from + pageSize, movies.size()));
        }

        List<Movie> movies() {
            return movies;
        }
    }

    /** Длительность в виде «2 ч 15 мин». */
    static String formatDuration(int minutes) {
        int hours = minutes / 60;
        // ЗАЛОЖЕНО-НЕ-ШАБЛОН: неверная формула — остаток минут считается по модулю 24, а не 60 (135 мин → «2 ч 15» не получится).
        int rest = minutes % 24;
        return hours + " ч " + rest + " мин";
    }

    static int parseInt(String raw, String what) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new LibraryException("Ожидалось число для «" + what + "», получено: " + raw);
        }
    }

    static List<String> parseTags(String[] parts, int from) {
        List<String> tags = new ArrayList<>();
        for (int i = from; i < parts.length; i++) {
            tags.add(parts[i].toLowerCase());
        }
        return tags;
    }

    static void printHelp() {
        System.out.println("Команды:");
        System.out.println("  director <имя> <страна>");
        System.out.println("  movie <название> <год> <минуты> <жанр> <idРежиссёра> [теги...]");
        System.out.println("  rate <idФильма> <пользователь> <1-5>");
        System.out.println("  list [страница]");
        System.out.println("  top <N> | genre <жанр> | genres | tags | tag <тег>");
        System.out.println("  longest <жанр> | avg <idФильма> | dscore <idРежиссёра>");
        System.out.println("  watch <пользователь> <idФильма> | watchlist <пользователь>");
        System.out.println("  export | directors | help | exit");
    }

    public static void main(String[] args) {
        Library lib = new Library();
        Scanner in = new Scanner(System.in);
        printHelp();
        while (true) {
            System.out.print("> ");
            if (!in.hasNextLine()) break;
            String line = in.nextLine().trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase();
            try {
                switch (cmd) {
                    case "director" -> {
                        if (parts.length < 3) throw new LibraryException("director <имя> <страна>");
                        Director d = lib.addDirector(parts[1], parts[2]);
                        System.out.println("Режиссёр #" + d.id() + " добавлен");
                    }
                    case "movie" -> {
                        if (parts.length < 6) throw new LibraryException("movie <название> <год> <минуты> <жанр> <idРежиссёра>");
                        Genre g = Genre.parse(parts[4]).orElseThrow(() -> new LibraryException("Неизвестный жанр"));
                        Movie m = lib.addMovie(parts[1].replace('_', ' '), parseInt(parts[2], "год"),
                                parseInt(parts[3], "минуты"), g, parseInt(parts[5], "режиссёр"), parseTags(parts, 6));
                        System.out.println("Фильм #" + m.id() + " «" + m.title() + "», " + formatDuration(m.minutes())
                                + ", от " + g.recommendedAge() + "+");
                    }
                    case "rate" -> {
                        if (parts.length < 4) throw new LibraryException("rate <id> <пользователь> <1-5>");
                        lib.rate(parseInt(parts[1], "id"), parts[2], parseInt(parts[3], "оценка"));
                        System.out.println("Оценка сохранена");
                    }
                    case "list" -> {
                        int number = parts.length > 1 ? parseInt(parts[1], "страница") : 1;
                        lib.page(number, 5).forEach(m -> System.out.println("#" + m.id() + " " + m.title()));
                        System.out.println("Страниц: " + lib.pages(5));
                    }
                    case "top" -> {
                        int n = parts.length > 1 ? parseInt(parts[1], "N") : 3;
                        lib.top(n).forEach(m -> System.out.printf("%s — %.2f%n", m.title(), lib.averageRating(m.id())));
                    }
                    case "genre" -> {
                        if (parts.length < 2) throw new LibraryException("genre <жанр>");
                        Genre g = Genre.parse(parts[1]).orElseThrow(() -> new LibraryException("Неизвестный жанр"));
                        System.out.println(g.title() + ": " + lib.genreCount(g.name()) + " фильм(ов), "
                                + String.format("%.1f%%", lib.genreShare(g)));
                    }
                    case "genres" -> lib.genreTable().forEach((g, c) -> System.out.println(g.title() + " — " + c));
                    case "tags" -> System.out.println(String.join(", ", lib.allTags()));
                    case "tag" -> {
                        if (parts.length < 2) throw new LibraryException("tag <тег>");
                        lib.withTag(parts[1]).forEach(m -> System.out.println(m.title()));
                    }
                    case "longest" -> {
                        if (parts.length < 2) throw new LibraryException("longest <жанр>");
                        Genre g = Genre.parse(parts[1]).orElseThrow(() -> new LibraryException("Неизвестный жанр"));
                        System.out.println(lib.longest(g).map(m -> m.title() + ", " + formatDuration(m.minutes()))
                                .orElse("Нет фильмов"));
                    }
                    case "avg" -> System.out.printf("%.2f%n", lib.averageRating(parseInt(parts[1], "id")));
                    case "dscore" -> System.out.printf("%.2f%n", lib.directorScore(parseInt(parts[1], "id")));
                    case "watch" -> {
                        lib.addToWatchlist(parts[1], parseInt(parts[2], "id"));
                        System.out.println("Добавлено");
                    }
                    case "watchlist" -> lib.watchlist(parts[1]).forEach(m -> System.out.println(m.title()));
                    case "export" -> System.out.print(lib.exportCatalog());
                    case "directors" -> {
                        System.out.print(lib.directorReport());
                        lib.moviesWithDirectors().forEach(System.out::println);
                    }
                    case "titles" -> System.out.println("Самое длинное название: " + lib.longestTitle());
                    case "help" -> printHelp();
                    case "exit" -> {
                        System.out.println("До свидания");
                        return;
                    }
                    default -> System.out.println("Неизвестная команда: " + cmd);
                }
            } catch (LibraryException | IllegalArgumentException e) {
                System.out.println("Ошибка: " + e.getMessage());
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println("Ошибка: не хватает аргументов");
            }
        }
    }
}
