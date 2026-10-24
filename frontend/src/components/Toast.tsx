import { useAppState } from '../state/store'

/** Всплывающее сообщение внизу экрана (ошибки API — красным). */
export function Toast() {
  const toast = useAppState((s) => s.toast)
  return (
    <div id="toast" role="status" aria-live="polite" className={`${toast ? 'show' : ''} ${toast?.tone === 'error' ? 'toast-error' : ''}`}>
      {toast?.text}
    </div>
  )
}
