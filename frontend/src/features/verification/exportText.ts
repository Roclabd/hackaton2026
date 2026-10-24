import type { JUnitExportPreview } from '../../api/exportApi'
import type { Verdict } from '../../api/types'

// Тексты окна экспорта и правило «можно ли экспортировать» — отдельно от JSX.

/** Экспорт есть только у проверки, которая реально сравнила версии: «совпало» или «расхождение». */
export function canExport(verdict: Verdict | null): boolean {
  return verdict !== null && (verdict.status === 'MATCHED' || verdict.status === 'MISMATCH')
}

export function exportHint(verdict: Verdict | null): string {
  if (verdict === null || verdict.status === 'RUNNING') return 'Экспорт станет доступен, когда проверка закончится'
  if (canExport(verdict)) return 'Скачать JUnit-тесты: мини-проект Maven, запускается без нашего инструмента'
  return 'Экспорт доступен только после проверки с итогом «совпало» или «расхождение»'
}

export function describeExport(preview: JUnitExportPreview): { intro: string; note: string } {
  const skipped = preview.skipped > 0
    ? ` Не вошли ${preview.skipped} ${plural(preview.skipped, 'вход', 'входа', 'входов')} со случайным или неизвестным выводом — причины в README.`
    : ''
  return {
    intro: `Первый из ${preview.tests} ${plural(preview.tests, 'теста', 'тестов', 'тестов')}: тесты запускают программу и сравнивают stdout, код выхода и исключение с выводом эталона.`,
    note: `В архиве ${preview.zipName} — мини-проект Maven: проверенный код, тесты и README. Запуск: mvn test.${skipped}`,
  }
}

/** 1 вход, 2 входа, 5 входов. */
function plural(n: number, one: string, few: string, many: string): string {
  const lastTwo = Math.abs(n) % 100
  const last = lastTwo % 10
  if (lastTwo >= 11 && lastTwo <= 14) return many
  if (last === 1) return one
  if (last >= 2 && last <= 4) return few
  return many
}
