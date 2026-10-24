package blueprints.runner;

import java.lang.reflect.Field;
import java.util.Collection;

/**
 * Счётчики копии «замер» (пакет instrument): вспомогательный класс {@value #CLASS_NAME} с массивом
 * {@value #FIELD}, который программа увеличивает перед изменёнными операторами.
 *
 * Рабочий процесс после прогона читает этот массив из загрузчика прогона. У обычных программ такого класса нет —
 * тогда ничего не читается и прогон идёт как всегда.
 */
public final class CoverageCounters {

    /** Простое имя класса со счётчиками. Двойное подчёркивание — чтобы не совпасть с классами программы. */
    public static final String CLASS_NAME = "__BlueprintsCoverage";
    public static final String FIELD = "HITS";

    private CoverageCounters() {}

    /** Полное имя класса со счётчиками среди классов программы или null, если его нет. */
    static String find(Collection<String> classNames) {
        for (String name : classNames) {
            String simple = name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1);
            if (simple.equals(CLASS_NAME)) {
                return name;
            }
        }
        return null;
    }

    /** Копия счётчиков после прогона; null — класса нет или прочитать не удалось (замер просто пропускает вход). */
    static int[] read(ClassLoader loader, String className) {
        if (className == null) {
            return null;
        }
        try {
            Field field = Class.forName(className, true, loader).getDeclaredField(FIELD);
            field.setAccessible(true);
            return ((int[]) field.get(null)).clone();
        } catch (Throwable unreadable) {
            return null;
        }
    }
}
