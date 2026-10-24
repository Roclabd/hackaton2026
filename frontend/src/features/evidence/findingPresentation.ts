import type { Finding } from '../../api/types'

export function findingCategory(finding: Finding): string {
  switch (finding.category) {
    case 'DEFECT': return 'Дефект'
    case 'DEAD_CODE': return 'Мёртвый код'
    case 'INEFFICIENCY': return 'Неэффективность'
    default: return finding.rule === 'R1' ? 'Неэффективность' : 'Находка'
  }
}

export function growthEstimate(finding: Finding | null): { before: string; after: string } | null {
  return finding?.growthBefore && finding.growthAfter ? { before: finding.growthBefore, after: finding.growthAfter } : null
}

export function findingGrowth(finding: Finding): string {
  const estimate = growthEstimate(finding)
  return estimate ? ` · рост ${estimate.before} → ${estimate.after} (оценка по структуре кода)` : ''
}
