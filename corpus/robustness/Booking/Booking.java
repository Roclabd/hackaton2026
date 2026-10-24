import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

/**
 * Бронирование мест в кинозале.
 * Команды: «показать», «бронь Р М», «отмена Р М», «группа N», «касса», «выход».
 * Внимание: на конце ввода программа не выходит — цикл ждёт новую строку бесконечно
 * (так бывает в учебном коде; выйти можно только командой «выход»).
 */
public class Booking {

    enum SeatType {
        STANDARD {
            @Override
            int price(int basePrice) {
                return basePrice;
            }
        },
        COMFORT {
            @Override
            int price(int basePrice) {
                return basePrice * 3 / 2;
            }
        },
        VIP {
            @Override
            int price(int basePrice) {
                return basePrice * 2 + 100;
            }
        };

        abstract int price(int basePrice);
    }

    interface PriceRule {
        int apply(int price, int row);
    }

    static class SeatTakenException extends RuntimeException {
        SeatTakenException(int row, int seat) {
            super("Место " + row + "-" + seat + " уже занято");
        }
    }

    private final int rows;
    private final int seats;
    private final boolean[][] taken;
    private final List<Ticket> tickets = new ArrayList<>();
    private int revenue;
    private final int basePrice = 300;

    // Скидка для первых рядов — анонимный класс
    private final PriceRule rule = new PriceRule() {
        @Override
        public int apply(int price, int row) {
            return row <= 2 ? price - 50 : price;
        }
    };

    Booking(int rows, int seats) {
        this.rows = rows;
        this.seats = seats;
        this.taken = new boolean[rows][seats];
    }

    // Внутренний (не статический) класс: видит поля зала
    class Ticket {
        final int row;
        final int seat;
        final int price;

        Ticket(int row, int seat) {
            this.row = row;
            this.seat = seat;
            this.price = rule.apply(typeOf(row).price(basePrice), row);
        }

        @Override
        public String toString() {
            return "билет " + row + "-" + seat + " (" + typeOf(row) + ") " + price + " руб.";
        }
    }

    SeatType typeOf(int row) {
        if (row == rows) {
            return SeatType.VIP;
        }
        return row > rows / 2 ? SeatType.COMFORT : SeatType.STANDARD;
    }

    Ticket book(int row, int seat) {
        if (row < 1 || row > rows || seat < 1 || seat > seats) {
            throw new IllegalArgumentException("Нет места " + row + "-" + seat);
        }
        if (taken[row - 1][seat - 1]) {
            throw new SeatTakenException(row, seat);
        }
        taken[row - 1][seat - 1] = true;
        Ticket t = new Ticket(row, seat);
        tickets.add(t);
        revenue += t.price;
        return t;
    }

    boolean cancel(int row, int seat) {
        Iterator<Ticket> it = tickets.iterator();
        while (it.hasNext()) {
            Ticket t = it.next();
            if (t.row == row && t.seat == seat) {
                it.remove();
                taken[row - 1][seat - 1] = false;
                revenue -= t.price;
                return true;
            }
        }
        return false;
    }

    /** Найти N свободных мест подряд в одном ряду, начиная с дальних рядов. */
    int[] findGroup(int n) {
        int foundRow = -1;
        int foundStart = -1;
        search:
        for (int r = rows - 1; r >= 0; r--) {
            int run = 0;
            for (int s = 0; s < seats; s++) {
                run = taken[r][s] ? 0 : run + 1;
                if (run == n) {
                    foundRow = r;
                    foundStart = s - n + 1;
                    break search;
                }
            }
        }
        return foundRow < 0 ? null : new int[]{foundRow + 1, foundStart + 1};
    }

    void show() {
        System.out.print("   ");
        for (int s = 1; s <= seats; s++) {
            System.out.print(s % 10);
        }
        System.out.println();
        int r = 0;
        do {
            StringBuilder sb = new StringBuilder(String.format("%2d ", r + 1));
            for (int s = 0; s < seats; s++) {
                sb.append(taken[r][s] ? 'X' : '.');
            }
            System.out.println(sb);
            r++;
        } while (r < rows);
    }

    String handle(String line) {
        String[] p = line.split(" ");
        String reply;
        try {
            switch (p[0]) {
                case "показать":
                case "зал":
                    show();
                    reply = "Свободно мест: " + (rows * seats - tickets.size());
                    break;
                case "бронь":
                    reply = "Куплен " + book(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                    break;
                case "отмена":
                    reply = cancel(Integer.parseInt(p[1]), Integer.parseInt(p[2])) ? "Бронь снята" : "Такой брони нет";
                    break;
                case "группа": {
                    int n = Integer.parseInt(p[1]);
                    int[] place = findGroup(n);
                    if (place == null) {
                        reply = "Нет " + n + " мест подряд";
                        break;
                    }
                    for (int i = 0; i < n; i++) {
                        book(place[0], place[1] + i);
                    }
                    reply = "Группа из " + n + ": ряд " + place[0] + ", места " + place[1] + "–" + (place[1] + n - 1);
                    break;
                }
                case "касса":
                    reply = "Продано билетов: " + tickets.size() + ", выручка: " + revenue + " руб.";
                    break;
                default:
                    reply = "Не понимаю: " + line;
            }
        } catch (SeatTakenException e) {
            reply = "Отказ: " + e.getMessage();
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            reply = "Неверный формат команды: " + line;
        } catch (IllegalArgumentException e) {
            reply = "Ошибка: " + e.getMessage();
        } finally {
            System.out.println("[команда: " + p[0] + "]");
        }
        return reply;
    }

    public static void main(String[] args) {
        Booking hall = new Booking(5, 8);
        hall.book(3, 4);
        hall.book(3, 5);
        Scanner sc = new Scanner(System.in);
        System.out.println("Кинозал 5×8. Команды: показать, бронь Р М, отмена Р М, группа N, касса, выход");
        while (true) {
            if (!sc.hasNextLine()) {
                continue; // ждём следующую строку (на конце ввода — бесконечно)
            }
            String line = sc.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equals("выход")) {
                System.out.println("Итог: " + hall.tickets.size() + " билетов, " + hall.revenue + " руб.");
                break;
            }
            System.out.println(hall.handle(line));
        }
    }
}
