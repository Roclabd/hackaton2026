import type { Blueprint, CommandResponse, Finding, HeatResult, Intent, ProjectResponse, Verdict } from '../api/types'
import { findExpression } from '../features/blueprint/expressionModel'

export type Level = 'map' | 'method'
export type DialogKind = 'examples' | 'nodeEditor' | 'finding' | null

/** Эталон для проверки: код на момент загрузки проекта или последнего применения заплатки. */
export interface Baseline {
  source: string
  version: number
  invalid: boolean
}

export interface JobState {
  id: string
  /** проект, к которому относится проверка: экспорт берёт его, а не текущий */
  projectId: string
  verdict: Verdict | null
  /** что проверяем: «код редактора» или название заплатки */
  candidateLabel: string
  candidateSource: string
  /** текст редактора при запуске: у проверки заплатки он равен оригиналу, а не кандидату */
  draftAtStart: string
  intent: Intent
  baselineVersion: number
  /** текущая версия проекта на момент запуска */
  projectVersion: number
  fileName: string
  methodLabel: string | null
}

export interface Toast {
  id: number
  text: string
  tone: 'info' | 'error'
}

export interface HeatState {
  requestId: number
  projectId: string
  version: number
  methodId: string
  loading: boolean
  result: HeatResult | null
}

export interface State {
  project: ProjectResponse | null
  /** текст в редакторе; может опережать project.source, пока правка не сохранена */
  draft: string
  baseline: Baseline | null
  level: Level
  methodId: string | null
  blueprint: Blueprint | null
  heat: HeatState | null
  selectedNodeId: string | null
  selectedExprId: string | null
  detailedExpressions: boolean
  findings: Finding[]
  findingId: string | null
  patchId: string | null
  job: JobState | null
  zoom: number
  dialog: DialogKind
  toast: Toast | null
}

export const initialState: State = {
  project: null, draft: '', baseline: null, level: 'map', methodId: null, blueprint: null, heat: null,
  selectedNodeId: null, selectedExprId: null, detailedExpressions: true, findings: [], findingId: null, patchId: null, job: null,
  zoom: 100, dialog: null, toast: null,
}

export type Action =
  | { type: 'projectLoaded'; project: ProjectResponse }
  | { type: 'projectSaved'; project: ProjectResponse }
  | { type: 'patchApplied'; project: ProjectResponse }
  | { type: 'commandApplied'; response: CommandResponse }
  | { type: 'draftChanged'; text: string }
  | { type: 'methodOpened'; blueprint: Blueprint; selectedNodeId: string | null }
  | { type: 'blueprintRefreshed'; blueprint: Blueprint | null }
  | { type: 'showMap' }
  | { type: 'select'; nodeId: string | null }
  | { type: 'selectExpr'; nodeId: string; expressionId: string }
  | { type: 'toggleExpressions' }
  | { type: 'heatStarted'; heat: HeatState }
  | { type: 'heatLoaded'; requestId: number; result: HeatResult }
  | { type: 'heatCleared'; requestId?: number }
  | { type: 'baselineAccepted' }
  | { type: 'findingsLoaded'; findings: Finding[] }
  | { type: 'patchSelected'; findingId: string; patchId: string | null }
  | { type: 'jobStarted'; job: JobState }
  | { type: 'jobUpdated'; jobId: string; verdict: Verdict }
  | { type: 'jobFailed'; jobId: string }
  | { type: 'zoom'; value: number }
  | { type: 'openDialog'; dialog: DialogKind }
  | { type: 'closeDialog' }
  | { type: 'toast'; toast: Toast }
  | { type: 'dismissToast'; id: number }

export function reducer(state: State, action: Action): State {
  switch (action.type) {
    case 'projectLoaded': {
      const { project } = action
      return {
        ...initialState, project, draft: project.source,
        baseline: baselineFor(project),
      }
    }
    case 'projectSaved':
      // поздний ответ сохранения уже закрытого проекта не должен подменить открытый
      if (state.project?.id !== action.project.id) return state
      return { ...state, project: action.project, selectedExprId: null, heat: null }
    case 'patchApplied':
      if (state.project?.id !== action.project.id) return state
      return {
        ...state, project: action.project, draft: action.project.source,
        baseline: baselineFor(action.project), selectedExprId: null,
        findingId: null, patchId: null, heat: null,
      }
    case 'commandApplied':
      return applyCommand(state, action.response)
    case 'draftChanged':
      return { ...state, draft: action.text, selectedExprId: null, heat: null }
    case 'methodOpened':
      return { ...state, level: 'method', methodId: action.blueprint.methodId, blueprint: action.blueprint, selectedNodeId: action.selectedNodeId, selectedExprId: null, zoom: 100, heat: null }
    case 'blueprintRefreshed':
      return action.blueprint
        ? { ...state, blueprint: action.blueprint, selectedNodeId: keepSelection(state.selectedNodeId, action.blueprint), selectedExprId: null, heat: null }
        : { ...state, level: 'map', blueprint: null, methodId: null, selectedNodeId: null, selectedExprId: null, heat: null }
    case 'showMap':
      return { ...state, level: 'map', selectedNodeId: state.methodId, selectedExprId: null, zoom: 100, heat: null }
    case 'select':
      return { ...state, selectedNodeId: action.nodeId, selectedExprId: null }
    case 'selectExpr': {
      const node = state.blueprint?.nodes.find((item) => item.id === action.nodeId)
      if (state.level !== 'method' || state.draft !== state.project?.source || state.project?.errors.length || !findExpression(node?.expr, action.expressionId)) return state
      return { ...state, selectedNodeId: action.nodeId, selectedExprId: action.expressionId }
    }
    case 'toggleExpressions':
      return { ...state, detailedExpressions: !state.detailedExpressions, selectedExprId: null }
    case 'heatStarted':
      return heatIsCurrent(state, action.heat) ? { ...state, heat: action.heat } : state
    case 'heatLoaded':
      return state.heat?.requestId === action.requestId && heatIsCurrent(state, state.heat)
        && action.result.methodId === state.methodId
        ? { ...state, heat: { ...state.heat, loading: false, result: action.result } } : state
    case 'heatCleared':
      return action.requestId === undefined || state.heat?.requestId === action.requestId ? { ...state, heat: null } : state
    case 'baselineAccepted':
      return state.baseline?.invalid && state.project && state.project.source === state.draft
        && !state.project.errors.length && !state.project.compileErrors?.length
        && (!state.job || (state.job.verdict && state.job.verdict.status !== 'RUNNING'))
        ? { ...state, baseline: baselineFor(state.project), job: null } : state
    case 'findingsLoaded':
      return { ...state, findings: action.findings }
    case 'patchSelected':
      return { ...state, findingId: action.findingId, patchId: action.patchId }
    case 'jobStarted':
      return state.project?.id === action.job.projectId ? { ...state, job: action.job } : state
    case 'jobUpdated':
      return state.job?.id === action.jobId ? { ...state, job: { ...state.job, verdict: action.verdict } } : state
    case 'jobFailed':
      return state.job?.id === action.jobId ? { ...state, job: null } : state
    case 'zoom':
      return { ...state, zoom: action.value }
    case 'openDialog':
      return { ...state, dialog: action.dialog }
    case 'closeDialog':
      return { ...state, dialog: null }
    case 'toast':
      return { ...state, toast: action.toast }
    case 'dismissToast':
      return state.toast?.id === action.id ? { ...state, toast: null } : state
  }
}

/** Правка узла: новый код, карта и чертёж; выделение переносим по idMap. */
function applyCommand(state: State, response: CommandResponse): State {
  if (!state.project || !response.ok || response.source === undefined) return state
  const project: ProjectResponse = {
    ...state.project,
    version: response.version ?? state.project.version,
    source: response.source,
    errors: response.errors ?? [],
    compileErrors: response.compileErrors ?? [],
    map: response.map ?? state.project.map,
  }
  const selected = state.selectedNodeId
  return {
    ...state, project, draft: response.source, selectedExprId: null, heat: null,
    blueprint: response.blueprint ?? state.blueprint,
    methodId: response.methodId ?? state.methodId,
    selectedNodeId: selected ? response.idMap?.[selected] ?? selected : null,
  }
}

function heatIsCurrent(state: State, heat: HeatState): boolean {
  return state.level === 'method' && state.methodId === heat.methodId && state.blueprint?.methodId === heat.methodId
    && state.project?.id === heat.projectId && state.project.version === heat.version && state.draft === state.project.source
}

function baselineFor(project: ProjectResponse): Baseline {
  return { source: project.source, version: project.version, invalid: !!(project.errors.length || project.compileErrors?.length) }
}

function keepSelection(selected: string | null, blueprint: Blueprint): string | null {
  return selected && blueprint.nodes.some((n) => n.id === selected) ? selected : null
}
