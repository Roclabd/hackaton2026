import { Dialog } from '../../components/Dialog'
import { Icon } from '../../components/Icon'
import { currentFinding, methodName } from '../../state/selectors'
import { dispatch, useAppState } from '../../state/store'
import { linesText, viewPatch } from './findingActions'
import { findingCategory, growthEstimate } from './findingPresentation'

/** Боковая панель находки: почему, условия, варианты заплаток (BUGFIX подписан «меняет поведение»). */
export function FindingDrawer() {
  const finding = useAppState(currentFinding)
  const close = () => dispatch({ type: 'closeDialog' })
  if (!finding) return null
  const growth = growthEstimate(finding)
  return (
    <Dialog title={finding.title} className="side-drawer" onClose={close} actions={<button type="button" className="btn" onClick={close}>Закрыть</button>}>
      <div className="finding-location"><Icon name="search" />{findingCategory(finding)} · {methodName(finding.methodId)} · {linesText(finding.lines).toLowerCase()} · правило {finding.rule}</div>
      <h3>Почему это находка</h3>
      <p>{finding.why}</p>
      {growth && <p>Рост: {growth.before} → {growth.after} (оценка по структуре кода)</p>}
      <h3>Условия применения</h3>
      <ul className="check-list">{finding.conditions.map((c) => <li key={c}><Icon name="check" />{c}</li>)}</ul>
      <h3>Варианты изменения</h3>
      {finding.patches.length === 0 && <p>Заплаток для этой находки нет.</p>}
      <div className="example-list">
        {finding.patches.map((patch) => (
          <button key={patch.id} type="button" className="example-option" onClick={() => viewPatch(finding, patch.id)}>
            <Icon name="compare" />
            <span>
              <strong>{patch.title}</strong>
              <span>{patch.description}</span>
              <small>{patch.intent === 'BUGFIX' ? 'Исправление дефекта — меняет поведение' : 'Рефакторинг — поведение должно сохраниться'}</small>
            </span>
            <Icon name="chevron" />
          </button>
        ))}
      </div>
      <div className="drawer-note">Сохранение поведения проверяется запуском обеих версий на одинаковых входах.</div>
    </Dialog>
  )
}
