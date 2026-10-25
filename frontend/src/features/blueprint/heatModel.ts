import type { HeatResult } from '../../api/types'

/** Отсутствующий счётчик никогда не превращается в выдуманный ноль. */
export function executionCount(result: HeatResult | null | undefined, nodeId: string): number | null {
  if (!result || !Object.hasOwn(result.counts, nodeId)) return null
  const count = result.counts[nodeId]
  return Number.isSafeInteger(count) && count >= 0 ? count : null
}

/** Линейная шкала: светлый фон для нуля, оранжевый для максимума. */
export function heatColor(count: number, maxCount: number): string {
  const ratio = maxCount > 0 ? Math.min(1, Math.max(0, count / maxCount)) : 0
  const pale = [255, 249, 240]
  const hot = [243, 153, 80]
  return `rgb(${pale.map((value, i) => Math.round(value + (hot[i] - value) * ratio)).join(', ')})`
}
