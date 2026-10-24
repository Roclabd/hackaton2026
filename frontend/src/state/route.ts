import { useSyncExternalStore } from 'react'

// Этапы работы — хэш-маршруты, как в макете: #workspace, #diff, #verification.

export type Route = 'workspace' | 'diff' | 'verification'
const ROUTES: Route[] = ['workspace', 'diff', 'verification']

export function currentRoute(): Route {
  const hash = location.hash.slice(1) as Route
  return ROUTES.includes(hash) ? hash : 'workspace'
}

export function navigate(route: Route): void {
  if (currentRoute() !== route) location.hash = route
}

function subscribe(listener: () => void) {
  window.addEventListener('hashchange', listener)
  return () => window.removeEventListener('hashchange', listener)
}

export function useRoute(): Route {
  return useSyncExternalStore(subscribe, currentRoute)
}
