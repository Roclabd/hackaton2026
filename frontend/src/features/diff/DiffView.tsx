import { DiffEditor } from '@monaco-editor/react'
import { EDITOR_FONT, EDITOR_FONT_SIZE, EDITOR_LINE_HEIGHT, THEME } from '../../components/editor/monacoSetup'
import { Icon } from '../../components/Icon'
import { applyPatch, runPatchCheck } from '../../state/gateActions'
import { navigate } from '../../state/route'
import { currentFinding, currentPatch } from '../../state/selectors'
import { dispatch, useAppState } from '../../state/store'
import { openFindingDrawer } from '../evidence/findingActions'

/** Экран 2 «Изменение»: diff текущего кода и заплатки, условия, применить / проверить / отклонить. */
export function DiffView() {
  const patch = useAppState(currentPatch)
  const finding = useAppState(currentFinding)
  const draft = useAppState((s) => s.draft)
  const project = useAppState((s) => s.project)
  const running = useAppState((s) => s.job !== null && (s.job.verdict === null || s.job.verdict.status === 'RUNNING'))

  if (!patch || !project) {
    return (
      <main id="main-content" className="diff-view">
        <div className="empty-card">
          <h1>Изменение не выбрано</h1>
          <p>Откройте находку на экране «Код и схема» и нажмите «Посмотреть изменение».</p>
          <button type="button" className="btn" onClick={() => navigate('workspace')}>К коду и схеме</button>
        </div>
      </main>
    )
  }

  const applied = draft === patch.source
  const reject = () => {
    dispatch({ type: 'patchSelected', findingId: finding?.id ?? '', patchId: null })
    navigate('workspace')
  }

  return (
    <main id="main-content" className="diff-view" tabIndex={-1}>
      <div className="view-toolbar">
        <div>
          <div className="section-label"><Icon name="compare" />Просмотр изменения · {patch.intent === 'BUGFIX' ? 'исправление дефекта' : 'рефакторинг'}</div>
          <h1>{patch.title}</h1>
        </div>
        <div className="revision-chip">Версия {project.version} <Icon name="chevron" /> заплатка</div>
      </div>
      <div className="diff-context">
        <Icon name="info" />{patch.description}
        {finding && <button type="button" className="text-button" onClick={() => openFindingDrawer(finding)}>Условия изменения</button>}
      </div>
      <div className="diff-grid diff-grid-single">
        <section className="pane diff-pane">
          <div className="pane-header">
            <div><strong>До</strong><span className="muted">{project.fileName} · текущий код, версия {project.version}</span></div>
            <div><strong>После</strong><span className="muted">заплатка · строки {patch.changedLines[0]}–{patch.changedLines[1]}</span></div>
          </div>
          <div className="code-editor">
            <DiffEditor
              original={draft}
              modified={patch.source}
              language="java"
              theme={THEME}
              options={{ readOnly: true, originalEditable: false, fontFamily: EDITOR_FONT, fontSize: EDITOR_FONT_SIZE, lineHeight: EDITOR_LINE_HEIGHT, minimap: { enabled: false }, automaticLayout: true, scrollBeyondLastLine: false, renderOverviewRuler: false }}
            />
          </div>
        </section>
      </div>
      <div className="diff-bottom">
        <div className="change-summary">
          <Icon name={patch.intent === 'BUGFIX' ? 'alert' : 'check'} />
          <span>{patch.intent === 'BUGFIX' ? 'Меняет поведение — вердикт не будет зелёным' : 'Поведение должно сохраниться'} <span className="muted">· {finding?.conditions.length ?? 0} условий</span></span>
        </div>
        <div>
          <button type="button" className="btn" onClick={reject}>Отклонить</button>
          <button type="button" className="btn btn-blue" disabled={applied} onClick={() => void applyPatch()}><Icon name="check" />{applied ? 'Изменение применено' : 'Применить изменение'}</button>
          <button type="button" className="btn btn-orange" disabled={running} onClick={() => void runPatchCheck()}><Icon name="play" />Проверить изменение</button>
        </div>
      </div>
    </main>
  )
}
