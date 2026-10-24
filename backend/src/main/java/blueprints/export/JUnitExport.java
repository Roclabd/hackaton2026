package blueprints.export;

import java.util.Map;

/**
 * Готовый экспорт: файлы мини-проекта Maven и сведения для предпросмотра.
 *
 * @param zipName   имя архива: {@code <Main>-tests.zip}
 * @param files     путь внутри архива → текст файла (UTF-8), в порядке записи
 * @param testFile  путь к классу тестов внутри архива
 * @param tests     сколько тестов в классе
 * @param skipped   сколько входов проверки не стали тестами (случайный, неизвестный или слишком большой вывод)
 * @param firstTest текст первого метода теста — для предпросмотра
 */
public record JUnitExport(String zipName, Map<String, String> files, String testFile, int tests, int skipped,
                          String firstTest) {}
