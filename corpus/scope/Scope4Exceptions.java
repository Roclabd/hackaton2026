import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.EmptyStackException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.InputMismatchException;
import java.util.Scanner;
import java.util.Stack;

/**
 * Раздел 4 документа: исключения. Сообщения JVM-исключений (NPE, файловые) зависят от окружения, поэтому
 * для них печатается только имя класса; сообщения своих исключений и стабильных библиотечных — печатаются.
 * Ввод — слова-токены: каждое прогоняется через цепочку проверок; по умолчанию «12 0 abc 7 -3».
 */
public class Scope4Exceptions {

    // 4.Свои исключения: проверяемое (наследует Exception)
    static class InsufficientFundsException extends Exception {
        private final int missing;

        InsufficientFundsException(String message, int missing) {
            super(message);
            this.missing = missing;
        }

        int missing() {
            return missing;
        }
    }

    // 4.Свои исключения: непроверяемое (наследует RuntimeException), с причиной
    static class ValidationException extends RuntimeException {
        ValidationException(String message) {
            super(message);
        }

        ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // 4.Свои исключения: иерархия (наследник своего исключения)
    static class NegativeAmountException extends ValidationException {
        NegativeAmountException(int amount) {
            super("отрицательная сумма " + amount);
        }
    }

    // 4.try-with-resources: ресурс AutoCloseable
    static class Resource implements AutoCloseable {
        private final String name;
        private final boolean failOnClose;

        Resource(String name, boolean failOnClose) {
            this.name = name;
            this.failOnClose = failOnClose;
            System.out.println("открыт " + name);
        }

        void use(boolean fail) {
            System.out.println("используем " + name);
            if (fail) {
                throw new IllegalStateException("сбой в " + name);
            }
        }

        @Override
        public void close() {
            System.out.println("закрыт " + name);
            if (failOnClose) {
                throw new UnsupportedOperationException("не закрывается " + name);
            }
        }
    }

    // 4.CloneNotSupportedException: clone без Cloneable
    static class NotCloneable {
        Object tryClone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    // 4.throws: метод объявляет проверяемое исключение
    static int withdraw(int balance, int amount) throws InsufficientFundsException {
        if (amount < 0) {
            // 4.Свои исключения: непроверяемое из иерархии
            throw new NegativeAmountException(amount);
        }
        if (amount > balance) {
            throw new InsufficientFundsException("не хватает средств", amount - balance);
        }
        return balance - amount;
    }

    // 4.throws: несколько проверяемых исключений в объявлении
    static void ioWork(int mode) throws IOException, InterruptedException {
        if (mode == 1) {
            throw new IOException("ошибка ввода-вывода");
        }
        if (mode == 2) {
            throw new FileNotFoundException("нет файла");
        }
        if (mode == 3) {
            throw new InterruptedException("прервано");
        }
        System.out.println("ioWork без ошибок " + mode);
    }

    static int depth = 0;

    static void recurse() {
        depth++;
        recurse();
    }

    public static void main(String[] args) throws Exception {
        Scanner in = new Scanner(System.in);
        List<String> tokens = new ArrayList<>();
        while (in.hasNext() && tokens.size() < 25) {
            tokens.add(in.next());
        }
        if (tokens.isEmpty()) {
            tokens.addAll(List.of("12", "0", "abc", "7", "-3"));
        }
        for (String token : tokens) {
            System.out.println(token + " -> " + classify(token));
        }
        bank();
        multiCatchAndFinally();
        nestedTry();
        withResources();
        javaLangExceptions();
        errors();
        utilExceptions();
        ioExceptions();
        throwableApi();
        customChain();
    }

    // 4.Требования: цепочка catch от частного к общему, multi-catch и finally в одном try
    static String classify(String token) {
        StringBuilder log = new StringBuilder();
        try {
            int value = Integer.parseInt(token);
            int[] table = new int[5];
            table[value % 5] = 100 / value;
            log.append("ok");
            if (value < 0) {
                // 4.IllegalArgumentException: бросаем сами
                throw new IllegalArgumentException("отрицательное: " + value);
            }
        } catch (NumberFormatException e) {
            // 4.NumberFormatException: Integer.parseInt от не-числа
            log.append("NFE:").append(e.getMessage());
        } catch (ArithmeticException | ArrayIndexOutOfBoundsException e) {
            // 4.ArithmeticException: деление на ноль; 4.ArrayIndexOutOfBoundsException: multi-catch
            log.append("multi:").append(e.getClass().getSimpleName()).append(':').append(e.getMessage());
        } catch (IllegalArgumentException e) {
            log.append("IAE:").append(e.getMessage());
        } catch (RuntimeException e) {
            log.append("RTE");
        } finally {
            log.append(";finally");
        }
        return log.toString();
    }

    static void bank() {
        int[] amounts = {30, 500, -5};
        for (int amount : amounts) {
            try {
                // 4.Проверяемые исключения: обязательный catch
                int left = withdraw(100, amount);
                System.out.println("осталось " + left);
            } catch (InsufficientFundsException e) {
                System.out.println(e.getMessage() + ", не хватает " + e.missing());
            } catch (ValidationException e) {
                System.out.println("валидация: " + e.getMessage() + " " + (e instanceof NegativeAmountException));
            }
        }
    }

    static void multiCatchAndFinally() throws InterruptedException {
        for (int mode = 0; mode <= 3; mode++) {
            try {
                ioWork(mode);
            } catch (FileNotFoundException e) {
                // 4.FileNotFoundException: подкласс IOException — ловим раньше родителя
                System.out.println("FNF " + e.getMessage());
            } catch (IOException e) {
                // 4.IOException: проверяемое
                System.out.println("IO " + e.getMessage());
            } catch (InterruptedException e) {
                // 4.InterruptedException: проверяемое, не из ввода-вывода
                System.out.println("INT " + e.getMessage());
            } finally {
                System.out.println("mode " + mode + " обработан");
            }
        }
        System.out.println(finallyOrder());
    }

    // 4.try/finally: finally выполняется и при return из try
    @SuppressWarnings("finally")
    static String finallyOrder() {
        StringBuilder order = new StringBuilder();
        try {
            order.append("try;");
            return order.append("return;").toString();
        } finally {
            order.append("finally;");
            System.out.println("порядок: " + order);
        }
    }

    static void nestedTry() {
        // 4.Вложенные try: внутренний не ловит — исключение уходит наружу
        try {
            try {
                System.out.println("внутри " + Integer.parseInt("x1"));
            } catch (IllegalStateException e) {
                System.out.println("не это");
            } finally {
                System.out.println("внутренний finally");
            }
            System.out.println("сюда не дойдём");
        } catch (NumberFormatException e) {
            System.out.println("внешний поймал " + e.getMessage());
            try {
                // 4.throw в catch: перебрасываем с причиной
                throw new ValidationException("обёртка", e);
            } catch (ValidationException wrapped) {
                System.out.println(wrapped.getMessage() + " <- " + wrapped.getCause().getClass().getSimpleName());
            }
        } finally {
            System.out.println("внешний finally");
        }
        // 4.Вложенные try: исключение в finally заменяет прежнее
        try {
            try {
                throw new IllegalStateException("первое");
            } finally {
                System.out.println("finally перед выбросом нового");
            }
        } catch (IllegalStateException e) {
            System.out.println("поймано " + e.getMessage());
        }
    }

    static void withResources() {
        // 4.try-with-resources: закрытие в обратном порядке
        try (Resource first = new Resource("A", false); Resource second = new Resource("B", false)) {
            first.use(false);
            second.use(false);
        }
        // 4.try-with-resources: исключение в теле и в close (addSuppressed/getSuppressed)
        try (Resource broken = new Resource("C", true)) {
            broken.use(true);
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage() + " подавлено: " + e.getSuppressed().length + " "
                    + e.getSuppressed()[0].getMessage());
        }
        // 4.try-with-resources: с catch и finally
        try (Resource last = new Resource("D", false)) {
            last.use(false);
        } catch (RuntimeException e) {
            System.out.println("не бывает");
        } finally {
            System.out.println("finally после ресурсов");
        }
    }

    static void javaLangExceptions() {
        Object text = "строка";
        String missing = null;
        int[] array = new int[3];
        // Каждая проверка бросает своё исключение; печатаем имя класса.
        String[] names = new String[11];
        int next = 0;
        try {
            // 4.NullPointerException: вызов метода у null
            missing.length();
        } catch (NullPointerException e) {
            names[next++] = e.getClass().getSimpleName();
        }
        try {
            // 4.ClassCastException: неверное приведение
            Integer wrong = (Integer) text;
            names[next++] = "no" + wrong;
        } catch (ClassCastException e) {
            names[next++] = e.getClass().getSimpleName();
        }
        try {
            // 4.IndexOutOfBoundsException: List.get за границей
            new ArrayList<Integer>().get(0);
        } catch (IndexOutOfBoundsException e) {
            names[next++] = e.getClass().getSimpleName() + "(" + e.getMessage() + ")";
        }
        try {
            // 4.ArrayIndexOutOfBoundsException: индекс массива
            array[3] = 1;
        } catch (ArrayIndexOutOfBoundsException e) {
            names[next++] = e.getClass().getSimpleName() + "(" + e.getMessage() + ")";
        }
        try {
            // 4.StringIndexOutOfBoundsException: charAt за границей
            "abc".charAt(5);
        } catch (StringIndexOutOfBoundsException e) {
            names[next++] = e.getClass().getSimpleName();
        }
        try {
            // 4.NegativeArraySizeException: массив отрицательной длины
            int size = -1;
            int[] bad = new int[size];
            names[next++] = "no" + bad.length;
        } catch (NegativeArraySizeException e) {
            names[next++] = e.getClass().getSimpleName() + "(" + e.getMessage() + ")";
        }
        try {
            // 4.UnsupportedOperationException: изменение неизменяемого списка
            List.of(1).add(2);
        } catch (UnsupportedOperationException e) {
            names[next++] = e.getClass().getSimpleName();
        }
        try {
            // 4.IllegalStateException: бросаем сами
            throw new IllegalStateException("состояние");
        } catch (IllegalStateException e) {
            names[next++] = e.getClass().getSimpleName() + "(" + e.getMessage() + ")";
        }
        try {
            // 4.CloneNotSupportedException: проверяемое
            new NotCloneable().tryClone();
        } catch (CloneNotSupportedException e) {
            names[next++] = e.getClass().getSimpleName();
        }
        try {
            // 4.RuntimeException и Exception: базовые классы
            throw new RuntimeException("базовое");
        } catch (RuntimeException e) {
            names[next++] = e.getClass().getSimpleName() + "(" + e.getMessage() + ")";
        }
        try {
            throw new Exception("общее");
        } catch (Exception e) {
            names[next++] = e.getClass().getSimpleName() + "(" + e.getMessage() + ")";
        }
        for (String name : names) {
            System.out.println("java.lang: " + name);
        }
    }

    static void errors() {
        // 4.StackOverflowError: бесконечная рекурсия (глубину не печатаем — она зависит от стека)
        depth = 0;
        try {
            recurse();
        } catch (StackOverflowError e) {
            System.out.println("StackOverflowError " + (depth > 500));
        }
        // 4.OutOfMemoryError: массив больше предела JVM
        try {
            long[] huge = new long[Integer.MAX_VALUE];
            System.out.println("no " + huge.length);
        } catch (OutOfMemoryError e) {
            System.out.println("OutOfMemoryError");
        }
        // 4.AssertionError: бросаем сами
        try {
            throw new AssertionError("утверждение");
        } catch (AssertionError e) {
            System.out.println("AssertionError " + e.getMessage());
        }
        // 4.Error: базовый класс ошибок (не Exception)
        try {
            throw new Error("серьёзная ошибка");
        } catch (Error e) {
            System.out.println("Error " + e.getMessage() + " " + ((Throwable) e instanceof Exception));
        }
        // 4.Throwable: общий предок — ловит всё
        try {
            throw new ValidationException("через Throwable");
        } catch (Throwable t) {
            System.out.println("Throwable " + t.getMessage());
        }
    }

    static void utilExceptions() {
        // 4.NoSuchElementException: next() у пустого итератора
        Iterator<Integer> empty = new ArrayList<Integer>().iterator();
        try {
            empty.next();
        } catch (NoSuchElementException e) {
            System.out.println("NoSuchElementException (итератор)");
        }
        // 4.NoSuchElementException: Optional.get у пустого
        try {
            Optional.empty().get();
        } catch (NoSuchElementException e) {
            System.out.println("NoSuchElementException (Optional) " + e.getMessage());
        }
        // 4.InputMismatchException: Scanner.nextInt от не-числа (подкласс NoSuchElementException)
        Scanner scanner = new Scanner("abc");
        try {
            scanner.nextInt();
        } catch (InputMismatchException e) {
            System.out.println("InputMismatchException " + (e instanceof NoSuchElementException));
        }
        // 4.ConcurrentModificationException: изменение списка во время for-each
        List<Integer> list = new ArrayList<>(List.of(1, 2, 3, 4));
        try {
            for (Integer value : list) {
                if (value == 2) {
                    list.remove(value);
                }
            }
            System.out.println("без исключения " + list);
        } catch (ConcurrentModificationException e) {
            System.out.println("ConcurrentModificationException " + list);
        }
        // 4.EmptyStackException: pop у пустого Stack
        Stack<Integer> stack = new Stack<>();
        stack.push(1);
        stack.pop();
        try {
            stack.pop();
        } catch (EmptyStackException e) {
            System.out.println("EmptyStackException");
        }
    }

    static void ioExceptions() {
        // 4.FileNotFoundException: открытие несуществующего файла (текст сообщения зависит от ОС — не печатаем)
        try (FileInputStream stream = new FileInputStream("no_such_dir/no_such_file.bin")) {
            System.out.println("открыли " + stream.available());
        } catch (FileNotFoundException e) {
            System.out.println("FileNotFoundException из FileInputStream");
        } catch (IOException e) {
            System.out.println("IOException");
        }
        // 4.UncheckedIOException: обёртка IOException в непроверяемое
        try {
            try {
                throw new IOException("диск");
            } catch (IOException e) {
                throw new UncheckedIOException("обёрнуто", e);
            }
        } catch (UncheckedIOException e) {
            System.out.println(e.getMessage() + " <- " + e.getCause().getMessage() + " " + (e instanceof RuntimeException));
        }
    }

    static void throwableApi() {
        Exception cause = new IllegalArgumentException("причина");
        Exception top = new IllegalStateException("верх", cause);
        // 4.Throwable: getMessage
        System.out.println(top.getMessage());
        // 4.Throwable: getCause
        System.out.println(top.getCause().getMessage() + " " + (top.getCause() == cause));
        // 4.Throwable: toString
        System.out.println(top);
        // 4.Throwable: getStackTrace
        StackTraceElement[] trace = top.getStackTrace();
        System.out.println(trace.length > 0 ? trace[0].getMethodName() : "пусто");
        // 4.Throwable: printStackTrace в свой поток (берём только первую строку — остальное зависит от окружения)
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream sink = new PrintStream(buffer);
        top.printStackTrace(sink);
        sink.flush();
        System.out.println(buffer.toString().lines().findFirst().orElse("пусто"));
        // 4.Throwable: addSuppressed и getSuppressed
        top.addSuppressed(new ArithmeticException("подавленное"));
        System.out.println(top.getSuppressed().length + " " + top.getSuppressed()[0].getMessage());
        // 4.Throwable: initCause и getLocalizedMessage
        Exception late = new Exception("поздняя причина");
        late.initCause(new RuntimeException("вот причина"));
        System.out.println(late.getCause().getMessage() + " " + late.getLocalizedMessage());
        // 4.Throwable: printStackTrace() в System.err (stderr сравнивается отдельно от stdout)
        new IllegalStateException("для stderr").printStackTrace();
    }

    static void customChain() {
        // 4.Свои исключения: цепочка причин, обход через getCause
        Exception chain = new ValidationException("уровень 3",
                new ValidationException("уровень 2", new IllegalArgumentException("уровень 1")));
        int level = 0;
        for (Throwable t = chain; t != null; t = t.getCause()) {
            level++;
            System.out.println(level + ": " + t.getClass().getSimpleName() + " " + t.getMessage());
        }
        // 4.throw: исключение, неперехваченное в main, завершало бы программу с кодом 1 (здесь не бросаем)
        if (level != 3) {
            throw new IllegalStateException("недостижимо");
        }
    }
}
