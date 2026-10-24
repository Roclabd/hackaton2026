import type { Api } from './client'
import { ApiError } from './client'
import type { CommandResponse, EditStatementCommand, GateRequest, GraphNode, ProjectResponse, Verdict } from './types'
import { firstSyntaxProblem } from './mock/javaText'
import { buildBlueprint, buildMap } from './mock/mockGraph'
import { findNestedLoops } from './mock/mockFindings'
import { finalVerdict, progressVerdict } from './mock/mockRun'
import matchDemoSource from '../fixtures/MatchDemo.java?raw'
import miniLang from '../fixtures/minilang.project.json'
import exprDemoSource from './mock/ExprDemo.java?raw'

// Демо-данные: ответы по формам docs/ПРОТОТИП_КОНТРАКТ.md без бэкенда.
// Включается только вручную: VITE_USE_MOCKS=1. В интерфейсе подписано «демо-данные».

const miniLangFixture = miniLang as unknown as ProjectResponse
const JOB_DURATION_MS = 1800
const delay = (ms = 120) => new Promise((resolve) => setTimeout(resolve, ms))

const projects = new Map<string, ProjectResponse>()
const jobs = new Map<string, { startedAt: number; final: Verdict }>()

const EXAMPLES = [
  { fileName: 'ExprDemo.java', title: 'Дерево выражений', description: 'Тернарный оператор, instanceof, приведение и лямбды · демо-данные', source: exprDemoSource },
  { fileName: 'MatchDemo.java', title: 'Подсчёт совпадающих пар', description: 'Вложенный цикл и ловушка с null — сценарий демо', source: matchDemoSource },
  { fileName: 'MiniLang.java', title: 'Интерпретатор мини-языка', description: 'Около 1100 строк, 36 классов — карта из настоящего ответа бэка', source: miniLangFixture.source },
]

function mapFor(source: string) {
  return source === miniLangFixture.source ? miniLangFixture.map : buildMap(source)
}

function findProject(id: string): ProjectResponse {
  const project = projects.get(id)
  if (!project) throw new ApiError(404, 'Проект не найден — загрузите файл заново')
  return project
}

/** Новая версия проекта: если код не разбирается — ошибки и карта от последней рабочей версии. */
function saveVersion(previous: ProjectResponse, source: string): ProjectResponse {
  const problem = firstSyntaxProblem(source)
  const next: ProjectResponse = {
    ...previous, version: previous.version + 1, source,
    errors: problem ? [problem] : [],
    map: problem ? previous.map : mapFor(source),
  }
  projects.set(next.id, next)
  return next
}

function methodNode(project: ProjectResponse, methodId: string): GraphNode {
  const method = project.map.nodes.find((n) => n.id === methodId && n.kind === 'method')
  if (!method) throw new ApiError(404, `Метод не найден: ${methodId}`)
  return method
}

/** Подставляет текст в строки узла. Для if/while меняется только условие в скобках. */
function replaceNodeText(source: string, node: GraphNode, text: string): string {
  const lines = source.split('\n')
  const [start, end] = node.lines
  const first = lines[start - 1]
  const indent = first.slice(0, first.length - first.trimStart().length)
  if (node.kind === 'if' || node.kind === 'loop') {
    lines[start - 1] = first.replace(`(${node.code})`, `(${text})`)
    return lines.join('\n')
  }
  lines.splice(start - 1, end - start + 1, indent + text)
  return lines.join('\n')
}

function checkNodeText(node: GraphNode, text: string): string | null {
  if (!text.trim()) return 'Пустой текст узла'
  const needsSemicolon = !['if', 'loop'].includes(node.kind)
  if (needsSemicolon && !text.trim().endsWith(';')) return 'Ожидалась «;» в конце оператора'
  if (!needsSemicolon && text.includes(';')) return 'Условие не может содержать «;»'
  const balance = (open: string, close: string) => text.split(open).length - text.split(close).length
  if (balance('(', ')') !== 0) return 'Круглые скобки не сбалансированы'
  return null
}

function editStatement(id: string, command: EditStatementCommand): CommandResponse {
  const project = findProject(id)
  if (command.baseVersion !== project.version) throw new ApiError(409, 'Версия устарела — проект обновлён')
  const method = project.map.nodes.find((m) => m.kind === 'method' && command.nodeId.startsWith(`${m.id}/`))
  if (!method) throw new ApiError(404, `Узел не найден: ${command.nodeId}`)
  const before = buildBlueprint(project.source, method)
  const node = before.nodes.find((n) => n.id === command.nodeId)
  if (!node || !node.editable) throw new ApiError(400, 'Этот узел нельзя править')
  const problem = checkNodeText(node, command.text)
  if (problem) return { ok: false, compileErrors: [{ line: node.lines[0], column: 1, text: problem }] }
  const next = saveVersion(project, replaceNodeText(project.source, node, command.text.trim()))
  const newMethod = methodNode(next, method.id)
  const blueprint = buildBlueprint(next.source, newMethod)
  const idMap = Object.fromEntries(before.nodes.map((old, i) => [old.id, blueprint.nodes[i]?.id ?? old.id]))
  return {
    ok: true, compileErrors: [], version: next.version, source: next.source, errors: next.errors,
    map: next.map, idMap, methodId: method.id, blueprint,
  }
}

function startGate(id: string, request: GateRequest): { jobId: string } {
  const project = findProject(id)
  const jobId = `j${jobs.size + 1}`
  jobs.set(jobId, { startedAt: Date.now(), final: finalVerdict(request.originalSource ?? project.source, request.source) })
  return { jobId }
}

function getJob(jobId: string): Verdict {
  const job = jobs.get(jobId)
  if (!job) throw new ApiError(404, 'Задача проверки не найдена')
  return progressVerdict(job.final, Date.now() - job.startedAt, JOB_DURATION_MS)
}

function upload(fileName: string, source: string): ProjectResponse {
  const id = Math.random().toString(16).slice(2, 10)
  const problem = firstSyntaxProblem(source)
  const project: ProjectResponse = {
    id, version: 1, fileName, source, errors: problem ? [problem] : [],
    map: problem ? { nodes: [], edges: [] } : mapFor(source),
  }
  projects.set(id, project)
  return project
}

const after = async <T>(make: () => T): Promise<T> => {
  await delay()
  return structuredClone(make())
}

export const mockApi: Api = {
  health: () => after(() => ({ status: 'ok' })),
  uploadProject: (fileName, source) => after(() => upload(fileName, source)),
  getProject: (id) => after(() => findProject(id)),
  updateSource: (id, source, baseVersion) => after(() => {
    const project = findProject(id)
    if (baseVersion !== project.version) throw new ApiError(409, 'Версия устарела — проект обновлён')
    return saveVersion(project, source)
  }),
  getMethod: (id, methodId) => after(() => {
    const project = findProject(id)
    return buildBlueprint(project.source, methodNode(project, methodId))
  }),
  editStatement: (id, command) => after(() => editStatement(id, command)),
  getFindings: (id) => after(() => {
    const project = findProject(id)
    return project.errors.length ? [] : findNestedLoops(project.source, project.map)
  }),
  startGate: (id, request) => after(() => startGate(id, request)),
  getJob: (jobId) => after(() => getJob(jobId)),
  listExamples: () => after(() => EXAMPLES.map(({ fileName, title, description }) => ({ fileName, title, description }))),
  getExample: (fileName) => after(() => {
    const example = EXAMPLES.find((e) => e.fileName === fileName)
    if (!example) throw new ApiError(404, `Пример не найден: ${fileName}`)
    return { fileName, source: example.source }
  }),
}
