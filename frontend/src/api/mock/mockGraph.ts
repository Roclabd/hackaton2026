import type { Blueprint, Graph, GraphEdge, GraphNode, NodeKind } from '../types'
import { blockEnd, braceDelta, codeOnly, paramType, shortHash, splitParams } from './javaText'
import { mockExpr } from './mockExpr'

// Демо-данные: карта и чертёж метода, построенные построчно по исходнику (только режим моков).

const CLASS_RE = /^\s*(?:(?:public|private|protected|static|final|abstract)\s+)*(class|interface|enum|record)\s+(\w+)/
const METHOD_RE = /^\s*((?:(?:public|private|protected|static|final|synchronized|abstract)\s+)*)([\w<>[\],.? ]+?)\s+(\w+)\s*\(([^)]*)\)\s*(?:throws [\w., ]+)?\{\s*$/

function node(partial: Partial<GraphNode> & Pick<GraphNode, 'id' | 'kind' | 'label' | 'lines'>): GraphNode {
  return { code: null, parent: null, branch: null, badges: [], editable: false, branches: null, ...partial }
}

export function buildMap(source: string): Graph {
  const lines = source.split('\n')
  const nodes: GraphNode[] = []
  const classStack: { id: string; name: string; end: number }[] = []
  lines.forEach((line, index) => {
    const number = index + 1
    while (classStack.length && classStack[classStack.length - 1].end < number) classStack.pop()
    const outer = classStack[classStack.length - 1]
    const classMatch = CLASS_RE.exec(line)
    if (classMatch && line.includes('{')) {
      const name = outer ? `${outer.name}.${classMatch[2]}` : classMatch[2]
      const end = blockEnd(lines, number)
      nodes.push(node({ id: `C:${name}`, kind: 'class', label: `${classMatch[1]} ${classMatch[2]}`, lines: [number, end], parent: outer?.id ?? null }))
      classStack.push({ id: `C:${name}`, name, end })
      return
    }
    const methodMatch = METHOD_RE.exec(line)
    if (methodMatch && outer && !/^\s*(if|for|while|switch|return|new)\b/.test(line)) {
      const [, modifiers, returnType, name, params] = methodMatch
      const types = splitParams(params).map(paramType)
      nodes.push(node({
        id: `M:${outer.name}#${name}(${types.map((t) => t.replace(/\s+/g, '')).join(',')})`,
        kind: 'method',
        label: `${modifiers}${returnType} ${name}(${types.join(', ')})`.trim(),
        lines: [number, blockEnd(lines, number)],
        parent: outer.id,
      }))
    }
  })
  return { nodes, edges: buildCallEdges(lines, nodes) }
}

function buildCallEdges(lines: string[], nodes: GraphNode[]): GraphEdge[] {
  const methods = nodes.filter((n) => n.kind === 'method')
  const byName = new Map(methods.map((m) => [m.id.slice(m.id.indexOf('#') + 1, m.id.indexOf('(')), m]))
  const edges: GraphEdge[] = []
  for (const method of methods) {
    const body = lines.slice(method.lines[0], method.lines[1]).map(codeOnly).join('\n')
    for (const [name, target] of byName) {
      if (target.id !== method.id && new RegExp(`\\b${name}\\s*\\(`).test(body)) {
        edges.push({ id: `${method.id}->${target.id}`, from: method.id, to: target.id, kind: 'call', label: 'вызов' })
      }
    }
  }
  return edges
}

const BRANCHES: Partial<Record<NodeKind, { key: string; label: string }[]>> = {
  if: [{ key: 'then', label: 'да' }, { key: 'else', label: 'нет' }],
  loop: [{ key: 'body', label: 'тело' }],
}

/** Чертёж метода: операторы тела по строкам, контейнеры if/for/while — по фигурным скобкам. */
export function buildBlueprint(source: string, method: GraphNode): Blueprint {
  const lines = source.split('\n')
  const nodes: GraphNode[] = []
  parseBlock(lines, method.lines[0] + 1, method.lines[1] - 1, method.id, 'body', nodes)
  const edges: GraphEdge[] = []
  for (let i = 1; i < nodes.length; i++) {
    const prev = nodes[i - 1]
    edges.push({ id: `${prev.id}->${nodes[i].id}`, from: prev.id, to: nodes[i].id, kind: 'exec', label: '' })
  }
  return { methodId: method.id, label: method.label, nodes: nodes.map((node) => ({ ...node, expr: mockExpr(source, node) })), edges }
}

function parseBlock(lines: string[], from: number, to: number, parent: string, branch: string, out: GraphNode[]): void {
  const repeats = new Map<string, number>()
  const makeId = (kind: string, text: string) => {
    const base = `${parent}/${kind}:${shortHash(text)}`
    const count = (repeats.get(base) ?? 0) + 1
    repeats.set(base, count)
    return count === 1 ? base : `${base}~${count}`
  }
  let number = from
  while (number <= to) {
    const text = lines[number - 1].trim()
    if (!text || text.startsWith('//') || text === '}') { number++; continue }
    const header = /^(if|for|while)\s*\((.*)\)\s*\{$/.exec(text)
    if (header) {
      const end = blockEnd(lines, number)
      const kind: NodeKind = header[1] === 'if' ? 'if' : 'loop'
      const label = text.slice(0, -1).trim()
      const id = makeId(kind, label)
      const elseLine = kind === 'if' ? findElse(lines, number, end) : null
      out.push(node({
        id, kind, label, lines: [number, end], parent, branch,
        code: kind === 'if' || header[1] === 'while' ? header[2] : null,
        editable: kind === 'if' || header[1] === 'while', branches: BRANCHES[kind] ?? null,
      }))
      parseBlock(lines, number + 1, (elseLine ?? end) - 1, id, kind === 'if' ? 'then' : 'body', out)
      if (elseLine) parseBlock(lines, elseLine + 1, end - 1, id, 'else', out)
      number = end + 1
      continue
    }
    if (braceDelta(lines[number - 1]) > 0) {
      const end = blockEnd(lines, number)
      out.push(node({ id: makeId('raw', text), kind: 'raw', label: text, code: text, lines: [number, end], parent, branch }))
      number = end + 1
      continue
    }
    let end = number
    while (end < to && !codeOnly(lines[end - 1]).trim().endsWith(';')) end++
    const code = lines.slice(number - 1, end).map((l) => l.trim()).join(' ')
    const kind = statementKind(code)
    out.push(node({ id: makeId(kind, code), kind, label: code, code, lines: [number, end], parent, branch, editable: true }))
    number = end + 1
  }
}

/** Строка «} else {» на глубине самого if, или null. */
function findElse(lines: string[], start: number, end: number): number | null {
  let depth = 0
  for (let number = start; number < end; number++) {
    if (number > start && depth === 1 && /^\}\s*else\s*\{$/.test(lines[number - 1].trim())) return number
    depth += braceDelta(lines[number - 1])
  }
  return null
}

function statementKind(code: string): NodeKind {
  if (/^return\b/.test(code)) return 'return'
  if (/^throw\b/.test(code)) return 'throw'
  if (/^break\b/.test(code)) return 'break'
  if (/^continue\b/.test(code)) return 'continue'
  return 'stmt'
}
