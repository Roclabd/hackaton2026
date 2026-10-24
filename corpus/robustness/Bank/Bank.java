import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Банковские счета: открытие, пополнение, снятие, перевод, начисление процентов.
 * Свои проверяемые исключения. Выход из меню — System.exit(0).
 */
public class Bank {

    // ---------- Исключения ----------

    static class BankException extends Exception {
        BankException(String message) {
            super(message);
        }
    }

    static class InsufficientFundsException extends BankException {
        private final double shortage;

        InsufficientFundsException(double shortage) {
            super(String.format("Недостаточно средств, не хватает %.2f руб.", shortage));
            this.shortage = shortage;
        }

        double getShortage() {
            return shortage;
        }
    }

    static class AccountNotFoundException extends BankException {
        AccountNotFoundException(String number) {
            super("Счёт не найден: " + number);
        }
    }

    // ---------- Счета ----------

    interface Interest {
        double rate();

        default double interestFor(double amount) {
            return amount * rate() / 100.0;
        }
    }

    static abstract class Account {
        protected final String number;
        protected final String owner;
        protected double balance;
        protected final List<String> history = new ArrayList<>();

        Account(String number, String owner) {
            this.number = number;
            this.owner = owner;
        }

        void deposit(double amount) throws BankException {
            if (amount <= 0) {
                throw new BankException("Сумма должна быть положительной");
            }
            balance += amount;
            history.add("+" + amount);
        }

        void withdraw(double amount) throws BankException {
            if (amount <= 0) {
                throw new BankException("Сумма должна быть положительной");
            }
            double limit = available();
            if (amount > limit) {
                throw new InsufficientFundsException(amount - limit);
            }
            balance -= amount;
            history.add("-" + amount);
        }

        /** Сколько можно снять. */
        abstract double available();

        abstract String type();

        @Override
        public String toString() {
            return String.format("%s %s (%s): %.2f руб.", type(), number, owner, balance);
        }
    }

    static class SavingsAccount extends Account implements Interest {
        SavingsAccount(String number, String owner) {
            super(number, owner);
        }

        @Override
        double available() {
            return balance;
        }

        @Override
        String type() {
            return "Сберегательный";
        }

        @Override
        public double rate() {
            return balance >= 100000 ? 7.5 : 5.0;
        }
    }

    static class CheckingAccount extends Account {
        private final double overdraft;

        CheckingAccount(String number, String owner, double overdraft) {
            super(number, owner);
            this.overdraft = overdraft;
        }

        @Override
        double available() {
            return balance + overdraft;
        }

        @Override
        String type() {
            return "Текущий";
        }

        @Override
        void withdraw(double amount) throws BankException {
            super.withdraw(amount);
            if (balance < 0) {
                history.add("овердрафт");
            }
        }
    }

    // ---------- Банк ----------

    private final Map<String, Account> accounts = new HashMap<>();
    private int nextNumber = 1001;

    Account open(String owner, boolean savings) {
        String number = "40817-" + nextNumber++;
        Account account = savings ? new SavingsAccount(number, owner) : new CheckingAccount(number, owner, 5000);
        accounts.put(number, account);
        return account;
    }

    Account find(String number) throws AccountNotFoundException {
        Account account = accounts.get(number);
        if (account == null) {
            throw new AccountNotFoundException(number);
        }
        return account;
    }

    void transfer(String from, String to, double amount) throws BankException {
        Account a = find(from);
        Account b = find(to);
        if (a == b) {
            throw new BankException("Нельзя перевести на тот же счёт");
        }
        a.withdraw(amount);
        try {
            b.deposit(amount);
        } catch (BankException e) {
            a.balance += amount; // откат
            throw e;
        }
    }

    int accrueInterest() {
        int count = 0;
        for (Account account : accounts.values()) {
            if (account instanceof Interest interest) {
                double sum = interest.interestFor(account.balance);
                if (sum > 0) {
                    account.balance += sum;
                    account.history.add("проценты " + String.format("%.2f", sum));
                    count++;
                }
            }
        }
        return count;
    }

    double totalBalance() {
        double total = 0;
        for (Account account : accounts.values()) {
            total += account.balance;
        }
        return total;
    }

    // ---------- Меню ----------

    private static double readAmount(Scanner sc) {
        System.out.print("Сумма: ");
        while (!sc.hasNextDouble()) {
            String bad = sc.next();
            System.out.print("«" + bad + "» — не число. Сумма: ");
        }
        return sc.nextDouble();
    }

    public static void main(String[] args) {
        Bank bank = new Bank();
        bank.open("Иванов", true);
        bank.open("Петрова", false);
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.println("\n1-открыть 2-пополнить 3-снять 4-перевод 5-проценты 6-счета 0-выход");
            System.out.print("> ");
            int choice = sc.nextInt();
            try {
                switch (choice) {
                    case 1 -> {
                        System.out.print("Владелец и тип (s/c): ");
                        String owner = sc.next();
                        String type = sc.next();
                        Account acc = bank.open(owner, type.equals("s"));
                        System.out.println("Открыт счёт " + acc.number);
                    }
                    case 2 -> {
                        System.out.print("Номер: ");
                        Account acc = bank.find(sc.next());
                        acc.deposit(readAmount(sc));
                        System.out.println(acc);
                    }
                    case 3 -> {
                        System.out.print("Номер: ");
                        Account acc = bank.find(sc.next());
                        acc.withdraw(readAmount(sc));
                        System.out.println(acc);
                    }
                    case 4 -> {
                        System.out.print("Откуда и куда: ");
                        String from = sc.next();
                        String to = sc.next();
                        bank.transfer(from, to, readAmount(sc));
                        System.out.println("Перевод выполнен");
                    }
                    case 5 -> System.out.println("Начислено процентов на счетах: " + bank.accrueInterest());
                    case 6 -> {
                        bank.accounts.values().stream()
                                .sorted((x, y) -> x.number.compareTo(y.number))
                                .forEach(System.out::println);
                        System.out.printf("Всего в банке: %.2f%n", bank.totalBalance());
                    }
                    case 0 -> {
                        System.out.println("Работа завершена");
                        System.exit(0);
                    }
                    default -> System.out.println("Нет такого пункта: " + choice);
                }
            } catch (InsufficientFundsException e) {
                System.out.println("Отказ: " + e.getMessage() + " [" + (int) Math.ceil(e.getShortage()) + "]");
            } catch (BankException e) {
                System.out.println("Ошибка: " + e.getMessage());
            } finally {
                System.out.println("-- операция " + choice + " обработана --");
            }
        }
    }
}
