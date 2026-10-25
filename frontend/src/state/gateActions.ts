import { api } from '../api/client'
import type { Intent } from '../api/types'
import { draftSaved, flushDraft, isCurrentProject, refreshBlueprint, refreshFindings, reportError, showToast } from './actions'
import type { JobState } from './reducer'
import { navigate } from './route'
import { currentPatch } from './selectors'
import { dispatch, store } from './store'

// Проверка поведения: POST /gate → опрос GET /api/jobs/{id} раз в 300 мс до итогового статуса.

const POLL_MS = 300
const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

interface Candidate {
  source: string
  intent: Intent
  label: string
}

/** «Запустить и сравнить»: текущий код редактора против эталона (версия до правок). */
export async function runEditorCheck(): Promise<void> {
  await flushDraft()
  if (!draftSaved()) { showToast('Код редактора не сохранён — проверка не запущена', 'error'); return }
  const { draft, project } = store.getState()
  await startCheck({ source: draft, intent: 'REFACTOR', label: `код редактора · версия ${project?.version ?? '—'}` })
}

/** «Проверить изменение»: текст заплатки против эталона. */
export async function runPatchCheck(): Promise<void> {
  const patch = currentPatch(store.getState())
  if (!patch) return
  await startCheck({ source: patch.source, intent: patch.intent, label: `заплатка «${patch.title}»` })
}

async function startCheck(candidate: Candidate): Promise<void> {
  const { project, baseline, blueprint, job, draft } = store.getState()
  if (!project || !baseline) return
  if (job && job.verdict?.status === 'RUNNING') return
  try {
    const { jobId } = await api.startGate(project.id, {
      source: candidate.source, intent: candidate.intent, originalSource: baseline.source,
    })
    // пока ждали ответа, открыли другой проект — эта проверка к нему не относится
    if (!isCurrentProject(project.id)) return
    const started: JobState = {
      id: jobId, projectId: project.id, verdict: null, candidateLabel: candidate.label, candidateSource: candidate.source, draftAtStart: draft,
      intent: candidate.intent, baselineVersion: baseline.version, projectVersion: project.version,
      fileName: project.fileName, methodLabel: blueprint?.label ?? null,
    }
    dispatch({ type: 'jobStarted', job: started })
    navigate('verification')
    await pollJob(jobId)
  } catch (error) { reportError(error) }
}

async function pollJob(jobId: string): Promise<void> {
  for (;;) {
    if (store.getState().job?.id !== jobId) return
    try {
      const verdict = await api.getJob(jobId)
      dispatch({ type: 'jobUpdated', jobId, verdict })
      if (verdict.status !== 'RUNNING') return
    } catch (error) {
      dispatch({ type: 'jobFailed', jobId })
      reportError(error)
      return
    }
    await sleep(POLL_MS)
  }
}

/** «Применить изменение»: заплатка становится текущим кодом и новым эталоном. */
export async function applyPatch(): Promise<void> {
  await flushDraft()
  if (!draftSaved()) { showToast('Код редактора не сохранён — изменение не применено', 'error'); return }
  const state = store.getState()
  const patch = currentPatch(state)
  if (!state.project || !patch) return
  try {
    const project = await api.updateSource(state.project.id, patch.source, state.project.version)
    if (!isCurrentProject(project.id)) return
    dispatch({ type: 'patchApplied', project })
    await Promise.all([refreshBlueprint(), refreshFindings()])
    showToast(`Изменение применено · версия ${project.version}`)
    navigate('workspace')
  } catch (error) { reportError(error) }
}
