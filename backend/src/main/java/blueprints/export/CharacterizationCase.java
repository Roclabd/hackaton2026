package blueprints.export;

/**
 * Один тест экспорта: вход и то, что на нём сделал эталон.
 *
 * @param methodName        имя метода теста (латиница, чтобы не зависеть от кодировки консоли)
 * @param displayName       читаемое имя в отчёте JUnit: группа входа и номер
 * @param input             что подаётся в stdin
 * @param expectedStdout    stdout эталона; переводы строк приведены к \n
 * @param expectedExitCode  код выхода эталона
 * @param expectedException тип необработанного исключения эталона или null
 */
record CharacterizationCase(String methodName, String displayName, String input, String expectedStdout,
                            int expectedExitCode, String expectedException) {}
