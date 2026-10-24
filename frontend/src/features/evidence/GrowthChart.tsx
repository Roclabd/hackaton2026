import type { Verdict } from '../../api/types'

// График роста: только по данным замера (verdict.speedup). Нет замера — честная заглушка, без выдуманных цифр.

interface GrowthChartProps {
  speedup: Verdict['speedup']
  /** оценка роста по структуре кода из находки (growthBefore → growthAfter), если есть */
  estimate?: { before: string; after: string } | null
}

export function GrowthChart({ speedup, estimate }: GrowthChartProps) {
  if (!speedup) {
    return (
      <div className="growth-placeholder">
        <strong>Появится после замера</strong>
        {estimate && <span>Оценка по структуре кода: {estimate.before} → {estimate.after}</span>}
      </div>
    )
  }
  // Две полосы: «до» — полная, «после» — в factor раз короче (factor — из ответа API).
  const afterWidth = Math.max(2, 300 / speedup.factor)
  return (
    <svg viewBox="0 0 400 120" role="img" aria-label={`Ускорение в ${speedup.factor} раз при n = ${speedup.n}`} className="growth-svg">
      <text x="0" y="16" fill="var(--muted)" fontSize="13">Время при n = {speedup.n}</text>
      <text x="0" y="48" fill="var(--ink)" fontSize="13">до · {speedup.before}</text>
      <rect x="90" y="36" width="300" height="16" rx="3" fill="var(--orange)" />
      <text x="0" y="84" fill="var(--ink)" fontSize="13">после · {speedup.after}</text>
      <rect x="90" y="72" width={afterWidth} height="16" rx="3" fill="var(--blue)" />
      <text x={96 + afterWidth} y="85" fill="var(--blue)" fontSize="13">×{speedup.factor.toLocaleString('ru-RU')}</text>
    </svg>
  )
}
