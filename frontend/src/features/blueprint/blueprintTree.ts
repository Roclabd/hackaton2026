import type { Blueprint, GraphNode, NodeKind } from '../../api/types'

// Плоский список узлов чертежа → дерево для вложенных блоков: контейнер → ветки → операторы.

export interface TreeNode {
  node: GraphNode
  branches: TreeBranch[]
}

export interface TreeBranch {
  key: string
  label: string
  children: TreeNode[]
}

export const CONTAINER_KINDS: NodeKind[] = ['if', 'loop', 'switch', 'try']
export const TERMINAL_KINDS: NodeKind[] = ['return', 'throw', 'break', 'continue']

const CONTAINER_TITLES: Partial<Record<NodeKind, string>> = {
  if: 'Условие', loop: 'Цикл', switch: 'Выбор', try: 'Попытка',
  stmt: 'Оператор', return: 'Возврат', throw: 'Исключение',
}

export function containerTitle(kind: NodeKind): string {
  return CONTAINER_TITLES[kind] ?? kind
}

/** Подпись ветки, если сервер не прислал свою. */
export function defaultBranchLabel(key: string): string {
  if (key === 'then') return 'да'
  if (key === 'else') return 'нет'
  if (key === 'body') return 'тело'
  if (key === 'finally') return 'finally'
  if (key.startsWith('case:')) return `case ${key.slice(5)}`
  if (key.startsWith('catch:')) return 'catch'
  return key
}

export function buildTree(blueprint: Blueprint): TreeNode[] {
  const byParent = new Map<string, GraphNode[]>()
  blueprint.nodes.forEach((node) => {
    const parent = node.parent ?? blueprint.methodId
    byParent.set(parent, [...(byParent.get(parent) ?? []), node])
  })
  const toTree = (node: GraphNode): TreeNode => ({ node, branches: branchesOf(node, byParent.get(node.id) ?? [], toTree) })
  return sortByLine(byParent.get(blueprint.methodId) ?? []).map(toTree)
}

function branchesOf(node: GraphNode, children: GraphNode[], toTree: (n: GraphNode) => TreeNode): TreeBranch[] {
  const declared = node.branches ?? []
  if (!CONTAINER_KINDS.includes(node.kind) && declared.length === 0) return []
  const keys = [...declared.map((b) => b.key)]
  children.forEach((child) => {
    const key = child.branch ?? 'body'
    if (!keys.includes(key)) keys.push(key)
  })
  return keys.map((key) => ({
    key,
    label: declared.find((b) => b.key === key)?.label ?? defaultBranchLabel(key),
    children: sortByLine(children.filter((c) => (c.branch ?? 'body') === key)).map(toTree),
  }))
}

function sortByLine(nodes: GraphNode[]): GraphNode[] {
  return [...nodes].sort((a, b) => a.lines[0] - b.lines[0] || a.lines[1] - b.lines[1])
}

/** Сводка для подвала схемы: «6 узлов · 2 цикла». */
export function blueprintSummary(blueprint: Blueprint): string {
  const loops = blueprint.nodes.filter((n) => n.kind === 'loop').length
  const loopWord = loops % 10 === 1 && loops % 100 !== 11 ? 'цикл' : loops % 10 >= 2 && loops % 10 <= 4 && (loops % 100 < 12 || loops % 100 > 14) ? 'цикла' : 'циклов'
  return `${blueprint.nodes.length} узл. · ${loops} ${loopWord}`
}
