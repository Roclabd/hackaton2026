import { useSyncExternalStore } from 'react'
import { measureGrowth, type ProfileResult } from '../../api/profileApi'

// Замеры роста — общие для нижней панели и карточки находки: замерил в одном месте — график виден в обоих.
// Ключ включает версию проекта: после правки кода старый замер не показываем.

export type GrowthProfileState =
  | { readonly status: 'idle' }
  | { readonly status: 'loading' }
  | { readonly status: 'done'; readonly result: ProfileResult }
  | { readonly status: 'error'; readonly message: string }

const IDLE: GrowthProfileState = { status: 'idle' }
const LOADING: GrowthProfileState = { status: 'loading' }

const states = new Map<string, GrowthProfileState>()
const listeners = new Set<() => void>()

export interface ProfileTarget {
  readonly projectId: string
  readonly version: number
  readonly findingId: string
  readonly patchId: string | null
}

const keyOf = (t: ProfileTarget) => `${t.projectId}@${t.version}:${t.findingId}:${t.patchId ?? ''}`

function set(key: string, state: GrowthProfileState) {
  states.set(key, state)
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

/** Состояние замера для находки; null — проекта или находки нет. */
export function useGrowthProfile(target: ProfileTarget | null): GrowthProfileState {
  return useSyncExternalStore(subscribe, () => (target ? states.get(keyOf(target)) ?? IDLE : IDLE))
}

/** Запустить замер (повторный клик во время замера ничего не делает). */
export async function startGrowthProfile(target: ProfileTarget): Promise<void> {
  const key = keyOf(target)
  if (states.get(key)?.status === 'loading') return
  set(key, LOADING)
  try {
    const result = await measureGrowth(target.projectId, target.findingId, target.patchId)
    set(key, { status: 'done', result })
  } catch (error) {
    set(key, { status: 'error', message: error instanceof Error ? error.message : 'Замер не удался' })
  }
}
