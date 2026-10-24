import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Журнал оценок студентов.
 * Ввод: число студентов N, затем N строк «Фамилия оценка1 оценка2 ...», затем фамилия для поиска.
 * В программе намеренно оставлены типичные ошибки: целочисленное деление в среднем
 * и сравнение строк через ==.
 */
public class Grades {

    static final int MIN_GRADE = 2;
    static final int MAX_GRADE = 5;

    static class Student {
        String name;
        int[] marks;

        Student(String name, int[] marks) {
            this.name = name;
            this.marks = marks;
        }

        // Ошибка: целочисленное деление — дробная часть теряется
        double average() {
            if (marks.length == 0) {
                return 0;
            }
            int sum = 0;
            for (int m : marks) {
                sum += m;
            }
            return sum / marks.length;
        }

        int best() {
            int max = marks[0];
            for (int i = 1; i < marks.length; i++) {
                if (marks[i] > max) {
                    max = marks[i];
                }
            }
            return max;
        }

        boolean hasDebt() {
            for (int m : marks) {
                if (m == MIN_GRADE) {
                    return true;
                }
            }
            return false;
        }

        String level() {
            double avg = average();
            if (avg >= 4.5) {
                return "отличник";
            } else if (avg >= 3.5) {
                return "хорошист";
            } else if (avg >= 3) {
                return "троечник";
            }
            return "неуспевающий";
        }
    }

    static int[] parseMarks(String[] parts) {
        int[] marks = new int[parts.length - 1];
        for (int i = 1; i < parts.length; i++) {
            int m = Integer.parseInt(parts[i]);
            if (m < MIN_GRADE || m > MAX_GRADE) {
                throw new IllegalArgumentException("Оценка вне диапазона: " + m);
            }
            marks[i - 1] = m;
        }
        return marks;
    }

    // Средняя по группе — тоже целочисленно (та же ошибка)
    static double groupAverage(List<Student> students) {
        int total = 0;
        int count = 0;
        for (Student s : students) {
            for (int m : s.marks) {
                total += m;
                count++;
            }
        }
        return count == 0 ? 0 : (double) (total / count);
    }

    // Гистограмма оценок: сколько двоек, троек, четвёрок, пятёрок
    static int[] histogram(List<Student> students) {
        int[] hist = new int[MAX_GRADE + 1];
        for (Student s : students) {
            for (int m : s.marks) {
                hist[m]++;
            }
        }
        return hist;
    }

    // Сортировка пузырьком по среднему баллу (по убыванию)
    static void sortByAverage(List<Student> students) {
        for (int i = 0; i < students.size(); i++) {
            for (int j = 0; j < students.size() - 1 - i; j++) {
                if (students.get(j).average() < students.get(j + 1).average()) {
                    Student tmp = students.get(j);
                    students.set(j, students.get(j + 1));
                    students.set(j + 1, tmp);
                }
            }
        }
    }

    // Ошибка: сравнение строк через == (для строк из Scanner почти всегда false)
    static Student findByName(List<Student> students, String name) {
        for (Student s : students) {
            if (s.name == name) {
                return s;
            }
        }
        return null;
    }

    static String stars(int n) {
        String result = "";
        for (int i = 0; i < n; i++) {
            result += "*";
        }
        return result;
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("Сколько студентов? ");
        int n = Integer.parseInt(sc.nextLine().trim());
        List<Student> students = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!sc.hasNextLine()) {
                System.out.println("Ввод закончился раньше: прочитано " + i + " из " + n);
                break;
            }
            String line = sc.nextLine().trim();
            if (line.isEmpty()) {
                i--;
                continue;
            }
            String[] parts = line.split("\\s+");
            try {
                students.add(new Student(parts[0], parseMarks(parts)));
            } catch (IllegalArgumentException e) {
                System.out.println("Строка пропущена (" + e.getMessage() + "): " + line);
            }
        }

        System.out.println("\n--- Ведомость ---");
        for (Student s : students) {
            System.out.printf("%-10s %-15s ср. %.2f  лучш. %d  %s%s%n", s.name, Arrays.toString(s.marks),
                    s.average(), s.marks.length == 0 ? 0 : s.best(), s.level(), s.hasDebt() ? " (долг)" : "");
        }
        System.out.printf("Средний балл группы: %.2f%n", groupAverage(students));

        int[] hist = histogram(students);
        for (int g = MIN_GRADE; g <= MAX_GRADE; g++) {
            System.out.println(g + ": " + stars(hist[g]) + " " + hist[g]);
        }

        sortByAverage(students);
        System.out.print("Рейтинг:");
        for (int i = 0; i < students.size(); i++) {
            System.out.print(" " + (i + 1) + "." + students.get(i).name);
        }
        System.out.println();

        if (sc.hasNextLine()) {
            String wanted = sc.nextLine().trim();
            Student found = findByName(students, wanted);
            if (found != null) {
                System.out.println("Найден: " + found.name + ", средний " + found.average());
            } else {
                System.out.println("Студент «" + wanted + "» не найден");
            }
        }
    }
}
