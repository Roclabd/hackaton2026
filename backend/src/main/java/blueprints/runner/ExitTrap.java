package blueprints.runner;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Быстрый System.exit без перезапуска рабочего процесса.
 *
 * Зачем: монолиты-меню часто выходят через System.exit(0). Честный выход убивает рабочий процесс, а новый
 * на Windows поднимается до ~1 с — проверка из сотни прогонов растягивается на десятки секунд.
 *
 * Как: с JDK 21 Runtime.exit пишет в журнал «java.lang.Runtime» строку «Runtime.exit() called with status: N»
 * — до начала завершения JVM и в потоке программы. Наш обработчик журнала запоминает код и навсегда
 * останавливает этот поток. Для программы это неотличимо от выхода: дальше ни одна строка, ни finally не
 * выполняются. JVM при этом не завершается, и следующий прогон идёт в том же процессе.
 *
 * Чем отличается от настоящего выхода: не срабатывают shutdown-хуки программы и не останавливаются её
 * другие потоки. Поэтому ловушка включается только для программ без хуков и потоков ({@link #safeFor}),
 * а иначе — настоящий выход с перезапуском процесса. Если формат строки журнала другой (не та JDK) —
 * ловушка молча не срабатывает, и выход тоже настоящий.
 */
public final class ExitTrap {

    private static final Pattern STATUS = Pattern.compile("Runtime\\.exit\\(\\) called with status: (-?\\d+)");
    /** Признаки того, что ловушка изменила бы поведение: хуки завершения и свои потоки. */
    private static final List<String> UNSAFE_MARKERS = List.of(
            "addShutdownHook", "Thread", "Executor", "CompletableFuture", "parallel", "Timer", "LogManager");

    /** Сильная ссылка: журналы JUL хранятся по слабым ссылкам, без неё настройка может пропасть. */
    private static Logger runtimeLogger;

    private ExitTrap() {}

    /** Можно ли ловить System.exit этой программы, не меняя её наблюдаемого поведения. */
    public static boolean safeFor(String source) {
        return source != null && UNSAFE_MARKERS.stream().noneMatch(source::contains);
    }

    /**
     * Ставит обработчик в рабочем процессе.
     *
     * @param onExit вызывается в потоке программы с кодом выхода; true — выход пойман, поток надо остановить
     */
    static void install(IntPredicate onExit) {
        runtimeLogger = Logger.getLogger("java.lang.Runtime");
        runtimeLogger.setUseParentHandlers(false);
        runtimeLogger.setLevel(Level.ALL);
        runtimeLogger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                Matcher m = STATUS.matcher(String.valueOf(record.getMessage()));
                if (m.find() && onExit.test(Integer.parseInt(m.group(1)))) {
                    parkForever();
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    /** Поток программы «вышел»: больше он не выполнит ни строки. */
    private static void parkForever() {
        while (true) {
            java.util.concurrent.locks.LockSupport.park();
        }
    }
}
