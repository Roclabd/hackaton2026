import { useSyncExternalStore } from 'react'
import { initialState, reducer, type Action, type State } from './reducer'

// Один стор на всё приложение: состояние неизменяемое, меняется только через dispatch(action).
// Асинхронные шаги (запросы к API) — в actions.ts, они читают состояние через getState().

type Listener = () => void

function createStore() {
  let state = initialState
  const listeners = new Set<Listener>()
  return {
    getState: () => state,
    dispatch(action: Action) {
      state = reducer(state, action)
      listeners.forEach((listener) => listener())
    },
    subscribe(listener: Listener) {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
  }
}

export const store = createStore()
export const dispatch = store.dispatch

/** Подписка компонента на часть состояния. Селектор должен возвращать уже существующий объект, а не новый. */
export function useAppState<T>(selector: (state: State) => T): T {
  return useSyncExternalStore(store.subscribe, () => selector(store.getState()))
}
