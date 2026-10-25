import { USE_MOCKS } from '../../api/client'
import { Icon } from '../../components/Icon'
import { navigate } from '../../state/route'
import { useAppState } from '../../state/store'
import { describeVerdict, phaseText } from '../verification/verdictText'

/** Строка состояния внизу: цвет — по последнему вердикту. */
export function StatusBar() {
  const job = useAppState((s) => s.job)
  const version = useAppState((s) => s.project?.version)
  const draft = useAppState((s) => s.draft)
  const verdict = job?.verdict ?? null
  const running = job !== null && (verdict === null || verdict.status === 'RUNNING')

  let tone = 'neutral'
  let icon: 'clock' | 'info' | 'check' | 'close' | 'alert' = 'info'
  let title = 'Проверка не запускалась'
  let message = 'Нажмите «Запустить и сравнить», чтобы сравнить код с эталоном'
  if (running) {
    tone = 'busy'
    icon = 'clock'
    title = 'Проверка…'
    message = verdict ? phaseText(verdict.phase) : 'Отправляем код на проверку'
  } else if (verdict) {
    const view = describeVerdict(verdict)
    tone = view.tone
    icon = view.icon as typeof icon
    title = view.label
    // Устарела, только если код правили после запуска; применённая заплатка совпадает с кандидатом.
    const stale = job !== null && draft !== job.draftAtStart && draft !== job.candidateSource
    message = stale ? 'Код изменён после проверки — нужна новая' : view.detail
  }

  return (
    <footer className={`status-bar ${tone}`}>
      <button type="button" className="status-link" onClick={() => navigate('verification')}>
        <Icon name={icon} />
        <strong>{title}</strong>
        <span className="status-message">{message}</span>
      </button>
      <div className="status-meta">
        {version !== undefined && <span>Версия {version}</span>}
        <span>Java 25</span>
        {USE_MOCKS && <span>Демо-данные</span>}
      </div>
    </footer>
  )
}
