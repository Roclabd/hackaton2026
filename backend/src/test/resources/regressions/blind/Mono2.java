import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Расписание поездов: маршруты, остановки, отправления и опоздания.
 * Ввод — команды в консоли, время в формате ЧЧ:ММ, числа через пробел.
 */
public class Mono2 {

    enum TrainType {
        SUBURBAN("Электричка", 60) {
            @Override
            int ticketPrice(int stops) { return 40 + stops * 15; }
        },
        EXPRESS("Экспресс", 120) {
            @Override
            int ticketPrice(int stops) { return 300 + stops * 10; }
        },
        LONG_DISTANCE("Дальнего следования", 90) {
            @Override
            int ticketPrice(int stops) { return 900 + stops * 50; }
        };

        private final String label;
        private final int speedKmh;

        TrainType(String label, int speedKmh) {
            this.label = label;
            this.speedKmh = speedKmh;
        }

        abstract int ticketPrice(int stops);

        String label() { return label; }

        int speedKmh() { return speedKmh; }
    }

    record Time(int hour, int minute) implements Comparable<Time> {
        Time {
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                throw new IllegalArgumentException("Неверное время: " + hour + ":" + minute);
            }
        }

        static Time parse(String raw) {
            String[] hm = raw.split(":");
            if (hm.length != 2) throw new IllegalArgumentException("Время в формате ЧЧ:ММ");
            return new Time(Integer.parseInt(hm[0]), Integer.parseInt(hm[1]));
        }

        int totalMinutes() { return hour * 60 + minute; }

        @Override
        public int compareTo(Time other) { return Integer.compare(totalMinutes(), other.totalMinutes()); }

        @Override
        public String toString() { return String.format("%02d:%02d", hour, minute); }
    }

    record Train(String number, TrainType type, List<String> stops, Time departure, Time arrival, int[] delays) {
        Train {
            if (stops.size() < 2) throw new IllegalArgumentException("Нужно минимум две станции");
            stops = List.copyOf(stops);
        }

        String from() { return stops.getFirst(); }

        String to() { return stops.getLast(); }
    }

    static class ScheduleException extends Exception {
        ScheduleException(String message) { super(message); }
    }

    static class Schedule {
        private final List<Train> trains = new ArrayList<>();
        private final Map<String, Train> byNumber = new HashMap<>();
        private final Map<String, Integer> boardings = new HashMap<>();

        void add(Train t) throws ScheduleException {
            if (byNumber.containsKey(t.number())) throw new ScheduleException("Поезд " + t.number() + " уже есть");
            trains.add(t);
            byNumber.put(t.number(), t);
        }

        Train get(String number) throws ScheduleException {
            Train t = byNumber.get(number);
            if (t == null) throw new ScheduleException("Нет поезда " + number);
            return t;
        }

        /** Поезда, отправляющиеся с указанной станции. */
        List<Train> departingFrom(String station) {
            List<Train> result = new ArrayList<>();
            for (Train t : trains) {
                String from = t.from();
                // ЗАЛОЖЕНО: R7 — строки сравниваются по ссылке; название, введённое с клавиатуры, — другой объект.
                if (from == station) {
                    result.add(t);
                }
            }
            return result;
        }

        /** Поезда, прибывающие на станцию, — правильное сравнение. */
        List<Train> arrivingTo(String station) {
            return trains.stream().filter(t -> t.to().equalsIgnoreCase(station)).toList();
        }

        /** Все станции сети без повторов, в порядке появления. */
        List<String> allStations() {
            List<String> stations = new ArrayList<>();
            for (Train t : trains) {
                for (String s : t.stops()) {
                    // ЗАЛОЖЕНО: R10 — contains у списка внутри цикла: на каждую остановку просматриваются все станции.
                    if (!stations.contains(s)) {
                        stations.add(s);
                    }
                }
            }
            return stations;
        }

        /** То же самое, но через множество — используется в поиске маршрута. */
        Set<String> stationSet() {
            Set<String> set = new LinkedHashSet<>();
            for (Train t : trains) {
                set.addAll(t.stops());
            }
            return set;
        }

        /** Суммарное опоздание поезда по всем станциям. */
        int totalDelay(Train t) {
            int[] delays = t.delays();
            int total = 0;
            // ЗАЛОЖЕНО: R2 — граница включает delays.length, последняя итерация выходит за массив.
            for (int i = 0; i <= delays.length; i++) {
                total += delays[i];
            }
            return total;
        }

        /** Наибольшее опоздание поезда. */
        int worstDelay(Train t) {
            int[] delays = t.delays();
            if (delays.length == 0) return 0;
            int maxDelay = delays[0];
            for (int d : delays) {
                // ЗАЛОЖЕНО: R4 — сравнение направлено обратно, maxDelay сохраняет меньшее значение.
                if (d < maxDelay) maxDelay = d;
            }
            return maxDelay;
        }

        /** Наименьшее опоздание — правильно. */
        int bestDelay(Train t) {
            int min = Integer.MAX_VALUE;
            for (int d : t.delays()) {
                if (d < min) min = d;
            }
            return min == Integer.MAX_VALUE ? 0 : min;
        }

        /** Среднее опоздание по станциям, в минутах. */
        double averageDelay(Train t) {
            int[] delays = t.delays();
            if (delays.length == 0) return 0;
            int sum = 0;
            for (int i = 0; i < delays.length; i++) {
                sum += delays[i];
            }
            return (double) sum / delays.length;
        }

        /** Сколько минут поезд в пути. */
        int travelMinutes(Train t) {
            // ЗАЛОЖЕНО-НЕ-ШАБЛОН: неверная формула — ночной поезд (23:10 → 06:40) даёт отрицательное время, нет перехода через полночь.
            return t.arrival().totalMinutes() - t.departure().totalMinutes();
        }

        /** Сколько промежуточных остановок между двумя станциями маршрута. */
        int stopsBetween(Train t, String a, String b) throws ScheduleException {
            int i = t.stops().indexOf(a);
            int j = t.stops().indexOf(b);
            if (i < 0 || j < 0) throw new ScheduleException("Станции нет в маршруте " + t.number());
            // ЗАЛОЖЕНО-НЕ-ШАБЛОН: ошибка на единицу — промежуточных остановок |j - i| - 1, а возвращается |j - i|.
            return Math.abs(j - i);
        }

        int price(Train t, String a, String b) throws ScheduleException {
            return t.type().ticketPrice(stopsBetween(t, a, b));
        }

        /** Прямые поезда между станциями, отсортированные по времени отправления. */
        List<Train> direct(String a, String b) {
            List<Train> result = new ArrayList<>();
            for (Train t : trains) {
                int i = t.stops().indexOf(a);
                int j = t.stops().indexOf(b);
                if (i >= 0 && j > i) result.add(t);
            }
            result.sort(Comparator.comparing(Train::departure));
            return result;
        }

        /** Ближайший поезд после указанного времени. */
        Optional<Train> nextAfter(Time now, Predicate<Train> filter) {
            Train best = null;
            for (Train t : trains) {
                if (!filter.test(t) || t.departure().compareTo(now) < 0) continue;
                if (best == null || t.departure().compareTo(best.departure()) < 0) best = t;
            }
            return Optional.ofNullable(best);
        }

        /** Самое раннее отправление в минутах от полуночи. */
        int earliestDeparture() {
            int earliest = Integer.MAX_VALUE;
            for (Train t : trains) {
                int m = t.departure().totalMinutes();
                if (m < earliest) earliest = m;
            }
            return earliest;
        }

        void board(String number, int passengers) throws ScheduleException {
            get(number);
            boardings.merge(number, passengers, Integer::sum);
        }

        int boarded(String number) {
            if (!boardings.containsKey(number)) return 0;
            int n = boardings.get(number);
            return n;
        }

        /** Таблица отправлений. */
        String board() {
            StringBuilder sb = new StringBuilder();
            int width = 0;
            for (Train t : trains) {
                if (t.number().length() > width) width = t.number().length();
            }
            List<Train> sorted = new ArrayList<>(trains);
            sorted.sort(Comparator.comparing(Train::departure).thenComparing(Train::number));
            for (Train t : sorted) {
                sb.append(String.format("%-" + Math.max(width, 1) + "s  %s  %s → %s  (%s)%n",
                        t.number(), t.departure(), t.from(), t.to(), t.type().label()));
            }
            return sb.toString();
        }

        Map<TrainType, Long> countByType() {
            return trains.stream().collect(Collectors.groupingBy(Train::type, Collectors.counting()));
        }

        /** Средняя скорость в км/ч при известном расстоянии. */
        double averageSpeed(Train t, int km) {
            int minutes = travelMinutes(t);
            if (minutes <= 0) return 0;
            return km * 60.0 / minutes;
        }

        /** Сколько полных часов в пути — нужен именно целый результат. */
        int fullHours(Train t) {
            int hours = travelMinutes(t) / 60;
            return hours;
        }

        List<Train> trains() { return trains; }
    }

    static List<String> parseStops(String raw) {
        List<String> stops = new ArrayList<>();
        for (String s : raw.split(",")) {
            String name = s.trim();
            if (!name.isEmpty()) stops.add(name);
        }
        return stops;
    }

    static int[] parseDelays(String[] parts, int from) {
        int[] delays = new int[parts.length - from];
        for (int i = from; i < parts.length; i++) {
            delays[i - from] = Integer.parseInt(parts[i]);
        }
        return delays;
    }

    static void help() {
        System.out.println("add <номер> <тип> <станции,через,запятую> <отпр ЧЧ:ММ> <приб ЧЧ:ММ> [опоздания...]");
        System.out.println("from <станция> | to <станция> | direct <A> <B> | next <ЧЧ:ММ> [тип]");
        System.out.println("delay <номер> | price <номер> <A> <B> | trip <номер> [км]");
        System.out.println("board <номер> <пассажиры> | boarded <номер>");
        System.out.println("stations | table | types | help | exit");
    }

    public static void main(String[] args) {
        Schedule schedule = new Schedule();
        Scanner in = new Scanner(System.in);
        Set<String> knownCommands = new HashSet<>(List.of("add", "from", "to", "direct", "next", "delay",
                "price", "trip", "board", "boarded", "stations", "table", "types", "help", "exit"));
        help();
        while (in.hasNextLine()) {
            String line = in.nextLine().trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+");
            String cmd = p[0].toLowerCase();
            if (!knownCommands.contains(cmd)) {
                System.out.println("Неизвестная команда. help — список команд");
                continue;
            }
            try {
                switch (cmd) {
                    case "add" -> {
                        if (p.length < 6) throw new ScheduleException("Недостаточно аргументов");
                        TrainType type = TrainType.valueOf(p[2].toUpperCase());
                        Train t = new Train(p[1], type, parseStops(p[3]), Time.parse(p[4]), Time.parse(p[5]),
                                parseDelays(p, 6));
                        schedule.add(t);
                        System.out.println("Добавлен " + t.number() + ": " + t.from() + " → " + t.to());
                    }
                    case "from" -> schedule.departingFrom(p[1]).forEach(t -> System.out.println(t.number() + " " + t.departure()));
                    case "to" -> schedule.arrivingTo(p[1]).forEach(t -> System.out.println(t.number() + " " + t.arrival()));
                    case "direct" -> schedule.direct(p[1], p[2]).forEach(t -> System.out.println(t.number() + " " + t.departure()));
                    case "next" -> {
                        Time now = Time.parse(p[1]);
                        Predicate<Train> filter = p.length > 2
                                ? t -> t.type().name().equalsIgnoreCase(p[2])
                                : t -> true;
                        System.out.println(schedule.nextAfter(now, filter).map(t -> t.number() + " в " + t.departure())
                                .orElse("Сегодня поездов больше нет"));
                    }
                    case "delay" -> {
                        Train t = schedule.get(p[1]);
                        System.out.printf("Всего %d мин, худшее %d, лучшее %d, среднее %.1f%n",
                                schedule.totalDelay(t), schedule.worstDelay(t), schedule.bestDelay(t), schedule.averageDelay(t));
                    }
                    case "price" -> {
                        Train t = schedule.get(p[1]);
                        System.out.println("Остановок: " + schedule.stopsBetween(t, p[2], p[3])
                                + ", цена " + schedule.price(t, p[2], p[3]) + " руб.");
                    }
                    case "trip" -> {
                        Train t = schedule.get(p[1]);
                        int km = p.length > 2 ? Integer.parseInt(p[2]) : t.type().speedKmh();
                        System.out.printf("В пути %d мин (%d ч), средняя скорость %.1f км/ч%n",
                                schedule.travelMinutes(t), schedule.fullHours(t), schedule.averageSpeed(t, km));
                    }
                    case "board" -> {
                        schedule.board(p[1], Integer.parseInt(p[2]));
                        System.out.println("Посадка учтена");
                    }
                    case "boarded" -> System.out.println(schedule.boarded(p[1]));
                    case "stations" -> System.out.println(String.join(", ", schedule.allStations()));
                    case "table" -> System.out.print(schedule.board());
                    case "types" -> schedule.countByType().forEach((k, v) -> System.out.println(k.label() + ": " + v));
                    case "help" -> help();
                    case "exit" -> {
                        return;
                    }
                    default -> System.out.println("?");
                }
            } catch (ScheduleException e) {
                System.out.println("Ошибка расписания: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                System.out.println("Ошибка ввода: " + e.getMessage());
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println("Не хватает аргументов команды " + cmd);
            }
        }
        System.out.println("Ввод закончился. Станций в сети: " + schedule.stationSet().size()
                + ", самое раннее отправление: " + (schedule.trains().isEmpty() ? "—" : schedule.earliestDeparture() + " мин"));
    }
}
