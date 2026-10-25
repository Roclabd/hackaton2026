import { measureHeat } from '../api/heatApi'
import { flushDraft, reportError } from './actions'
import { dispatch, store } from './store'

let requestCounter = 0

export async function toggleHeat(): Promise<void> {
  const before = store.getState()
  if (before.level !== 'method' || !before.project || !before.methodId || before.heat?.loading) return
  if (before.heat?.result) {
    dispatch({ type: 'heatCleared' })
    return
  }
  const projectId = before.project.id
  const methodId = before.methodId
  await flushDraft()
  const state = store.getState()
  if (state.level !== 'method' || state.project?.id !== projectId || state.methodId !== methodId
    || state.blueprint?.methodId !== methodId || state.draft !== state.project.source || state.heat?.loading) return
  const requestId = ++requestCounter
  dispatch({ type: 'heatStarted', heat: { requestId, projectId, version: state.project.version, methodId, loading: true, result: null } })
  try {
    const result = await measureHeat(projectId, methodId)
    dispatch({ type: 'heatLoaded', requestId, result })
  } catch (error) {
    // Правка, смена метода или проекта уже сделала этот запрос неактуальным.
    if (store.getState().heat?.requestId !== requestId) return
    dispatch({ type: 'heatCleared', requestId })
    reportError(error)
  }
}
