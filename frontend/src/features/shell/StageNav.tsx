import { useRoute, type Route } from '../../state/route'
import { useAppState } from '../../state/store'

const STAGES: [Route, string][] = [
  ['workspace', 'Код и схема'],
  ['diff', 'Изменение'],
  ['verification', 'Проверка'],
]

/** Этапы работы «1 Код и схема · 2 Изменение · 3 Проверка» — ссылки на хэш-маршруты, живут в шапке. */
export function StageNav() {
  const route = useRoute()
  const hasPatch = useAppState((s) => s.patchId !== null)
  return (
    <nav className="stage-nav" aria-label="Этапы работы">
      {STAGES.map(([id, label], index) => (
        <a key={id} href={`#${id}`} className={`stage-link ${route === id ? 'active' : ''}`} aria-current={route === id ? 'page' : undefined}>
          <span className="step-number">{index + 1}</span>
          {label}
          {id === 'diff' && hasPatch && <span className="tiny-dot" />}
        </a>
      ))}
    </nav>
  )
}
