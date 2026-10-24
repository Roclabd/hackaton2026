import { useEffect } from 'react'
import { IconSprite } from './components/Icon'
import { Toast } from './components/Toast'
import { NodeEditorDialog } from './features/blueprint/NodeEditorDialog'
import { DiffView } from './features/diff/DiffView'
import { FindingDrawer } from './features/evidence/FindingDrawer'
import { ExamplesDialog } from './features/examples/ExamplesDialog'
import { Header } from './features/shell/Header'
import { StatusBar } from './features/shell/StatusBar'
import { VerificationView } from './features/verification/VerificationView'
import { WorkspaceView } from './features/workspace/WorkspaceView'
import { restoreSession } from './state/actions'
import { useRoute } from './state/route'
import { useAppState } from './state/store'

const TITLES = { workspace: 'Код и схема', diff: 'Изменение', verification: 'Проверка' }

/** Каркас: шапка с этапами, экран по хэшу, строка состояния, окна поверх. */
export function App() {
  const route = useRoute()
  const dialog = useAppState((s) => s.dialog)

  useEffect(() => { void restoreSession() }, [])
  useEffect(() => { document.title = `${TITLES[route]} · Java Blueprints` }, [route])

  return (
    <>
      <IconSprite />
      <div id="app">
        <Header />
        {route === 'workspace' && <WorkspaceView />}
        {route === 'diff' && <DiffView />}
        {route === 'verification' && <VerificationView />}
        <StatusBar />
      </div>
      {dialog === 'examples' && <ExamplesDialog />}
      {dialog === 'nodeEditor' && <NodeEditorDialog />}
      {dialog === 'finding' && <FindingDrawer />}
      <Toast />
    </>
  )
}
