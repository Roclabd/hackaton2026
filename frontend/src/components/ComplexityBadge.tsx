import type { Complexity } from '../api/types'

/** Значок «O(n²)» у метода или цикла. Цвет — по уровню, подсказка — откуда оценка (с бэка, по структуре кода). */
export function ComplexityBadge({ complexity }: { complexity: Complexity | null | undefined }) {
  if (!complexity) return null
  return (
    <span className={`cx-badge cx-${complexity.level}`} title={complexity.why} aria-label={`Сложность ${complexity.big}: ${complexity.why}`}>
      {complexity.big}
    </span>
  )
}
