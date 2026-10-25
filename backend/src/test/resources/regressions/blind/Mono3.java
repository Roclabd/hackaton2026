import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Учёт спортзала: клиенты, абонементы, тренеры, посещения и замеры веса.
 * Консольное меню: команда и аргументы через пробел, даты в формате ГГГГ-ММ-ДД.
 */
public class Mono3 {

    enum Plan {
        MORNING("Утренний", 30) {
            @Override
            boolean allows(int hour) { return hour >= 7 && hour < 12; }
        },
        STANDARD("Стандарт", 30) {
            @Override
            boolean allows(int hour) { return hour >= 7 && hour < 22; }
        },
        UNLIMITED("Безлимит", 90) {
            @Override
            boolean allows(int hour) { return true; }
        };

        private final String label;
        private final int days;

        Plan(String label, int days) {
            this.label = label;
            this.days = days;
        }

        abstract boolean allows(int hour);

        String label() { return label; }

        int days() { return days; }
    }

    record Trainer(Integer id, String name, Set<String> skills) { }

    record Member(Integer id, String name, Plan plan, LocalDate start, Integer trainerId, int heightCm) {
        /** Последний день, когда абонемент ещё действует (включительно). */
        LocalDate expires() { return start.plusDays(plan.days() - 1); }
    }

    record Visit(int memberId, LocalDate date, int hour, int minutes) { }

    record Weighing(int memberId, LocalDate date, int weightKg) { }

    static class GymException extends RuntimeException {
        GymException(String message) { super(message); }
    }

    static class Gym {
        private final Map<Integer, Member> members = new TreeMap<>();
        private final Map<Integer, Trainer> trainers = new HashMap<>();
        private final List<Visit> visits = new ArrayList<>();
        private final List<Weighing> weighings = new ArrayList<>();
        private int nextMemberId = 1;
        private int nextTrainerId = 200;

        Trainer hire(String name, Set<String> skills) {
            Trainer t = new Trainer(nextTrainerId++, name, skills);
            trainers.put(t.id(), t);
            return t;
        }

        Member enroll(String name, Plan plan, LocalDate start, Integer trainerId, int heightCm) {
            if (trainerId != null && !trainers.containsKey(trainerId)) throw new GymException("Нет тренера " + trainerId);
            Member m = new Member(nextMemberId++, name, plan, start, trainerId, heightCm);
            members.put(m.id(), m);
            return m;
        }

        Member member(int id) {
            Member m = members.get(id);
            if (m == null) throw new GymException("Нет клиента " + id);
            return m;
        }

        /** Действует ли абонемент в этот день. */
        boolean isActive(Member m, LocalDate day) {
            // ЗАЛОЖЕНО-НЕ-ШАБЛОН: ошибка на единицу — expires() включительный, а isBefore отрезает последний оплаченный день.
            return !day.isBefore(m.start()) && day.isBefore(m.expires());
        }

        long daysLeft(Member m, LocalDate today) {
            long left = ChronoUnit.DAYS.between(today, m.expires()) + 1;
            return Math.max(0, left);
        }

        void checkIn(int memberId, LocalDate date, int hour, int minutes) {
            Member m = member(memberId);
            if (!isActive(m, date)) throw new GymException("Абонемент не действует");
            if (!m.plan().allows(hour)) throw new GymException("Тариф «" + m.plan().label() + "» не пускает в " + hour + " ч");
            visits.add(new Visit(memberId, date, hour, minutes));
        }

        /** Загрузка посещений из пакета (импорт с турникета). */
        int importVisits(List<Visit> batch) {
            int accepted = 0;
            for (Visit v : batch) {
                if (!members.containsKey(v.memberId())) continue;
                visits.add(v);
                // ЗАЛОЖЕНО: R11 — сортировка всего журнала на каждой итерации; достаточно одной после цикла.
                visits.sort(Comparator.comparing(Visit::date).thenComparingInt(Visit::hour));
                accepted++;
            }
            return accepted;
        }

        /** Импорт замеров — сортировка один раз после цикла. */
        int importWeighings(List<Weighing> batch) {
            int accepted = 0;
            for (Weighing w : batch) {
                if (members.containsKey(w.memberId()) && w.weightKg() > 0) {
                    weighings.add(w);
                    accepted++;
                }
            }
            weighings.sort(Comparator.comparing(Weighing::date));
            return accepted;
        }

        /** Клиенты конкретного тренера. */
        List<Member> clientsOf(int trainerKey) {
            Trainer trainer = trainers.get(trainerKey);
            if (trainer == null) throw new GymException("Нет тренера " + trainerKey);
            List<Member> result = new ArrayList<>();
            for (Member m : members.values()) {
                Integer assigned = m.trainerId();
                Integer wanted = trainer.id();
                // ЗАЛОЖЕНО: R15 — == между двумя Integer сравнивает ссылки; id тренеров от 200 вне кэша -128..127.
                if (assigned == wanted) {
                    result.add(m);
                }
            }
            return result;
        }

        /** Тот же поиск, но через equals — используется в отчёте. */
        long clientCount(Integer trainerKey) {
            return members.values().stream().filter(m -> Objects.equals(m.trainerId(), trainerKey)).count();
        }

        /** Изменения веса между соседними замерами клиента. */
        int[] gains(int memberId) {
            List<Weighing> own = weighings.stream().filter(w -> w.memberId() == memberId)
                    .sorted(Comparator.comparing(Weighing::date)).toList();
            if (own.size() < 2) return new int[0];
            int[] gains = new int[own.size() - 1];
            for (int i = 1; i < own.size(); i++) {
                gains[i - 1] = own.get(i).weightKg() - own.get(i - 1).weightKg();
            }
            return gains;
        }

        /** Наибольшее изменение веса между замерами (у худеющих все изменения отрицательные). */
        int biggestChange(int memberId) {
            int[] gains = gains(memberId);
            // ЗАЛОЖЕНО: R12 — максимум стартует с 0; если клиент только худел, вернётся 0, которого в данных нет.
            int maxGain = 0;
            for (int g : gains) {
                if (g > maxGain) maxGain = g;
            }
            return maxGain;
        }

        /** Самая тяжёлая тренировка по длительности — длительность неотрицательна. */
        int longestSession(int memberId) {
            int longest = 0;
            for (Visit v : visits) {
                if (v.memberId() == memberId && v.minutes() > longest) longest = v.minutes();
            }
            return longest;
        }

        /** Лёгкий замер — минимум стартует с первого элемента. */
        int lightest(int memberId) {
            List<Integer> ws = weighings.stream().filter(w -> w.memberId() == memberId).map(Weighing::weightKg).toList();
            if (ws.isEmpty()) throw new GymException("Нет замеров");
            int min = ws.getFirst();
            for (int w : ws) {
                if (w < min) min = w;
            }
            return min;
        }

        /** Индекс массы тела по последнему замеру. */
        double bmi(int memberId) {
            Member m = member(memberId);
            Optional<Weighing> last = weighings.stream().filter(w -> w.memberId() == memberId)
                    .max(Comparator.comparing(Weighing::date));
            if (last.isEmpty()) throw new GymException("Нет замеров");
            double heightM = m.heightCm() / 100.0;
            // ЗАЛОЖЕНО-НЕ-ШАБЛОН: неверная формула — ИМТ = вес / рост², а здесь вес делится на рост в первой степени.
            return last.get().weightKg() / heightM;
        }

        /** Посещения по клиентам. */
        Map<Integer, Integer> visitCounts() {
            Map<Integer, Integer> counts = new HashMap<>();
            for (Visit v : visits) {
                counts.merge(v.memberId(), 1, Integer::sum);
            }
            return counts;
        }

        /** Самый частый посетитель. */
        Optional<Member> regular() {
            Map<Integer, Integer> counts = visitCounts();
            int best = 0;
            Integer bestId = null;
            for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
                if (e.getValue() > best) {
                    best = e.getValue();
                    bestId = e.getKey();
                }
            }
            return bestId == null ? Optional.empty() : Optional.of(members.get(bestId));
        }

        /** Средняя длительность визита — деление уже в double. */
        double meanSession() {
            if (visits.isEmpty()) return 0;
            long total = 0;
            for (Visit v : visits) total += v.minutes();
            return (double) total / visits.size();
        }

        /** Загрузка по часам: сколько визитов начинается в каждый час. */
        int[] hourLoad() {
            int[] load = new int[24];
            for (Visit v : visits) load[v.hour()]++;
            return load;
        }

        int peakHour() {
            int[] load = hourLoad();
            int peak = 0;
            for (int h = 1; h < load.length; h++) {
                if (load[h] > load[peak]) peak = h;
            }
            return peak;
        }

        /** Тренеры с навыком — поиск по множеству навыков. */
        List<Trainer> withSkill(String skill) {
            List<Trainer> result = new ArrayList<>();
            for (Trainer t : trainers.values()) {
                if (t.skills().contains(skill.toLowerCase())) result.add(t);
            }
            result.sort(Comparator.comparing(Trainer::name));
            return result;
        }

        String report(LocalDate today) {
            Map<Integer, Integer> counts = visitCounts();
            StringBuilder sb = new StringBuilder();
            for (Member m : members.values()) {
                sb.append(String.format("#%d %-12s %-10s визитов: %3d, осталось дней: %d%n", m.id(), m.name(),
                        m.plan().label(), counts.getOrDefault(m.id(), 0), daysLeft(m, today)));
            }
            Map<Plan, Long> byPlan = members.values().stream()
                    .collect(Collectors.groupingBy(Member::plan, Collectors.counting()));
            byPlan.forEach((p, n) -> sb.append(p.label()).append(": ").append(n).append('\n'));
            return sb.toString();
        }

        List<Member> members() { return new ArrayList<>(members.values()); }
    }

    static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new GymException("Дата в формате ГГГГ-ММ-ДД: " + raw);
        }
    }

    /** Рост в сантиметрах; если не указан или не число — 170. */
    static int parseHeight(String[] p, int index) {
        int height = 170;
        if (index < p.length) {
            // ЗАЛОЖЕНО: R14 — исключение проглатывается пустым catch без комментария (строка с catch ниже); опечатка в росте молча даёт 170.
            try {
                height = Integer.parseInt(p[index]);
            } catch (NumberFormatException e) {}
        }
        return height;
    }

    static Integer parseTrainer(String raw) {
        if (raw.equals("-")) return null;
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            // ожидаемо: «нет» и прочий текст означают «без тренера»
            return null;
        }
    }

    static Set<String> parseSkills(String raw) {
        Set<String> skills = new HashSet<>();
        for (String s : raw.split(",")) skills.add(s.trim().toLowerCase());
        return skills;
    }

    static void help() {
        System.out.println("hire <имя> <навыки,через,запятую>");
        System.out.println("enroll <имя> <тариф> <ГГГГ-ММ-ДД> <idТренера|-> [рост]");
        System.out.println("visit <id> <ГГГГ-ММ-ДД> <час> <минуты> | weigh <id> <ГГГГ-ММ-ДД> <кг>");
        System.out.println("clients <idТренера> | skill <навык> | change <id> | bmi <id> | light <id>");
        System.out.println("rank | regular | peak | report <ГГГГ-ММ-ДД> | help | exit");
    }

    public static void main(String[] args) {
        Gym gym = new Gym();
        Scanner in = new Scanner(System.in);
        List<Member> ranking = new ArrayList<>();
        help();
        while (true) {
            System.out.print("gym> ");
            if (!in.hasNextLine()) return;
            String[] p = in.nextLine().trim().split("\\s+");
            String cmd = p[0];
            try {
                switch (cmd) {
                    case "hire" -> {
                        Trainer t = gym.hire(p[1], parseSkills(p.length > 2 ? p[2] : ""));
                        System.out.println("Тренер #" + t.id());
                    }
                    case "enroll" -> {
                        Plan plan = Plan.valueOf(p[2].toUpperCase());
                        Member m = gym.enroll(p[1], plan, parseDate(p[3]), parseTrainer(p[4]), parseHeight(p, 5));
                        System.out.println("Клиент #" + m.id() + ", абонемент до " + m.expires());
                    }
                    case "visit" -> {
                        gym.checkIn(Integer.parseInt(p[1]), parseDate(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
                        System.out.println("Отмечено");
                    }
                    case "weigh" -> {
                        int accepted = gym.importWeighings(List.of(new Weighing(Integer.parseInt(p[1]), parseDate(p[2]),
                                Integer.parseInt(p[3]))));
                        System.out.println(accepted == 1 ? "Замер сохранён" : "Замер отклонён");
                    }
                    case "clients" -> gym.clientsOf(Integer.parseInt(p[1])).forEach(m -> System.out.println(m.name()));
                    case "skill" -> gym.withSkill(p[1]).forEach(t -> System.out.println(t.name()));
                    case "change" -> System.out.println("Наибольшее изменение: " + gym.biggestChange(Integer.parseInt(p[1])) + " кг");
                    case "bmi" -> System.out.printf("ИМТ: %.1f%n", gym.bmi(Integer.parseInt(p[1])));
                    case "light" -> System.out.println(gym.lightest(Integer.parseInt(p[1])) + " кг");
                    case "rank" -> {
                        ranking = gym.members();
                        Map<Integer, Integer> counts = gym.visitCounts();
                        ranking.sort(Comparator.comparing((Member m) -> counts.getOrDefault(m.id(), 0)).reversed());
                        ranking.forEach(m -> System.out.println(m.name() + " — " + counts.getOrDefault(m.id(), 0)));
                    }
                    case "regular" -> System.out.println(gym.regular().map(Member::name).orElse("Посещений нет"));
                    case "peak" -> System.out.println("Час пик: " + gym.peakHour() + ":00, средний визит "
                            + String.format("%.0f", gym.meanSession()) + " мин");
                    case "report" -> System.out.print(gym.report(p.length > 1 ? parseDate(p[1]) : LocalDate.now()));
                    case "help" -> help();
                    case "exit" -> {
                        System.out.println("Клиентов: " + gym.members().size());
                        return;
                    }
                    default -> System.out.println("Неизвестная команда " + cmd);
                }
            } catch (GymException e) {
                System.out.println("Ошибка: " + e.getMessage());
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                System.out.println("Неверные аргументы: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                System.out.println("Нет такого тарифа");
            }
        }
    }
}
