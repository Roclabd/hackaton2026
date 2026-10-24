import { Icon } from '../../components/Icon'
import { dispatch, useAppState } from '../../state/store'
import { EvidencePanel } from '../evidence/EvidencePanel'
import { CodePane } from './CodePane'
import { GraphPane } from './GraphPane'

/** Экран 1 «Код и схема»: код и схема рядом, внизу — находки. */
export function WorkspaceView() {
  const hasProject = useAppState((s) => s.project !== null)
  if (!hasProject) return <EmptyWorkspace />
  return (
    <main id="main-content" className="workspace-view" tabIndex={-1}>
      <section className="workbench" aria-label="Код и схема">
        <CodePane />
        <GraphPane />
      </section>
      <EvidencePanel />
    </main>
  )
}

function EmptyWorkspace() {
  return (
    <main id="main-content" className="empty-view">
      <div className="empty-card">
        <Icon name="nodes" className="empty-icon" />
        <h1>Откройте Java-программу</h1>
        <p>Код и схема появятся рядом. Выберите готовый пример или загрузите свой файл .java кнопкой в шапке.</p>
        <button type="button" className="btn btn-blue" onClick={() => dispatch({ type: 'openDialog', dialog: 'examples' })}>
          <Icon name="file" />Открыть пример
        </button>
      </div>
    </main>
  )
}
