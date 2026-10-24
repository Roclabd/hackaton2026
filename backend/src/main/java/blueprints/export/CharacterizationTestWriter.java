package blueprints.export;

import java.util.List;

/**
 * Текст JUnit-класса с характеризационными тестами: каждый тест запускает программу отдельным процессом
 * {@code java}, подаёт вход в stdin и сравнивает stdout, код выхода и тип необработанного исключения
 * с тем, что выдал эталон.
 *
 * Отдельный процесс, а не вызов main в том же JVM: так тест честно работает и с System.exit, и со статикой,
 * которая иначе переживала бы предыдущий тест.
 */
final class CharacterizationTestWriter {

    /** Лимит на один запуск программы внутри теста. */
    static final int PROCESS_TIMEOUT_SECONDS = 10;
    /** Страховка JUnit поверх лимита процесса: тест не может зависнуть навсегда. */
    static final int TEST_TIMEOUT_SECONDS = 30;

    private static final String ARGUMENT_INDENT = " ".repeat(16);

    private CharacterizationTestWriter() {}

    /**
     * @param mainClass полное имя запускаемого класса (с пакетом, если он есть)
     * @param origin    откуда тесты — строка для комментария в шапке
     */
    static String write(String testClass, String mainClass, String origin, List<CharacterizationCase> cases) {
        StringBuilder out = new StringBuilder();
        out.append(HEADER_IMPORTS);
        out.append("/**\n")
                .append(" * Характеризационные тесты ").append(mainClass).append(": фиксируют поведение эталона\n")
                .append(" * (исходной версии) на ").append(Plural.of(cases.size(), "входе", "входах", "входах")).append(".\n")
                .append(" * ").append(origin).append('\n')
                .append(" *\n")
                .append(" * Ожидания — настоящий вывод эталона, полученный запуском, а не придуманный. Каждый тест\n")
                .append(" * запускает программу отдельным процессом java, подаёт вход в stdin и сравнивает stdout\n")
                .append(" * и код выхода точно, а при необработанном исключении — его тип.\n")
                .append(" */\n")
                .append("@DisplayName(").append(JavaLiterals.quoted(mainClass + ": поведение эталона")).append(")\n")
                .append("@Timeout(").append(TEST_TIMEOUT_SECONDS).append(")\n")
                .append("class ").append(testClass).append(" {\n\n")
                .append("    /** Главный класс программы. */\n")
                .append("    private static final String MAIN_CLASS = ").append(JavaLiterals.quoted(mainClass)).append(";\n")
                .append("    /** Лимит на один запуск программы. */\n")
                .append("    private static final long TIMEOUT_SECONDS = ").append(PROCESS_TIMEOUT_SECONDS).append(";\n")
                .append("    /** Заголовок, который JVM печатает в stderr при необработанном исключении из main. */\n")
                .append("    private static final Pattern UNCAUGHT =\n")
                .append("            Pattern.compile(\"Exception in thread \\\"main\\\" ([^\\\\s:]+)\");\n");
        for (CharacterizationCase testCase : cases) {
            out.append('\n').append(method(testCase));
        }
        out.append(HELPERS);
        return out.toString();
    }

    /** Один метод теста — он же показывается в предпросмотре. */
    static String method(CharacterizationCase testCase) {
        String exception = testCase.expectedException() == null ? "null" : JavaLiterals.quoted(testCase.expectedException());
        return "    @Test\n"
                + "    @DisplayName(" + JavaLiterals.quoted(testCase.displayName()) + ")\n"
                + "    void " + testCase.methodName() + "() throws Exception {\n"
                + "        assertBehaviour(\n"
                + ARGUMENT_INDENT + "// вход (stdin)\n"
                + ARGUMENT_INDENT + JavaLiterals.literal(testCase.input(), ARGUMENT_INDENT) + ",\n"
                + ARGUMENT_INDENT + "// stdout эталона\n"
                + ARGUMENT_INDENT + JavaLiterals.literal(testCase.expectedStdout(), ARGUMENT_INDENT) + ",\n"
                + ARGUMENT_INDENT + "/* код выхода */ " + testCase.expectedExitCode() + ",\n"
                + ARGUMENT_INDENT + "/* необработанное исключение */ " + exception + ");\n"
                + "    }\n";
    }

    private static final String HEADER_IMPORTS = """
            import org.junit.jupiter.api.DisplayName;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.Timeout;

            import java.io.IOException;
            import java.nio.charset.StandardCharsets;
            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.util.List;
            import java.util.concurrent.TimeUnit;
            import java.util.regex.Matcher;
            import java.util.regex.Pattern;

            import static org.junit.jupiter.api.Assertions.assertEquals;

            """;

    /** Общая часть класса: запуск программы и сравнение. Не зависит от входов. */
    private static final String HELPERS = """

                /** Что программа сделала на одном входе. */
                private record Run(String stdout, String stderr, int exitCode) {}

                /**
                 * Запускает программу на входе и сравнивает с эталоном.
                 *
                 * @param expectedException тип необработанного исключения эталона; null — эталон не падал
                 */
                private static void assertBehaviour(String input, String expectedStdout, int expectedExitCode,
                                                    String expectedException) throws Exception {
                    Run run = run(input);
                    assertEquals(expectedStdout, lineEndsToLf(run.stdout()), "stdout");
                    assertEquals(expectedExitCode, run.exitCode(), "код выхода");
                    assertEquals(expectedException, uncaughtException(run.stderr()), "необработанное исключение");
                }

                /** Отдельный процесс java: stdin, stdout и stderr — через временные файлы, без потоков-читателей. */
                private static Run run(String input) throws IOException, InterruptedException {
                    Path dir = Files.createTempDirectory("characterization");
                    Path in = dir.resolve("stdin.txt");
                    Path out = dir.resolve("stdout.txt");
                    Path err = dir.resolve("stderr.txt");
                    try {
                        Files.writeString(in, input, StandardCharsets.UTF_8);
                        Process process = new ProcessBuilder(command())
                                .redirectInput(in.toFile())
                                .redirectOutput(out.toFile())
                                .redirectError(err.toFile())
                                .start();
                        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            process.destroyForcibly().waitFor();
                            throw new AssertionError("Программа не завершилась за " + TIMEOUT_SECONDS + " с");
                        }
                        return new Run(read(out), read(err), process.exitValue());
                    } finally {
                        for (Path file : List.of(in, out, err, dir)) {
                            Files.deleteIfExists(file);
                        }
                    }
                }

                /** Та же JDK, что запускает тесты, и то же окружение, в котором снимался эталон. */
                private static List<String> command() {
                    String java = ProcessHandle.current().info().command()
                            .orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
                    return List.of(java,
                            "-Duser.language=en", "-Duser.country=US", "-Dfile.encoding=UTF-8",
                            "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                            "-cp", System.getProperty("java.class.path"),
                            MAIN_CLASS);
                }

                private static String read(Path file) throws IOException {
                    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                }

                /** Тест переносим между ОС: CRLF приравнивается к LF (ожидания записаны с LF). */
                private static String lineEndsToLf(String text) {
                    return text.replace("\\r\\n", "\\n");
                }

                private static String uncaughtException(String stderr) {
                    Matcher header = UNCAUGHT.matcher(stderr);
                    String type = null;
                    while (header.find()) {
                        type = header.group(1); // заголовок JVM печатается последним, после stderr самой программы
                    }
                    return type;
                }
            }
            """;
}
