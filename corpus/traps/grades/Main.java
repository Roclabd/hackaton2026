public class Main {
    public static void main(String[] args) {
        int[] scores = {5, 4, 3, 5, 2};
        String student = "Аня";

        System.out.println("Отчёт ученика: " + student);
        printScores(scores);

        double average = calculateAverage(scores);
        System.out.println("Средний балл: " + average);

        if (average > 4) {
            System.out.println("Отличный результат!");
        } else {
            System.out.println("Можно улучшить результат.");
        }

        int highest = findHighest(scores);
        System.out.println("Самая высокая оценка: " + highest);

        String status = getStatus(average);
        System.out.println("Статус: " + status);

        showProgress(average);
    }

    private static void printScores(int[] scores) {
        System.out.println("Оценки:");
        for (int i = 0; i <= scores.length; i++) { // Ошибка 1: выход за границы массива
            System.out.println("  " + (i + 1) + ". " + scores[i]);
        }
    }

    private static double calculateAverage(int[] scores) {
        int sum = 0;
        for (int score : scores) {
            sum += score;
        }
        return (double) sum / (scores.length - 1); // Ошибка 2: неверный делитель
    }

    private static int findHighest(int[] scores) {
        int highest = 0;
        for (int score : scores) {
            if (score < highest) { // Ошибка 3: сравнение в обратную сторону
                highest = score;
            }
        }
        return highest;
    }

    private static String getStatus(double average) {
        if (average >= 4.5) {
            return "Отлично";
        } else if (average >= 3.5) {
            return "Хорошо";
        } else if (average >= 2.5) {
            return "Удовлетворительно";
        }
        return "Неудовлетворительно";
    }

    private static void showProgress(double average) {
        int percent = (int) (average / 5 * 100);
        System.out.println("Прогресс: " + percent + "%");

        if (percent = 100) { // Ошибка 4: присваивание вместо сравнения — не скомпилируется
            System.out.println("Максимальный прогресс!");
        }
    }

    private static int countPassingScores(int[] scores) {
        int count = 0;
        for (int score : scores) {
            if (score >= 3) {
                count++;
            }
        }
        return count;
    }

    private static int countFailingScores(int[] scores) {
        int count = 0;
        for (int score : scores) {
            if (score < 3) {
                count++;
            }
        }
        return count;
    }

    private static void printSummary(int[] scores) {
        System.out.println("Сдано предметов: " + countPassingScores(scores));
        System.out.println("Не сдано предметов: " + countFailingScores(scores));
    }

    private static boolean hasPerfectScore(int[] scores) {
        for (int score : scores) {
            if (score == 5) {
                return true;
            }
        }
        return false;
    }

    private static void printPerfectScoreMessage(int[] scores) {
        if (hasPerfectScore(scores)) {
            System.out.println("Есть оценка 5!");
        } else {
            System.out.println("Оценок 5 нет.");
        }
    }
}
