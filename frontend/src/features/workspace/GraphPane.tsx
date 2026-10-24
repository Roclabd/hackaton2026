import { Icon } from '../../components/Icon'
import { BlueprintView } from '../blueprint/BlueprintView'
import { blueprintSummary } from '../blueprint/blueprintTree'
import { ClassMap } from '../map/ClassMap'
import { className, methodName, selectedNode } from '../../state/selectors'
import { dispatch, useAppState } from '../../state/store'

const ZOOM_MIN = 70
const ZOOM_MAX = 140
const ZOOM_STEP = 10

/** Правая панель: хлебные крошки, карта или чертёж метода, масштаб. */
export function GraphPane() {
  const level = useAppState((s) => s.level)
  const methodId = useAppState((s) => s.methodId)
  const blueprint = useAppState((s) => s.blueprint)
  const heat = useAppState((s) => s.heat)
  const detailed = useAppState((s) => s.detailedExpressions)
  const zoom = useAppState((s) => s.zoom)
  const node = useAppState(selectedNode)
  const onMethod = level === 'method' && methodId !== null
  const setZoom = (value: number) => dispatch({ type: 'zoom', value: Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, value)) })

  return (
    <div className="pane graph-pane">
      <div className="pane-header graph-toolbar">
        <div className="breadcrumbs">
          <button type="button" className="text-button" onClick={() => dispatch({ type: 'showMap' })}>Карта</button>
          {onMethod && <>
            <Icon name="chevron" /><span>{className(methodId)}</span>
            <Icon name="chevron" /><strong>{methodName(methodId)}</strong>
          </>}
        </div>
        <div className="graph-actions">
          {onMethod && <button type="button" className="btn btn-small" aria-pressed={detailed}
            title={detailed ? 'Скрыть дерево выражений' : 'Показать дерево выражений'}
            onClick={() => dispatch({ type: 'toggleExpressions' })}>{detailed ? 'Подробно' : 'Кратко'}</button>}
          <button
            type="button"
            className={`icon-button ${heat ? 'is-on' : ''}`}
            aria-pressed={heat}
            aria-label="Тепловая карта"
            title="Число выполнений"
            onClick={() => dispatch({ type: 'toggleHeat' })}
          ><Icon name="heat" /></button>
          <button
            type="button"
            className="btn btn-small"
            disabled={!onMethod || !node?.editable}
            onClick={() => dispatch({ type: 'openDialog', dialog: 'nodeEditor' })}
          ><Icon name="pencil" /><span>Править узел</span></button>
        </div>
      </div>
      <div className="graph-canvas" aria-label={onMethod ? 'Чертёж метода' : 'Карта классов'}>
        {onMethod ? <BlueprintView /> : <ClassMap />}
        <div className="canvas-hint">
          {heat
            ? <><span className="heat-swatch" /> Число выполнений появится после прогона</>
            : onMethod ? 'Клик — выбрать узел · двойной клик — изменить' : 'Клик по методу — открыть чертёж'}
        </div>
        {onMethod && (
          <div className="canvas-controls">
            <button type="button" className="icon-button" aria-label="Уменьшить схему" onClick={() => setZoom(zoom - ZOOM_STEP)}>−</button>
            <button type="button" className="zoom-value" aria-label="Сбросить масштаб" onClick={() => setZoom(100)}>{zoom}%</button>
            <button type="button" className="icon-button" aria-label="Увеличить схему" onClick={() => setZoom(zoom + ZOOM_STEP)}>+</button>
          </div>
        )}
      </div>
      <div className="graph-footer">
        <span><Icon name="nodes" />{node ? node.label : onMethod ? 'Узел не выбран' : 'Карта классов'}</span>
        <span>{onMethod && blueprint ? blueprintSummary(blueprint) : ''}</span>
      </div>
    </div>
  )
}
