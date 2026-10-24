import type { Verdict } from '../../api/types'
import { Icon } from '../../components/Icon'
import { describeVerdict, phaseText } from './verdictText'

/** Ход проверки: фаза и прогресс из GET /api/jobs/{jobId}. */
export function ProgressPanel({ verdict }: { verdict: Verdict | null }) {
  const percent = Math.round((verdict?.progress ?? 0) * 100)
  return (
    <section className="progress-panel" aria-live="polite">
      <div className="progress-top">
        <strong><Icon name="clock" />Проверяем поведение</strong>
        <span>{percent}%</span>
      </div>
      <div className="progress-track"><div style={{ width: `${percent}%` }} /></div>
      <p>{verdict ? phaseText(verdict.phase) : 'Отправляем код на проверку'}</p>
      <span className="muted">{verdict ? `Проверено входов: ${verdict.checked} из ${verdict.inputs}` : ''}</span>
    </section>
  )
}

/** Итог проверки — цвет и тексты по статусу (таблица статусов в docs/API.md). */
export function ResultPanel({ verdict }: { verdict: Verdict }) {
  const view = describeVerdict(verdict)
  return (
    <section className={`result-panel ${view.tone}`}>
      <div className="result-icon"><Icon name={view.icon} /></div>
      <div className="result-copy">
        <h2>{view.label}</h2>
        <p>{view.description}</p>
        {verdict.status === 'BUILD_ERROR' && verdict.buildErrors.length > 0 ? (
          <ul className="compile-errors">
            {verdict.buildErrors.map((e, i) => <li key={i}>Строка {e.line}:{e.column} — {e.text}</li>)}
          </ul>
        ) : (
          <div className="result-detail">{view.detail}</div>
        )}
      </div>
    </section>
  )
}
