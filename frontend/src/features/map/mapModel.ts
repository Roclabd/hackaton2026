import type { Graph, GraphNode } from '../../api/types'
import { className } from '../../state/selectors'

// Карта уровня 0: класс = карточка со списком методов; вызовы агрегированы по парам классов.

export interface ClassCard {
  id: string
  /** короткое имя для заголовка: «Token» */
  name: string
  /** полное имя с внешними классами: «MiniLang.Token» */
  fullName: string
  /** class / enum / interface / record */
  kind: string
  methods: GraphNode[]
  fieldCount: number
  calls: { target: string; count: number }[]
  lines: [number, number]
}

export function buildClassCards(graph: Graph): ClassCard[] {
  const byParent = new Map<string, GraphNode[]>()
  for (const node of graph.nodes) {
    if (!node.parent) continue
    byParent.set(node.parent, [...(byParent.get(node.parent) ?? []), node])
  }
  const calls = aggregateCalls(graph)
  return graph.nodes
    .filter((n) => n.kind === 'class')
    .sort((a, b) => a.lines[0] - b.lines[0])
    .map((node) => {
      const children = byParent.get(node.id) ?? []
      const fullName = displayClassName(className(node.id))
      return {
        id: node.id,
        name: fullName.slice(fullName.lastIndexOf('.') + 1),
        fullName,
        kind: node.label.split(' ')[0],
        methods: children.filter((c) => c.kind === 'method').sort((a, b) => a.lines[0] - b.lines[0]),
        fieldCount: children.filter((c) => c.kind === 'field').length,
        calls: calls.get(node.id) ?? [],
        lines: node.lines,
      }
    })
}

/** Сколько вызовов идёт из методов класса в методы других классов. */
function aggregateCalls(graph: Graph): Map<string, { target: string; count: number }[]> {
  const counts = new Map<string, Map<string, number>>()
  for (const edge of graph.edges) {
    if (edge.kind !== 'call') continue
    const from = `C:${className(edge.from)}`
    const to = className(edge.to)
    if (from === `C:${to}`) continue
    const row = counts.get(from) ?? new Map<string, number>()
    row.set(to, (row.get(to) ?? 0) + 1)
    counts.set(from, row)
  }
  const result = new Map<string, { target: string; count: number }[]>()
  for (const [from, row] of counts) {
    result.set(from, [...row].map(([target, count]) => ({ target: displayClassName(target), count })).sort((a, b) => b.count - a.count))
  }
  return result
}

/** Убираем техническое имя владельца enum-константы только из отображаемого текста. */
function displayClassName(name: string): string {
  return name.replace(/<constant:([^>]+)>/g, '$1')
}

/** Подпись метода без модификаторов: «static long countMatches(List<Integer>)» → «countMatches(List<Integer>)». */
export function methodSignature(label: string): string {
  const paren = label.indexOf('(')
  const head = paren >= 0 ? label.slice(0, paren) : label
  const name = head.trim().split(/\s+/).pop() ?? head
  return paren >= 0 ? `${name}${label.slice(paren)}` : name
}
