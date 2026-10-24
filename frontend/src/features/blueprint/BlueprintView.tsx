import { ComplexityBadge } from '../../components/ComplexityBadge'
import { useMemo } from 'react'
import { methodSignature } from '../map/mapModel'
import { useAppState } from '../../state/store'
import { Sequence } from './BlueprintBlock'
import { buildTree } from './blueprintTree'

/** Уровень «Чертёж метода»: начало → блоки тела → конец; масштаб — CSS transform. */
export function BlueprintView() {
  const blueprint = useAppState((s) => s.blueprint)
  const selectedId = useAppState((s) => s.selectedNodeId)
  const zoom = useAppState((s) => s.zoom)
  // Оценка всего метода лежит на его узле карты.
  const methodComplexity = useAppState((s) => s.project?.map.nodes.find((n) => n.id === s.blueprint?.methodId)?.complexity ?? null)
  const tree = useMemo(() => (blueprint ? buildTree(blueprint) : []), [blueprint])

  if (!blueprint) return <p className="canvas-empty">Чертёж загружается…</p>
  return (
    <div className="bp-scroll">
      <div className="bp-canvas" style={{ transform: `scale(${zoom / 100})` }}>
        <div className="bp-pill">Начало · {methodSignature(blueprint.label)} <ComplexityBadge complexity={methodComplexity} /></div>
        <div className="bp-arrow" aria-hidden="true" />
        <Sequence items={tree} selectedId={selectedId} />
        <div className="bp-arrow" aria-hidden="true" />
        <div className="bp-pill muted">Конец метода</div>
      </div>
    </div>
  )
}
