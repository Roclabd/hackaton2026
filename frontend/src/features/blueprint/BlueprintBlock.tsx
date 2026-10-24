import { Fragment } from 'react'
import { dispatch, useAppState } from '../../state/store'
import type { GraphNode } from '../../api/types'
import { ExpressionTree } from './ExpressionTree'
import { CONTAINER_KINDS, TERMINAL_KINDS, containerTitle, type TreeBranch, type TreeNode } from './blueprintTree'

// Блоки чертежа метода: контейнеры if/loop/switch/try — рамки с ветками, операторы — моноширинные блоки.

interface BlockProps {
  item: TreeNode
  selectedId: string | null
}

function select(id: string) {
  dispatch({ type: 'select', nodeId: id })
}

function openEditor(id: string, editable: boolean | undefined) {
  if (!editable) return
  dispatch({ type: 'select', nodeId: id })
  dispatch({ type: 'openDialog', dialog: 'nodeEditor' })
}

/** Последовательность блоков сверху вниз со стрелками между соседями. */
export function Sequence({ items, selectedId }: { items: TreeNode[]; selectedId: string | null }) {
  if (items.length === 0) return <div className="bp-empty">пусто</div>
  return (
    <div className="bp-sequence">
      {items.map((item, index) => (
        <Fragment key={item.node.id}>
          {index > 0 && <div className="bp-arrow" aria-hidden="true" />}
          <Block item={item} selectedId={selectedId} />
        </Fragment>
      ))}
    </div>
  )
}

function Block({ item, selectedId }: BlockProps) {
  return CONTAINER_KINDS.includes(item.node.kind)
    ? <ContainerBlock item={item} selectedId={selectedId} />
    : <StatementBlock item={item} selectedId={selectedId} />
}

function Badges({ badges }: { badges: string[] }) {
  return <>{badges.map((badge) => <span key={badge} className="node-badge">{badge}</span>)}</>
}

function Expressions({ node }: { node: GraphNode }) {
  const detailed = useAppState((s) => s.detailedExpressions)
  return detailed && node.expr ? <ExpressionTree root={node.expr} operatorId={node.id} /> : null
}

function ContainerBlock({ item, selectedId }: BlockProps) {
  const { node, branches } = item
  const picked = node.id === selectedId
  const sideBySide = node.kind === 'if'
  return (
    <div className={`bp-frame kind-${node.kind} ${picked ? 'is-selected' : ''}`}>
      <button
        type="button"
        className="bp-frame-header"
        onClick={() => select(node.id)}
        onDoubleClick={() => openEditor(node.id, node.editable)}
        title={node.editable ? 'Двойной клик — изменить условие' : undefined}
        aria-pressed={picked}
      >
        <span className="bp-frame-title">{containerTitle(node.kind)}</span>
        <code className="bp-frame-code">{node.label}</code>
        <Badges badges={node.badges} />
      </button>
      <Expressions node={node} />
      <div className={`bp-branches ${sideBySide ? 'side-by-side' : ''}`}>
        {branches.map((branch) => <BranchColumn key={branch.key} branch={branch} selectedId={selectedId} />)}
      </div>
    </div>
  )
}

function BranchColumn({ branch, selectedId }: { branch: TreeBranch; selectedId: string | null }) {
  return (
    <div className="bp-branch">
      <span className="bp-branch-label">{branch.label}</span>
      <Sequence items={branch.children} selectedId={selectedId} />
    </div>
  )
}

function StatementBlock({ item, selectedId }: BlockProps) {
  const { node } = item
  const picked = node.id === selectedId
  const style = TERMINAL_KINDS.includes(node.kind) ? 'terminal' : node.kind === 'raw' ? 'raw' : 'statement'
  return (
    <div className={`bp-statement ${picked ? 'is-selected' : ''}`}>
    <button
      type="button"
      className={`bp-node ${style} kind-${node.kind} ${picked ? 'is-selected' : ''}`}
      onClick={() => select(node.id)}
      onDoubleClick={() => openEditor(node.id, node.editable)}
      title={node.editable ? 'Двойной клик — изменить' : node.kind === 'raw' ? 'Исходный код без разбора на блоки' : undefined}
      aria-pressed={picked}
    >
      {node.kind === 'raw' && <span className="bp-raw-caption">исходный код</span>}
      <code>{node.code ?? node.label}</code>
      <Badges badges={node.badges} />
    </button>
    <Expressions node={node} />
    </div>
  )
}
