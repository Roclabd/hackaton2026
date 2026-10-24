package blueprints.runner;

import java.util.List;
import java.util.Map;

/**
 * Протокол между сервером и рабочим процессом (RunWorker).
 * Запрос — один JSON в stdin процесса. Ответы — по одной JSON-строке на событие в настоящий stdout процесса.
 */
public final class WorkerProtocol {

    private WorkerProtocol() {}

    /**
     * Скомпилированная версия кода: байткод уже готов, рабочему процессу компилятор не нужен.
     *
     * @param trapExit ловить System.exit без перезапуска процесса (см. ExitTrap)
     * @param plainJvm запускать настоящий java main, если программа читает/печатает стек
     */
    public record Program(String name, String mainClass, Map<String, byte[]> classes, boolean trapExit, boolean plainJvm) {

        public Program(String name, String mainClass, Map<String, byte[]> classes, boolean trapExit) {
            this(name, mainClass, classes, trapExit, false);
        }

        /** Без ловушки System.exit: выход программы — настоящий, с перезапуском процесса. */
        public Program(String name, String mainClass, Map<String, byte[]> classes) {
            this(name, mainClass, classes, false);
        }

        public Program withPlainJvm(boolean plainJvm) {
            return new Program(name, mainClass, classes, trapExit, plainJvm);
        }
    }

    /** Один прогон: какую версию и на каком входе. id уникален в пределах одного запроса. */
    public record Job(int id, String program, String input) {}

    public record Request(List<Program> programs, List<Job> jobs, long timeoutMs) {}

    /**
     * Событие из рабочего процесса.
     * {@code start} — прогон начат; {@code result} — прогон закончен;
     * {@code exit} — программа сама вызвала System.exit: вывод на этот момент, код выхода сервер возьмёт у процесса.
     */
    public record Event(String event, int job, RunResult result) {
        static final String START = "start";
        static final String RESULT = "result";
        static final String EXIT = "exit";
    }
}
