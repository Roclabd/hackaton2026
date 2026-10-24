import type { Finding, Verdict } from '../../api/types'
import { useAppState } from '../../state/store'
import { GrowthChart } from './GrowthChart'
import { growthEstimate } from './findingPresentation'
import { startGrowthProfile, useGrowthProfile, type ProfileTarget } from './growthProfileStore'
import { ProfileChart } from './ProfileChart'
import './growthMeasure.css'

// «Замерить рост»: кнопка + график по счётчикам из POST /profile. Пока замера нет — прежняя заглушка
// (или полосы ускорения из вердикта проверки), без выдуманных чисел.

interface GrowthMeasureProps {
  finding: Finding | null
  speedup?: Verdict['speedup']
}

/** Замер имеет смысл для находок о сложности: у них есть оценка роста или категория «неэффективность». */
function measurable(finding: Finding | null): finding is Finding {
  return !!finding && (finding.category === 'INEFFICIENCY' || finding.growthBefore != null)
}

export function GrowthMeasure({ finding, speedup = null }: GrowthMeasureProps) {
  const project = useAppState((s) => s.project)
  const target: ProfileTarget | null = project && measurable(finding)
    ? { projectId: project.id, version: project.version, findingId: finding.id, patchId: finding.patches[0]?.id ?? null }
    : null
  const state = useGrowthProfile(target)
  const fallback = <GrowthChart speedup={speedup} estimate={growthEstimate(finding)} />
  if (!target) return fallback

  const result = state.status === 'done' ? state.result : null
  const measured = result?.supported && result.sizes && result.before ? result : null
  return (
    <div className="growth-measure">
      <div className="growth-measure-bar">
        <button type="button" className="btn btn-small" disabled={state.status === 'loading'} onClick={() => void startGrowthProfile(target)}>
          {state.status === 'loading' ? 'Замеряем…' : measured ? 'Замерить снова' : 'Замерить рост'}
        </button>
        <span className="growth-measure-status" title={result?.note}>{statusText(state.status, result, state.status === 'error' ? state.message : null)}</span>
      </div>
      {measured
        ? <ProfileChart sizes={measured.sizes!} before={measured.before!} after={measured.after ?? null} factor={measured.factor ?? null} />
        : result && !result.supported
          ? <div className="growth-placeholder"><strong>Замер не выполнен</strong><span>{result.note}</span></div>
          : fallback}
    </div>
  )
}

function statusText(status: string, result: { supported: boolean; note: string; durationMs?: number } | null, error: string | null): string {
  if (status === 'loading') return 'Прогоны на n = 100…1600, до 15 с'
  if (error) return error
  if (result?.supported) return `Счётчики циклов, ${seconds(result.durationMs)} · ${result.note}`
  return 'Счётчики выполнений на входах растущего размера'
}

const seconds = (ms?: number) => (ms == null ? 'замер' : `${(ms / 1000).toLocaleString('ru-RU', { maximumFractionDigits: 1 })} с`)
