import { ApiError, request, USE_MOCKS } from './client'
import type { HeatResult } from './types'

export function measureHeat(projectId: string, methodId: string, input?: string): Promise<HeatResult> {
  if (USE_MOCKS) return Promise.reject(new ApiError(400, 'Частота исполнения недоступна в демо-режиме — запустите бэкенд'))
  return request<HeatResult>('POST', `/api/projects/${encodeURIComponent(projectId)}/heat`,
    input === undefined ? { methodId } : { methodId, input })
}
