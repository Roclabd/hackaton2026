import { ComplexityBadge } from '../../components/ComplexityBadge'
import { useMemo, useState } from 'react'
import { openMethod } from '../../state/actions'
import { useAppState } from '../../state/store'
import { buildClassCards, methodSignature, type ClassCard } from './mapModel'

/** Уровень «Карта»: карточки классов; клик по методу открывает его чертёж. */
export function ClassMap() {
  const map = useAppState((s) => s.project?.map)
  const selectedId = useAppState((s) => s.selectedNodeId)
  const cards = useMemo(() => (map ? buildClassCards(map) : []), [map])

  if (!map || cards.length === 0) {
    return <p className="canvas-empty">На карте нет классов. Если код не разбирается — исправьте ошибку в редакторе.</p>
  }
  return (
    <div className="class-map">
      {cards.map((card) => <ClassCardView key={card.id} card={card} selectedId={selectedId} />)}
    </div>
  )
}

function ClassCardView({ card, selectedId }: { card: ClassCard; selectedId: string | null }) {
  const [expanded, setExpanded] = useState(card.methods.length > 0)
  const containsSelection = card.methods.some((m) => m.id === selectedId)
  return (
    <section className={`class-card ${containsSelection ? 'is-selected' : ''}`} aria-label={`Класс ${card.fullName}`}>
      <button
        type="button"
        className="class-card-header"
        onClick={() => setExpanded(!expanded)}
        aria-expanded={expanded}
        title={card.fullName}
      >
        <strong>{card.name}</strong>
        <span className="muted">{card.kind}{card.fieldCount ? ` · ${card.fieldCount} пол.` : ''}{card.methods.length ? '' : ' · нет методов'}</span>
      </button>
      {expanded && card.methods.length > 0 && (
        <ul className="class-methods">
          {card.methods.map((method) => (
            <li key={method.id}>
              <button
                type="button"
                className={`method-row ${method.id === selectedId ? 'is-selected' : ''} ${method.badges.length ? 'has-badge' : ''}`}
                onClick={() => void openMethod(method.id)}
                title={`${method.label} · строки ${method.lines[0]}–${method.lines[1]}`}
              >
                <span className="method-name">{methodSignature(method.label)}</span>
                <ComplexityBadge complexity={method.complexity} />
                {method.badges.map((badge) => <span key={badge} className="node-badge">{badge}</span>)}
              </button>
            </li>
          ))}
        </ul>
      )}
      {expanded && card.calls.length > 0 && (
        <div className="class-calls">вызывает: {card.calls.slice(0, 3).map((c) => `${c.target.split('.').pop()} ×${c.count}`).join(', ')}{card.calls.length > 3 ? '…' : ''}</div>
      )}
    </section>
  )
}
