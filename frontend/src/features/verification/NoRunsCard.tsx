import type { Verdict } from '../../api/types'
import { Icon } from '../../components/Icon'
import { selectByLine } from '../../state/actions'
import { navigate } from '../../state/route'

/** Строка из «…: строка 67, колонка 21: …» — текст причины собирает бэкенд (GateJob). */
export function reasonLine(reason: string | null): number | null {
  const match = reason?.match(/строка (\d+)/)
  return match ? Number(match[1]) : null
}

/** Проверка закончилась до первого запуска: вместо пустых «входов» и «сценариев» — что случилось и что делать. */
export function NoRunsCard({ verdict }: { verdict: Verdict }) {
  const reason = verdict.incompleteReason ?? ''
  const baselineBroken = reason.startsWith('Эталон')
  const candidateBroken = verdict.status === 'BUILD_ERROR'
  const line = candidateBroken ? (verdict.buildErrors[0]?.line || null) : reasonLine(reason)
  const toCode = () => {
    navigate('workspace')
    if (line) selectByLine(line)
  }
  return (
    <section className="pane no-runs">
      <div className="pane-header"><strong>Запуски не выполнялись</strong><span className="muted">0 входов</span></div>
      <div className="no-runs-body">
        {baselineBroken ? (
          <>
            <p>Сравнивать не с чем: эталон — исходная программа, а она не компилируется. Поэтому вердикт жёлтый, а не зелёный.</p>
            <ol>
              <li>Вернитесь к коду — ошибка подсвечена{line ? ` в строке ${line}` : ''}.</li>
              <li>Исправьте её: схема и находки обновятся.</li>
              <li>Нажмите «Сделать текущую версию эталоном» над кодом и снова запустите проверку.</li>
            </ol>
          </>
        ) : candidateBroken ? (
          <p>Изменённая версия не компилируется, поэтому её не с чем было запускать. Ошибки компилятора — выше; исправьте их в коде и запустите проверку снова.</p>
        ) : (
          <p>{reason || 'Проверка остановилась до первого запуска.'}</p>
        )}
        {(baselineBroken || candidateBroken) && (
          <button type="button" className="btn btn-blue" onClick={toCode}>
            <Icon name="code" />{line ? `Перейти к ошибке в строке ${line}` : 'Вернуться к коду'}
          </button>
        )}
      </div>
    </section>
  )
}
