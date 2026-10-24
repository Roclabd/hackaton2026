package blueprints.runner;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Печать необработанного исключения так, как её делает JVM: «Exception in thread "main" …» и стектрейс.
 * Нижние кадры рабочего процесса (наш запуск через reflection) убираются — в настоящем запуске их нет.
 */
final class StackTraces {

    private StackTraces() {}

    static void printUncaught(Throwable error, PrintStream err) {
        trim(error, Collections.newSetFromMap(new IdentityHashMap<>()));
        err.print("Exception in thread \"main\" ");
        error.printStackTrace(err);
        err.flush();
    }

    /** Убирает хвост из кадров запуска у самого исключения, его причин и подавленных. */
    private static void trim(Throwable error, Set<Throwable> seen) {
        if (error == null || !seen.add(error)) {
            return;
        }
        StackTraceElement[] frames = error.getStackTrace();
        int end = frames.length;
        while (end > 0 && isLauncherFrame(frames[end - 1])) {
            end--;
        }
        if (end < frames.length) {
            error.setStackTrace(Arrays.copyOf(frames, end));
        }
        trim(error.getCause(), seen);
        for (Throwable suppressed : error.getSuppressed()) {
            trim(suppressed, seen);
        }
    }

    private static boolean isLauncherFrame(StackTraceElement frame) {
        String name = frame.getClassName();
        return name.startsWith("blueprints.runner.")
                || name.startsWith("java.lang.reflect.")
                || name.startsWith("jdk.internal.reflect.")
                || name.startsWith("java.lang.invoke.")
                || name.equals("java.lang.Thread");
    }
}
