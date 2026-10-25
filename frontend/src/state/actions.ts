import { api, ApiError } from '../api/client'
import type { CompileError, ProjectResponse } from '../api/types'
import { requestReveal } from './reveal'
import { navigate } from './route'
import { deepestNodeAt, methodAt } from './selectors'
import { dispatch, store } from './store'

// Асинхронные шаги интерфейса: запрос к API → действие в стор. Ошибки показываем тостом.

const SAVE_DELAY_MS = 600
const SESSION_KEY = 'blueprints.projectId'
let toastCounter = 0
let saveTimer: number | undefined
let saving: Promise<void> = Promise.resolve()

export function showToast(text: string, tone: 'info' | 'error' = 'info'): void {
  const id = ++toastCounter
  dispatch({ type: 'toast', toast: { id, text, tone } })
  setTimeout(() => dispatch({ type: 'dismissToast', id }), tone === 'error' ? 6000 : 3000)
}

export function reportError(error: unknown): void {
  showToast(error instanceof Error ? error.message : String(error), 'error')
}

function rememberProject(id: string) {
  try { sessionStorage.setItem(SESSION_KEY, id) } catch { /* хранилище недоступно — не страшно */ }
}

async function openProject(project: ProjectResponse): Promise<void> {
  dispatch({ type: 'projectLoaded', project })
  rememberProject(project.id)
  navigate('workspace')
  await refreshFindings()
}

/** После перезагрузки страницы — вернуть проект с сервера, если он там ещё есть. */
export async function restoreSession(): Promise<void> {
  let id: string | null = null
  try { id = sessionStorage.getItem(SESSION_KEY) } catch { return }
  if (!id) return
  try {
    await openProject(await api.getProject(id))
  } catch {
    try { sessionStorage.removeItem(SESSION_KEY) } catch { /* ничего */ }
  }
}

export async function loadExample(fileName: string): Promise<void> {
  try {
    const example = await api.getExample(fileName)
    await openProject(await api.uploadProject(example.fileName, example.source))
    dispatch({ type: 'closeDialog' })
    showToast(`Открыт пример ${example.fileName}`)
  } catch (error) { reportError(error) }
}

export async function uploadFile(file: File): Promise<void> {
  try {
    await openProject(await api.uploadProject(file.name, await file.text()))
    showToast(`Загружен ${file.name}`)
  } catch (error) { reportError(error) }
}

export async function refreshFindings(): Promise<void> {
  const project = store.getState().project
  if (!project || project.errors.length) return
  try {
    dispatch({ type: 'findingsLoaded', findings: await api.getFindings(project.id) })
  } catch (error) { reportError(error) }
}

export async function openMethod(methodId: string, selectLine?: number): Promise<void> {
  const project = store.getState().project
  if (!project) return
  try {
    const blueprint = await api.getMethod(project.id, methodId)
    const node = selectLine === undefined ? null : deepestNodeAt(blueprint, selectLine)
    dispatch({ type: 'methodOpened', blueprint, selectedNodeId: node?.id ?? null })
  } catch (error) { reportError(error) }
}

/** Перечитать чертёж открытого метода после изменения кода; метод исчез — назад к карте. */
export async function refreshBlueprint(): Promise<void> {
  const { project, methodId, level } = store.getState()
  if (!project || !methodId || level !== 'method') return
  if (!project.map.nodes.some((n) => n.id === methodId)) {
    dispatch({ type: 'blueprintRefreshed', blueprint: null })
    return
  }
  try {
    dispatch({ type: 'blueprintRefreshed', blueprint: await api.getMethod(project.id, methodId) })
  } catch (error) { reportError(error) }
}

/** Правка в редакторе: сохраняем через SAVE_DELAY_MS после последнего нажатия. */
export function editDraft(text: string): void {
  dispatch({ type: 'draftChanged', text })
  window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(() => { void flushDraft() }, SAVE_DELAY_MS)
}

/** Отправить несохранённый текст редактора (PUT /source). Вызовы идут строго по очереди. */
export function flushDraft(): Promise<void> {
  window.clearTimeout(saveTimer)
  saving = saving.then(saveDraftNow)
  return saving
}

export async function acceptCurrentBaseline(): Promise<void> {
  await flushDraft()
  const { project, draft, job } = store.getState()
  if (!project || project.source !== draft || project.errors.length || project.compileErrors?.length) {
    showToast('Сначала сохраните код и исправьте ошибки компиляции', 'error')
    return
  }
  if (job && (!job.verdict || job.verdict.status === 'RUNNING')) return
  dispatch({ type: 'baselineAccepted' })
  showToast(`Версия ${project.version} выбрана эталоном для следующих проверок`)
}

async function saveDraftNow(): Promise<void> {
  const { project, draft } = store.getState()
  if (!project || draft === project.source) return
  try {
    const saved = await api.updateSource(project.id, draft, project.version)
    dispatch({ type: 'projectSaved', project: saved })
    if (saved.errors.length === 0) await Promise.all([refreshBlueprint(), refreshFindings()])
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      const fresh = await api.getProject(project.id)
      dispatch({ type: 'projectSaved', project: fresh })
      showToast('Версия устарела — код обновлён с сервера', 'error')
      return
    }
    reportError(error)
  }
}

/** Правка узла схемы. Возвращает ошибки компиляции (диалог остаётся открытым) или пустой список. */
export async function editNode(nodeId: string, text: string): Promise<CompileError[]> {
  await flushDraft()
  const project = store.getState().project
  if (!project) return []
  try {
    const response = await api.editStatement(project.id, { type: 'editStatement', nodeId, text, baseVersion: project.version })
    if (!response.ok) return response.compileErrors.length ? response.compileErrors : [{ line: 0, column: 0, text: 'Правка не применена' }]
    dispatch({ type: 'commandApplied', response })
    showToast(`Узел изменён · версия ${response.version}`)
    void refreshFindings()
    return []
  } catch (error) {
    reportError(error)
    return [{ line: 0, column: 0, text: error instanceof Error ? error.message : 'Ошибка запроса' }]
  }
}

/** Клик по строке кода: выбрать самый вложенный узел чертежа (или метод на карте). */
/**
 * «Перейти к строке»: редактор прокручивается к ней всегда, а схема показывает её узел —
 * если строка в другом методе, открывается его чертёж.
 */
export async function goToLine(line: number): Promise<void> {
  navigate('workspace')
  requestReveal(line)
  const { level, blueprint, project } = store.getState()
  if (level === 'method' && blueprint && deepestNodeAt(blueprint, line)) {
    selectByLine(line)
    return
  }
  const method = methodAt(project?.map.nodes ?? [], line)
  if (method) await openMethod(method.id, line)
}

export function selectByLine(line: number): void {
  const { level, blueprint, project } = store.getState()
  if (level === 'method' && blueprint) {
    const node = deepestNodeAt(blueprint, line)
    if (node) dispatch({ type: 'select', nodeId: node.id })
    return
  }
  const method = methodAt(project?.map.nodes ?? [], line)
  if (method) dispatch({ type: 'select', nodeId: method.id })
}
