package blueprints.diag;

import java.util.List;

/**
 * Заплатка вместе с тем, что она меняет в тексте находки.
 *
 * @param growthAfter рост числа операций после заплатки
 * @param conditions  при каких условиях заплатка эквивалентна оригиналу
 * @param why         продолжение объяснения находки: что делает заплатка и почему она безопасна
 */
record PatchProposal(Patch patch, String growthAfter, List<String> conditions, String why) {}
