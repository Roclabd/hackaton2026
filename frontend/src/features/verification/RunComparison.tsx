import type { ScenarioGroup, Verdict } from '../../api/types'
import { Icon } from '../../components/Icon'
import type { JobState } from '../../state/reducer'
import { groupTone, runOutput } from './verdictText'

/** «Вход и результаты»: самый короткий вход с расхождением или пример при совпадении. */
export function RunComparison({ verdict, job }: { verdict: Verdict; job: JobState }) {
  const pair = verdict.smallestMismatch ?? verdict.example
  const differs = verdict.smallestMismatch !== null
  return (
    <section className="pane run-comparison">
      <div className="pane-header">
        <strong>Вход и результаты</strong>
        <span className="muted">{differs ? 'самый короткий вход с расхождением' : pair ? 'пример прогона' : ''}</span>
      </div>
      {pair ? (
        <div className="comparison-columns">
          <div>
            <span className="section-label">Вход (stdin)</span>
            <pre>{pair.input || '(пусто)'}</pre>
            <span className="muted">Группа: {pair.group}</span>
          </div>
          <div>
            <span className="section-label">До · эталон, версия {job.baselineVersion}</span>
            <pre>{runOutput(pair.original)}</pre>
          </div>
          <div>
            <span className="section-label">После · {job.candidateLabel}</span>
            <pre className={differs ? 'output-error' : ''}>{runOutput(pair.candidate)}</pre>
          </div>
        </div>
      ) : (
        <p className="pane-note">Сервер не прислал пример прогона{verdict.status === 'BUILD_ERROR' ? ' — запуски не выполнялись' : ''}.</p>
      )}
    </section>
  )
}

/** «Сценарии»: группы входов из verdict.groups. */
export function ScenarioTable({ groups, inputs }: { groups: ScenarioGroup[]; inputs: number }) {
  return (
    <section className="pane check-runs">
      <div className="pane-header"><strong>Сценарии</strong><span className="muted">{inputs} входов</span></div>
      <div className="scenario-table">
        <div className="table-heading"><span>Группа входов</span><span>Проверено</span><span>Результат</span></div>
        {groups.length === 0 && <div className="table-row"><span className="muted">Нет данных о группах</span><span /><span /></div>}
        {groups.map((group) => {
          const tone = groupTone(group.status)
          return (
            <div key={group.name} className="table-row">
              <span>{group.name}</span>
              <span>{group.checked} / {group.total}</span>
              <span className={`table-result ${tone.tone}`}><Icon name={tone.icon} />{tone.text}{group.matched < group.checked ? ` · совпало ${group.matched}` : ''}</span>
            </div>
          )
        })}
      </div>
    </section>
  )
}
