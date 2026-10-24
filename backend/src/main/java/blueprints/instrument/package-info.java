/**
 * Режим «замер» (план, раздел 3.4): копия кода со счётчиками — только для замеров, вердикт по ней никогда не выносится.
 * Задача B7.
 *
 * Сейчас здесь — покрытие изменённого кода для проверки (план 3.6, п. 4): построчный diff эталона и кандидата
 * ({@link blueprints.instrument.ChangedLines}), счётчики перед изменёнными операторами
 * ({@link blueprints.instrument.CoverageInstrumenter}), прогоны копии и сводка
 * ({@link blueprints.instrument.ChangedCoverageMeter}, {@link blueprints.instrument.ChangedCoverage}).
 * Счётчики читает рабочий процесс после прогона (runner.CoverageCounters).
 */
package blueprints.instrument;
