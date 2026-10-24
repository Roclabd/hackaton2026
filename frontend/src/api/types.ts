// Типы ответов API — зеркало docs/API.md и docs/ПРОТОТИП_КОНТРАКТ.md.
// Меняются только вместе с контрактом и фикстурами.

export type NodeKind =
  | 'class' | 'field' | 'method'
  | 'stmt' | 'if' | 'loop' | 'switch' | 'try'
  | 'return' | 'throw' | 'break' | 'continue' | 'raw'

export type EdgeKind = 'call' | 'exec'

/** Ветка контейнера на чертеже: then «да», else «нет», body «тело», case:N, catch:N, finally. */
export interface GraphBranch {
  key: string
  label: string
}

/** Оценка сложности по структуре кода — у методов на карте и у циклов на чертеже. */
export interface Complexity {
  /** «O(1)», «O(log n)», «O(n)», «O(n log n)», «O(n²)»… или «рекурсия» */
  big: string
  level: 'low' | 'linear' | 'high' | 'unknown'
  /** откуда оценка, по-русски, со строками */
  why: string
}

export interface GraphNode {
  id: string
  kind: NodeKind
  label: string
  code: string | null
  /** [первая, последняя] строка в исходнике, с 1 */
  lines: [number, number]
  parent: string | null
  /** then / else / body / case:N / catch:N / finally — только на чертеже метода */
  branch: string | null
  badges: string[]
  /** можно ли править узел командой editStatement (на карте поля может не быть) */
  editable?: boolean
  /** ветки контейнера в порядке показа; у остальных узлов — null */
  branches?: GraphBranch[] | null
  /** дерево выражения оператора — только на чертеже метода; нет выражения — null */
  expr?: ExprNode | null
  /** оценка сложности; на бэке без неё поля нет */
  complexity?: Complexity | null
}

export type ExprKind =
  | 'call' | 'new' | 'binary' | 'concat' | 'unary' | 'assign' | 'ternary' | 'instanceof' | 'cast'
  | 'lambda' | 'methodRef' | 'literal' | 'name' | 'field' | 'index' | 'arrayInit' | 'switchExpr'
  | 'declare' | 'forHeader' | 'forEachHeader' | 'resources' | 'block' | 'other'

/** [строка, колонка, строка конца, колонка конца], с 1 включительно. */
export type ExprRange = [number, number, number, number]

/** Узел дерева выражения (docs/API.md, «Дерево выражения ExprNode»). */
export interface ExprNode {
  /** id оператора + «/e» + путь индексов детей: «…/e/0/1» */
  id: string
  kind: ExprKind
  /** коротко: имя метода, знак операции, тип, значение литерала */
  label: string
  /** исходный текст выражения, до 200 символов */
  code: string
  range: ExprRange
  /** роль в родителе по-русски: «арг 1», «условие», «да»…; у корня — null */
  role: string | null
  children: ExprNode[]
}

export interface GraphEdge {
  id: string
  from: string
  to: string
  kind: EdgeKind
  /** для call: «вызов» — точно, «вызов?» — сопоставлено по имени */
  label: string
}

export interface Graph {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

export interface ParseError {
  line: number
  column: number
  message: string
}

export interface ProjectResponse {
  id: string
  version: number
  fileName: string
  source: string
  /** пусто — код разбирается; иначе map — от последней рабочей версии */
  errors: ParseError[]
  /** В старых ответах поля нет; это не заменяет ошибки разбора. */
  compileErrors?: CompileError[]
  map: Graph
}

/** Чертёж метода — GET /methods/{methodId}. */
export interface Blueprint {
  methodId: string
  label: string
  nodes: GraphNode[]
  edges: GraphEdge[]
}

/** Ошибка компиляции или разбора, текст по-русски. */
export interface CompileError {
  line: number
  column: number
  text: string
}

export interface EditStatementCommand {
  type: 'editStatement'
  nodeId: string
  text: string
  baseVersion: number
}

/** Ответ POST /commands. При ok:false заполнено только compileErrors. */
export interface CommandResponse {
  ok: boolean
  compileErrors: CompileError[]
  version?: number
  source?: string
  errors?: ParseError[]
  map?: Graph
  idMap?: Record<string, string>
  methodId?: string
  blueprint?: Blueprint
}

export interface RunResult {
  stdout: string
  stderr: string
  exitCode: number
  exception: { type: string; message: string | null } | null
  timedOut: boolean
  durationMs: number
}

export type Intent = 'REFACTOR' | 'BUGFIX'

export interface Patch {
  id: string
  intent: Intent
  title: string
  description: string
  /** полный текст файла с заплаткой */
  source: string
  changedLines: [number, number]
}

export interface Finding {
  id: string
  rule: string
  methodId: string
  lines: [number, number]
  title: string
  why: string
  /** Старый бэкенд присылает R1 без категории. */
  category?: 'DEFECT' | 'INEFFICIENCY' | 'DEAD_CODE'
  growthBefore: string | null
  growthAfter: string | null
  conditions: string[]
  patches: Patch[]
}

export interface GateRequest {
  source: string
  intent: Intent
  /** Эталон сравнения; поддерживается GateRoutes. Без поля берётся текущий проект. */
  originalSource?: string
}

export type VerdictStatus = 'MATCHED' | 'MISMATCH' | 'INCOMPLETE' | 'BUILD_ERROR' | 'BEHAVIOR_CHANGE'
export type JobStatus = 'RUNNING' | VerdictStatus
export type JobPhase = 'compile' | 'run' | 'compare' | 'done'

export interface RunPair {
  input: string
  group: string
  original: RunResult
  candidate: RunResult
}

export interface ScenarioGroup {
  name: string
  total: number
  checked: number
  matched: number
  status: string
}

export interface ChangedCoverageStatement {
  line: number
  /** Число разных входов, на которых выполнился оператор. */
  inputs: number
  /** В старых ответах стороны нет; true — эталон, false — кандидат. */
  onOriginal?: boolean
}

export interface ChangedCoverage {
  /** Имя поля сохранено в API: это количество операторов, а не уникальных строк. */
  lines: number
  covered: number
  /** Минимальное число разных входов среди измеренных операторов. */
  minHits: number
  inputs?: number
  inputsReached?: number
  onOriginal?: boolean
  statements?: ChangedCoverageStatement[]
}

/** Ответ GET /api/jobs/{jobId}: ход и итог проверки. */
export interface Verdict {
  status: JobStatus
  phase: JobPhase
  progress: number
  inputs: number
  checked: number
  /** на скольких входах эталон и кандидат реально сравнены и совпали — число для «Совпало на N» */
  matched: number
  mismatches: number
  nondeterministic: number
  incompleteReason: string | null
  buildErrors: CompileError[]
  smallestMismatch: RunPair | null
  example: RunPair | null
  groups: ScenarioGroup[]
  changedCovered: ChangedCoverage | null
  speedup: { n: number; before: string; after: string; factor: number } | null
  durationMs: number
}

export interface ExampleInfo {
  fileName: string
  title: string
  description: string
}

export interface ExampleSource {
  fileName: string
  source: string
}
