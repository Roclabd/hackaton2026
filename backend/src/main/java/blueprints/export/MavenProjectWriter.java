package blueprints.export;

import blueprints.gate.VerdictStatus;

/**
 * pom.xml и README.md мини-проекта: его можно собрать и запустить обычным Maven, без нашего инструмента.
 * Версии — те же, что в backend/pom.xml.
 */
final class MavenProjectWriter {

    static final String JUNIT_VERSION = "6.1.3";
    static final String SUREFIRE_VERSION = "3.6.0";
    static final String JAVA_RELEASE = "25";

    static final String JUNIT_PROPERTIES = "src/test/resources/junit-platform.properties";

    private MavenProjectWriter() {}

    /** Каждый тест — отдельный процесс java (~1 с на запуск JVM), поэтому параллельно: в разы быстрее. */
    static String junitProperties() {
        return """
                # Tests are independent (each starts its own java process), so run them in parallel.
                junit.jupiter.execution.parallel.enabled=true
                junit.jupiter.execution.parallel.mode.default=concurrent
                """;
    }

    /** @param artifactId имя проекта Maven (латиница, цифры, дефис) */
    static String pom(String artifactId) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <!-- Сгенерировано Java Blueprints: характеризационные тесты по проверке поведения -->
                    <groupId>blueprints.export</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0</version>
                    <packaging>jar</packaging>

                    <properties>
                        <maven.compiler.release>%s</maven.compiler.release>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
                        <junit.version>%s</junit.version>
                    </properties>

                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>${junit.version}</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-surefire-plugin</artifactId>
                                <version>%s</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(artifactId, JAVA_RELEASE, JUNIT_VERSION, SUREFIRE_VERSION);
    }

    /** Что лежит в архиве и что из проверки сюда не попало. */
    record ReadmeFacts(String mainClass, String mainFile, String testFile, String referenceFile, String jobId,
                       VerdictStatus status, CaseSelector.Selection selection) {}

    static String readme(ReadmeFacts facts) {
        CaseSelector.Selection selection = facts.selection();
        int tests = selection.cases().size();
        StringBuilder out = new StringBuilder();
        out.append("# Тесты поведения ").append(facts.mainClass()).append("\n\n")
                .append("Мини-проект Maven, который сгенерировал Java Blueprints по проверке `").append(facts.jobId())
                .append("` (итог проверки: ").append(statusText(facts.status())).append(").\n")
                .append("Его можно собрать и запустить **без нашего инструмента** — нужны только JDK ")
                .append(JAVA_RELEASE).append(" и Maven.\n\n")
                .append("## Что внутри\n\n")
                .append("- `").append(facts.mainFile()).append("` — проверенная (новая) версия кода.\n")
                .append("- `").append(facts.testFile()).append("` — ").append(Plural.of(tests, "тест", "теста", "тестов"))
                .append(" JUnit Jupiter ").append(JUNIT_VERSION).append(", по одному на каждый сравненный вход.\n")
                .append("- `").append(JUNIT_PROPERTIES)
                .append("` — тесты независимы (каждый — свой процесс), поэтому JUnit запускает их параллельно.\n")
                .append("- `").append(facts.referenceFile())
                .append("` — эталон (исходная версия) для справки; Maven его не компилирует.\n\n")
                .append("## Что фиксирует тест\n\n")
                .append("Тест фиксирует **поведение эталона**: ожидания — настоящий вывод исходной версии, полученный ")
                .append("её запуском при проверке, а не придуманный человеком или ИИ.\n\n")
                .append("Каждый тест запускает программу отдельным процессом `java` (та же JDK, что у Maven; ")
                .append("`-Duser.language=en -Duser.country=US`, вывод в UTF-8), подаёт вход в stdin и сравнивает:\n\n")
                .append("- stdout — точно;\n")
                .append("- код выхода — точно;\n")
                .append("- тип необработанного исключения из `main`, если эталон с ним падал.\n\n")
                .append("Отдельный процесс нужен, чтобы тест честно работал с `System.exit` и со статическими полями.\n\n")
                .append("## Как запустить\n\n")
                .append("```\nmvn test\n```\n\n")
                .append(expectation(facts.status(), tests))
                .append("Проверить, что тесты ловят регресс: поменяйте код в `").append(facts.mainFile())
                .append("` (например, сломайте условие) и снова запустите `mvn test`.\n\n")
                .append("## Что не вошло и ограничения\n\n")
                .append(skipped(selection))
                .append("- Переводы строк CRLF и LF считаются одинаковыми — чтобы тест, снятый на Windows, ")
                .append("проходил на Linux и macOS. Других нормализаций нет.\n")
                .append("- stderr целиком не сравнивается — только тип необработанного исключения.\n")
                .append("- Тесты проверяют поведение только на этих входах и не доказывают совпадение на всех возможных.\n");
        return out.toString();
    }

    private static String statusText(VerdictStatus status) {
        return switch (status) {
            case MATCHED -> "поведение совпало";
            case MISMATCH -> "найдено расхождение";
            default -> status.name();
        };
    }

    private static String expectation(VerdictStatus status, int tests) {
        if (status == VerdictStatus.MISMATCH) {
            return "Проверка нашла расхождение: на части входов новая версия ведёт себя не так, как эталон. "
                    + "Эти тесты **упадут** — так и задумано, они показывают, где именно поведение изменилось. "
                    + "С кодом из `reference/` все тесты проходят.\n\n";
        }
        return "Ожидаемый результат: `Tests run: " + tests + ", Failures: 0, Errors: 0`.\n\n";
    }

    private static String skipped(CaseSelector.Selection selection) {
        StringBuilder out = new StringBuilder();
        if (selection.random() > 0) {
            out.append("- Пропущено входов со случайным выводом: ").append(selection.random())
                    .append(" (эталон при повторном запуске печатал разное — тест падал бы сам по себе).\n");
        }
        if (selection.unknown() > 0) {
            out.append("- Пропущено входов с неизвестным выводом: ").append(selection.unknown())
                    .append(" (таймаут, потерянный или обрезанный вывод).\n");
        }
        if (selection.tooLarge() > 0) {
            out.append("- Пропущено входов со слишком большим входом или выводом: ").append(selection.tooLarge())
                    .append(" (больше ").append(CaseSelector.MAX_LITERAL_BYTES / 1000)
                    .append(" КБ — не помещается в строковую константу Java).\n");
        }
        if (out.isEmpty()) {
            out.append("- Пропущенных входов нет: все сравненные входы проверки стали тестами.\n");
        }
        return out.toString();
    }
}
