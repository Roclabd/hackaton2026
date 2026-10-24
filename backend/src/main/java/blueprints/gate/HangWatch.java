package blueprints.gate;

import java.util.HashSet;
import java.util.Set;

/**
 * Сторож «программа не завершается»: считает входы, на которых эталон подряд не уложился во время,
 * и после {@code limit} таких входов один раз вызывает {@code onHang} (останавливает прогоны проверки).
 *
 * Зачем: учебное меню вида {@code while (true) { if (!sc.hasNextLine()) continue; … }} на конце ввода ждёт
 * вечно. Каждый такой прогон — полный таймаут 5 с, а прогонов на проверку ~120: проверка тянулась бы минутами
 * и на защите выглядела бы зависшей. Двух-трёх зависших входов подряд достаточно, чтобы понять картину.
 *
 * Правила подсчёта:
 * <ul>
 *   <li>учитываются только прогоны эталона: зависший кандидат — это находка проверки, а не повод её бросить;</li>
 *   <li>считаются разные входы: повторный прогон эталона на том же входе нового не сообщает;</li>
 *   <li>любой завершившийся прогон эталона сбрасывает счёт. Параллельные процессы присылают итоги вперемешку,
 *       поэтому «подряд» значит «все процессы застряли одновременно», а не «один медленный вход».</li>
 * </ul>
 */
final class HangWatch {

    private final int limit;
    private final Runnable onHang;
    private final Set<Integer> hangingInputs = new HashSet<>();
    private boolean tripped;

    HangWatch(int limit, Runnable onHang) {
        this.limit = limit;
        this.onHang = onHang;
    }

    /** Итог очередного прогона эталона на входе {@code input}. */
    synchronized void original(int input, boolean timedOut) {
        if (tripped) {
            return;
        }
        if (!timedOut) {
            hangingInputs.clear();
            return;
        }
        hangingInputs.add(input);
        if (hangingInputs.size() >= limit) {
            tripped = true;
            onHang.run();
        }
    }

    synchronized boolean tripped() {
        return tripped;
    }
}
