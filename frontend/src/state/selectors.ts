import type { Blueprint, Finding, GraphNode, Patch } from '../api/types'
import type { State } from './reducer'
import { findExpression } from '../features/blueprint/expressionModel'

// Производные значения состояния — обычные функции, без React.

export function selectedNode(state: State): GraphNode | null {
  const id = state.selectedNodeId
  if (!id) return null
  const pool = state.level === 'method' && state.blueprint ? state.blueprint.nodes : state.project?.map.nodes ?? []
  return pool.find((n) => n.id === id) ?? null
}

export function selectedExpression(state: State) {
  return findExpression(selectedNode(state)?.expr, state.selectedExprId)
}

export function currentFinding(state: State): Finding | null {
  return state.findings.find((f) => f.id === state.findingId) ?? state.findings[0] ?? null
}

export function currentPatch(state: State): Patch | null {
  const finding = state.findings.find((f) => f.id === state.findingId)
  return finding?.patches.find((p) => p.id === state.patchId) ?? null
}

/** Самый вложенный узел чертежа, чьи строки содержат line (у вложенных узлов диапазон уже). */
export function deepestNodeAt(blueprint: Blueprint, line: number): GraphNode | null {
  let best: GraphNode | null = null
  for (const node of blueprint.nodes) {
    const [first, last] = node.lines
    if (line < first || line > last) continue
    if (!best || last - first <= best.lines[1] - best.lines[0]) best = node
  }
  return best
}

/** Метод на карте, в строках которого лежит line. */
export function methodAt(nodes: GraphNode[], line: number): GraphNode | null {
  return deepestNodeAt({ methodId: '', label: '', nodes: nodes.filter((n) => n.kind === 'method'), edges: [] }, line)
}

/** Короткое имя метода из id: «M:MatchDemo#countMatches(List<Integer>)» → «countMatches». */
export function methodName(methodId: string): string {
  const start = methodId.indexOf('#') + 1
  const end = methodId.indexOf('(', start)
  return methodId.slice(start, end > 0 ? end : undefined).replace('<init>', 'конструктор')
}

/** Имя класса из id метода или класса: «M:A.B#f()» → «A.B». */
export function className(nodeId: string): string {
  const body = nodeId.slice(2)
  const hash = body.indexOf('#')
  return hash >= 0 ? body.slice(0, hash) : body
}
