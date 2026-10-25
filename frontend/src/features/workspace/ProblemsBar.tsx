import { Icon } from '../../components/Icon'
import { countByKind, PROBLEM_KINDS, PROBLEM_STYLES, type Problem } from '../../components/editor/problems'

interface Props {
  problems: Problem[]
  /** номер текущей проблемы в problems; -1 — ещё не переходили */
  current: number
  onGo: (index: number) => void
  /** +1 — следующая, −1 — предыдущая; та же функция, что у F8 / Shift+F8 */
  onStep: (delta: number) => void
}

/**
 * Полоса над кодом: сколько проблем каждого вида и переход «предыдущая / следующая» (F8 / Shift+F8 в редакторе).
 * Клик по виду ведёт к первой проблеме этого вида после текущей.
 */
export function ProblemsBar({ problems, current, onGo, onStep }: Props) {
  if (problems.length === 0) return null
  const counts = countByKind(problems)
  const nextOfKind = (kind: Problem['kind']) => {
    for (let k = 1; k <= problems.length; k++) {
      const index = (Math.max(current, -1) + k + problems.length) % problems.length
      if (problems[index].kind === kind) return onGo(index)
    }
  }
  const active = current >= 0 ? problems[current] : null
  return (
    <div className="problems-bar" role="toolbar" aria-label="Проблемы в коде">
      <div className="problem-chips">
        {PROBLEM_KINDS.filter((kind) => counts[kind] > 0).map((kind) => (
          <button key={kind} type="button" className={`problem-chip problem-chip-${kind}`}
            title={`Перейти к следующей: ${PROBLEM_STYLES[kind].label.toLowerCase()}`} onClick={() => nextOfKind(kind)}>
            <i aria-hidden="true" />{PROBLEM_STYLES[kind].label}<strong>{counts[kind]}</strong>
          </button>
        ))}
      </div>
      <span className="problem-position" aria-live="polite">
        {active ? <>Строка {active.line} · {active.title}</> : `Проблем: ${problems.length}`}
      </span>
      <div className="problem-nav">
        <span className="muted">{active ? `${current + 1} из ${problems.length}` : ''}</span>
        <button type="button" className="icon-button" aria-label="Предыдущая проблема (Shift+F8)" title="Предыдущая (Shift+F8)" onClick={() => onStep(-1)}><Icon name="up" /></button>
        <button type="button" className="icon-button" aria-label="Следующая проблема (F8)" title="Следующая (F8)" onClick={() => onStep(1)}><Icon name="down" /></button>
      </div>
    </div>
  )
}
