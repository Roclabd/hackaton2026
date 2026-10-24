package blueprints.gate;

/**
 * Что известно, когда эталон не завершился на нескольких входах подряд и проверку остановили досрочно
 * (см. {@link HangWatch}). Из этого собирается причина INCOMPLETE — по-русски и с числами.
 *
 * @param totalInputs     сколько входов было сгенерировано всего
 * @param inputsInARow    на скольких разных входах подряд эталон не уложился во время, после чего проверку остановили
 * @param exitCommand     команда выхода из меню, найденная в коде, или null
 * @param scenariosFinish сценарии с командой выхода завершились (не сработал сторож и на них)
 */
record NonTerminating(int totalInputs, int inputsInARow, String exitCommand, boolean scenariosFinish) {

    /**
     * @param compared на скольких входах эталон и кандидат реально сравнены
     * @param scenarios сколько сценариев с командой выхода было
     */
    String reason(int compared, int scenarios, long timeoutMs) {
        String head = "Программа не завершается на входах без команды выхода: проверено " + compared + " из "
                + totalInputs + " входов. Эталон на " + inputsInARow + " входах подряд не уложился в " + timeoutMs / 1000
                + " с — похоже, на конце ввода он ждёт ввод бесконечно; остальные такие входы не прогонялись, "
                + "чтобы проверка не длилась минутами";
        if (exitCommand == null) {
            return head + ". Команду выхода из меню («0», «exit», «выход»…) в коде найти не удалось — "
                    + "сравнить поведение не на чем";
        }
        if (scenarios == 0) {
            return head + ". Сценариев с командой выхода «" + exitCommand + "» не нашлось";
        }
        if (!scenariosFinish) {
            return head + ". Даже сценарии, которые заканчиваются командой «" + exitCommand + "», не завершаются";
        }
        return head + ". Сравнены только сценарии, которые заканчиваются командой выхода «" + exitCommand + "»";
    }
}
