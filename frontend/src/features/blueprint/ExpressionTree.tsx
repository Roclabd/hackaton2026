import type { ExprNode } from '../../api/types'
import { dispatch, useAppState } from '../../state/store'
import { expressionKind } from './expressionModel'

/** Выражение выбирает оператор, но не меняет уровень команды editStatement. */
export function ExpressionTree({ root, operatorId }: { root: ExprNode; operatorId: string }) {
  const selected = useAppState((s) => s.selectedExprId)
  const current = useAppState((s) => s.project?.source === s.draft && !s.project?.errors.length)
  return <ul className="bp-expressions" aria-label="Дерево выражения">
    <Expression item={root} operatorId={operatorId} selected={selected} disabled={!current} />
  </ul>
}

function Expression({ item, operatorId, selected, disabled }: {
  item: ExprNode; operatorId: string; selected: string | null; disabled: boolean
}) {
  return <li>
    <button
      type="button" className={`bp-expression ${selected === item.id ? 'is-selected' : ''}`}
      aria-pressed={selected === item.id} disabled={disabled}
      title={disabled ? 'Дождитесь сохранения и исправьте ошибки разбора' : `${item.code} · строка ${item.range[0]}:${item.range[1]}`}
      onClick={() => dispatch({ type: 'selectExpr', nodeId: operatorId, expressionId: item.id })}
    >
      {item.role && <span className="bp-expression-role">{item.role}</span>}
      <code>{item.label}</code><span className="bp-expression-kind">{expressionKind[item.kind] ?? 'выражение'}</span>
    </button>
    {item.children.length > 0 && <ul>{item.children.map((child) =>
      <Expression key={child.id} item={child} operatorId={operatorId} selected={selected} disabled={disabled} />,
    )}</ul>}
  </li>
}
