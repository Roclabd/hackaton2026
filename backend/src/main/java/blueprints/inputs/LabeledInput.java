package blueprints.inputs;

/**
 * Один вход для прогона.
 *
 * @param group группа для отчёта: «Обычные значения», «Значения null», «Из корпуса»…
 * @param text  что подаётся в stdin программы
 */
public record LabeledInput(String group, String text) {}
