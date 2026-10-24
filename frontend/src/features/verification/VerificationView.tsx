import { useCallback, useState } from 'react'
import { Icon } from '../../components/Icon'
import { navigate } from '../../state/route'
import type { JobState } from '../../state/reducer'
import { useAppState } from '../../state/store'
import { GrowthChart } from '../evidence/GrowthChart'
import { ExportDialog } from './ExportDialog'
import { canExport, exportHint } from './exportText'
import { RunComparison, ScenarioTable } from './RunComparison'
import { ProgressPanel, ResultPanel } from './VerdictPanels'

/** Экран 3 «Проверка»: ход проверки, вердикт, вход и результаты, сценарии, условия запуска. */
export function VerificationView() {
  const job = useAppState((s) => s.job)
  const verdict = job?.verdict ?? null
  const running = job !== null && (verdict === null || verdict.status === 'RUNNING')

  return (
    <main id="main-content" className="verification-view" tabIndex={-1}>
      <div className="view-toolbar">
        <div>
          <div className="section-label"><Icon name="compare" />{job ? `Эталон версии ${job.baselineVersion} против: ${job.candidateLabel}` : 'Сравнение версий'}</div>
          <h1>Проверка поведения</h1>
        </div>
        <button type="button" className="btn" onClick={() => navigate('workspace')}><Icon name="pencil" />Вернуться к правке</button>
      </div>
      {!job ? (
        <div className="empty-card">
          <h1>Проверка ещё не запускалась</h1>
          <p>Нажмите «Запустить и сравнить» в шапке: текущий код сравнится с эталоном на одинаковых входах.</p>
        </div>
      ) : (
        <div className="verification-layout">
          <section className="verification-main">
            {running || !verdict ? <ProgressPanel verdict={verdict} /> : <ResultPanel verdict={verdict} />}
            {verdict && !running && <RunComparison verdict={verdict} job={job} />}
            {verdict && !running && <ScenarioTable groups={verdict.groups} inputs={verdict.inputs} />}
          </section>
          <RunConditions job={job} />
        </div>
      )}
    </main>
  )
}

function RunConditions({ job }: { job: JobState }) {
  const verdict = job.verdict
  const done = verdict !== null && verdict.status !== 'RUNNING'
  return (
    <aside className="verification-side">
      <section className="pane">
        <div className="pane-header"><strong>Условия запуска</strong></div>
        <dl className="run-meta">
          <div><dt>Файл</dt><dd>{job.fileName}</dd></div>
          <div><dt>Метод</dt><dd>{job.methodLabel ?? 'вся программа (main)'}</dd></div>
          <div><dt>Версии</dt><dd>эталон {job.baselineVersion} → {job.candidateLabel.startsWith('код') ? `текущая ${job.projectVersion}` : 'заплатка'}</dd></div>
          <div><dt>Цель</dt><dd>{job.intent === 'BUGFIX' ? 'исправление дефекта' : 'рефакторинг'}</dd></div>
          <div><dt>Входов</dt><dd>{verdict ? `${verdict.checked} из ${verdict.inputs}` : '—'}</dd></div>
          <div><dt>Время</dt><dd>{done ? `${verdict.durationMs} мс` : '—'}</dd></div>
        </dl>
        <div className="scope-note"><Icon name="info" />Результат относится только к выполненным входам.</div>
      </section>
      <section className="pane growth-side">
        <div className="pane-header"><strong>Рост времени</strong></div>
        <GrowthChart speedup={verdict?.speedup ?? null} />
      </section>
      <ExportButton job={job} />
    </aside>
  )
}

/** «Экспорт теста»: активна у завершённой проверки, которая реально сравнила версии. */
function ExportButton({ job }: { job: JobState }) {
  const projectId = useAppState((s) => s.project?.id ?? null)
  const [open, setOpen] = useState(false)
  const close = useCallback(() => setOpen(false), [])
  const enabled = projectId !== null && canExport(job.verdict)
  return (
    <>
      <button type="button" className="btn export-button" disabled={!enabled} title={exportHint(job.verdict)}
        onClick={() => setOpen(true)}>
        <Icon name="download" />Экспорт теста
      </button>
      {open && projectId !== null && <ExportDialog projectId={projectId} jobId={job.id} onClose={close} />}
    </>
  )
}
