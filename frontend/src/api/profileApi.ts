import { request } from './client'

// Замер роста — POST /api/projects/{id}/profile. Типы держим здесь, а не в types.ts: модуль подключается отдельно.

/** Ряд счётчиков одной версии: сколько раз выполнились тела циклов на каждом размере входа. */
export interface GrowthSeries {
  readonly counts: readonly number[]
  /** лучшая модель роста: «1», «log n», «n», «n log n», «n²», «n³» */
  readonly model: string
  /** наклон на лог-лог шкале: n² — около 2, n — около 1 */
  readonly slope: number
  /** [первая, последняя] строка, циклы которой считались */
  readonly lines: readonly [number, number]
  readonly loops: number
}

/** Ответ замера. supported = false — чисел нет, причина в note. */
export interface ProfileResult {
  readonly supported: boolean
  readonly sizes?: readonly number[]
  readonly before?: GrowthSeries
  /** null/нет — заплатку не просили или её не измерить */
  readonly after?: GrowthSeries | null
  /** во сколько раз меньше операций у заплатки на наибольшем n */
  readonly factor?: number | null
  readonly note: string
  readonly durationMs?: number
}

export function measureGrowth(projectId: string, findingId: string, patchId?: string | null): Promise<ProfileResult> {
  const body = patchId ? { findingId, patchId } : { findingId }
  return request<ProfileResult>('POST', `/api/projects/${encodeURIComponent(projectId)}/profile`, body)
}
