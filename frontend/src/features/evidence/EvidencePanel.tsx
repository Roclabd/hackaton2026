import { useState } from 'react'
import type { Finding } from '../../api/types'
import { Icon, type IconName } from '../../components/Icon'
import { methodName } from '../../state/selectors'
import { useAppState } from '../../state/store'
import { linesText, openFindingDrawer, showFindingLines, viewPatch } from './findingActions'
import { GrowthChart } from './GrowthChart'
import { GrowthMeasure } from './GrowthMeasure'
import { findingCategory, findingGrowth } from './findingPresentation'

type Tab = 'summary' | 'findings' | 'growth'
const TABS: [Tab, string, IconName][] = [['summary', 'Обзор', 'list'], ['findings', 'Находки', 'search'], ['growth', 'График роста', 'chart']]

const OPEN_KEY = 'blueprints.evidenceOpen'
// На невысоком экране панель по умолчанию свёрнута: место отдаём коду и схеме.
const TALL_SCREEN_PX = 820

function initialOpen(): boolean {
  try {
    const saved = localStorage.getItem(OPEN_KEY)
    if (saved !== null) return saved === '1'
  } catch { /* хранилище недоступно — решаем по высоте окна */ }
  return window.innerHeight >= TALL_SCREEN_PX
}

function rememberOpen(open: boolean) {
  try { localStorage.setItem(OPEN_KEY, open ? '1' : '0') } catch { /* не страшно */ }
}

/** Нижняя панель «Обзор / Находки / График роста» — данные из GET /findings и последнего вердикта. Сворачивается до строки вкладок. */
export function EvidencePanel() {
  const [tab, setTab] = useState<Tab>('summary')
  const [open, setOpen] = useState(initialOpen)
  const findings = useAppState((s) => s.findings)
  const speedup = useAppState((s) => s.job?.verdict?.speedup ?? null)
  const first = findings[0] ?? null

  const toggle = (next: boolean) => { setOpen(next); rememberOpen(next) }
  const chooseTab = (id: Tab) => { setTab(id); if (!open) toggle(true) }

  return (
    <section className={`evidence ${open ? '' : 'is-collapsed'}`} aria-label="Диагностика">
      <div className="evidence-tabs" role="tablist" aria-label="Панель диагностики">
        {TABS.map(([id, label, icon]) => (
          <button key={id} type="button" role="tab" aria-selected={open && tab === id} className={`evidence-tab ${open && tab === id ? 'active' : ''}`} onClick={() => chooseTab(id)}>
            <Icon name={icon} />{label}{id === 'findings' && <span className="count-badge">{findings.length}</span>}
          </button>
        ))}
        {!open && first && <span className="evidence-peek">{findingCategory(first)} · {first.title}{findingGrowth(first)}</span>}
        <button
          type="button"
          className="icon-button evidence-toggle"
          aria-expanded={open}
          aria-label={open ? 'Свернуть панель' : 'Развернуть панель'}
          title={open ? 'Свернуть панель' : 'Развернуть панель'}
          onClick={() => toggle(!open)}
        ><Icon name="down" /></button>
      </div>
      {open && <div className="evidence-body">
        {tab === 'summary' && <Summary finding={first} speedup={speedup} />}
        {tab === 'findings' && <FindingList findings={findings} />}
        {tab === 'growth' && <div className="growth-row"><GrowthMeasure finding={first} speedup={speedup} /><div><strong>Счётчики вместо догадок</strong><p>Копии оригинала и заплатки со счётчиком в теле каждого цикла запускаются на входах n = 100…1600. Модель роста подбирается по числу выполнений — это операции, а не время: результат не зависит от загрузки машины.</p></div></div>}
      </div>}
    </section>
  )
}

function Summary({ finding, speedup }: { finding: Finding | null; speedup: Parameters<typeof GrowthChart>[0]['speedup'] }) {
  if (!finding) {
    return <div className="summary-finding"><div className="section-label"><Icon name="search" />Находки</div><p>Находок нет — или проект ещё не загружен.</p></div>
  }
  return <>
    <div className="summary-finding">
      <div className="section-label"><Icon name="search" />{findingCategory(finding)} · {finding.rule}</div>
      <button type="button" className="finding-title" onClick={() => openFindingDrawer(finding)}>{finding.title} <Icon name="chevron" /></button>
      <p>{finding.why}</p>
      <button type="button" className="line-chip" onClick={() => showFindingLines(finding)}>{methodName(finding.methodId)} · {linesText(finding.lines)}</button>
    </div>
    <div className="summary-conditions">
      <div className="section-label"><Icon name="list" />Условия изменения</div>
      <ul className="compact-list">{finding.conditions.map((c) => <li key={c}>{c}</li>)}</ul>
      {finding.patches.length > 0 && <button type="button" className="text-button" onClick={() => viewPatch(finding)}>Посмотреть изменение <Icon name="chevron" /></button>}
    </div>
    <div className="summary-growth">
      <div className="section-label"><Icon name="chart" />График роста</div>
      <GrowthMeasure finding={finding} speedup={speedup} />
    </div>
  </>
}

function FindingList({ findings }: { findings: Finding[] }) {
  if (findings.length === 0) return <div className="finding-row"><p>Находок нет.</p></div>
  return (
    <div className="finding-list">
      {findings.map((finding, index) => (
        <div key={finding.id} className="finding-row">
          <div className="finding-number">{index + 1}</div>
          <div>
            <strong>{finding.title}</strong><span className="finding-category">{findingCategory(finding)}</span>
            <p>{finding.why}</p>
            <span className="muted">{methodName(finding.methodId)} · {linesText(finding.lines).toLowerCase()}{findingGrowth(finding)}</span>
          </div>
          <button type="button" className="btn" onClick={() => openFindingDrawer(finding)}>Подробнее <Icon name="chevron" /></button>
          <button type="button" className="btn btn-blue" disabled={finding.patches.length === 0} onClick={() => viewPatch(finding)}>Посмотреть изменение</button>
        </div>
      ))}
    </div>
  )
}
