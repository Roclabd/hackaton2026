import type { Finding } from '../../api/types'
import { openMethod } from '../../state/actions'
import { navigate } from '../../state/route'
import { dispatch } from '../../state/store'

/** Показать строки находки: открыть чертёж метода и выбрать узел на первой строке. */
export function showFindingLines(finding: Finding): void {
  void openMethod(finding.methodId, finding.lines[0])
}

/** Перейти к экрану «Изменение» с заплаткой находки (по умолчанию — первой). */
export function viewPatch(finding: Finding, patchId = finding.patches[0]?.id ?? null): void {
  dispatch({ type: 'patchSelected', findingId: finding.id, patchId })
  dispatch({ type: 'closeDialog' })
  navigate('diff')
}

export function openFindingDrawer(finding: Finding): void {
  dispatch({ type: 'patchSelected', findingId: finding.id, patchId: finding.patches[0]?.id ?? null })
  dispatch({ type: 'openDialog', dialog: 'finding' })
}

export const linesText = ([first, last]: [number, number]) => (first === last ? `Строка ${first}` : `Строки ${first}–${last}`)
