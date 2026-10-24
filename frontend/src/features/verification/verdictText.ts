import type { JobPhase, RunResult, Verdict } from '../../api/types'
import type { IconName } from '../../components/Icon'

// Как показывать вердикт: цвет, иконка и тексты. Все числа — из ответа API.
// BEHAVIOR_CHANGE никогда не зелёный: исправление дефекта — не доказательство эквивалентности.

export type Tone = 'success' | 'danger' | 'warning'

export interface VerdictView {
  tone: Tone
  icon: IconName
  label: string
  short: string
  description: string
  detail: string
}

const plural = (n: number, one: string, few: string, many: string) => {
  const mod10 = n % 10
  const mod100 = n % 100
  if (mod10 === 1 && mod100 !== 11) return one
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return few
  return many
}

export const inputsWord = (n: number) => plural(n, 'входе', 'входах', 'входах')

export function describeVerdict(v: Verdict): VerdictView {
  const time = `${(v.durationMs / 1000).toLocaleString('ru-RU', { maximumFractionDigits: 1 })} с`
  const checked = `${v.checked} из ${v.inputs} входов`
  switch (v.status) {
    case 'MATCHED': {
      // Сводка считает операторы обеих версий; minHits — разные входы, а не итерации цикла.
      const coverage = v.changedCovered
        ? ` · покрытие изменений: выполнено ${v.changedCovered.covered} из ${v.changedCovered.lines} операторов, каждый не меньше чем на ${v.changedCovered.minHits} разных входах`
        : ' · покрытие изменений не измерено'
      return {
        tone: 'success', icon: 'check', label: `Совпало на ${v.matched} ${inputsWord(v.matched)}`, short: 'Совпало',
        description: 'Результаты эталона и изменённой версии совпали на всех сравнённых входах: вывод, ошибки, код выхода и исключения. Это не доказательство для любых входов — только для этих.',
        detail: `${checked} · ${time}${coverage}${v.nondeterministic ? ` · случайных (не сравнивались): ${v.nondeterministic}` : ''}`,
      }
    }
    case 'MISMATCH':
      return {
        tone: 'danger', icon: 'close', label: 'Обнаружено расхождение', short: 'Расхождение',
        description: 'Найден вход, на котором результаты эталона и изменённой версии различаются. Ниже — самый короткий такой вход.',
        detail: `Расхождений: ${v.mismatches} · ${checked} · ${time}`,
      }
    case 'INCOMPLETE':
      return {
        tone: 'warning', icon: 'alert', label: 'Проверка неполна', short: 'Неполная проверка',
        description: v.incompleteReason ?? 'Сервер не указал причину.',
        detail: `${checked} · ${time}`,
      }
    case 'BUILD_ERROR':
      return {
        tone: 'danger', icon: 'alert', label: 'Ошибка сборки', short: 'Ошибка сборки',
        description: 'Изменённая версия не компилируется — запуски не выполнялись.',
        detail: v.buildErrors.map((e) => `Строка ${e.line}:${e.column} — ${e.text}`).join(' · ') || 'Сервер не прислал текст ошибки',
      }
    case 'BEHAVIOR_CHANGE':
      return {
        tone: 'warning', icon: 'info', label: 'Изменение поведения', short: 'Поведение изменено',
        description: 'Исправление дефекта меняет результат на части входов. Это не подтверждение эквивалентности — проверьте изменения.',
        detail: `Отличий: ${v.mismatches} · ${checked} · ${time}`,
      }
    case 'RUNNING':
      return { tone: 'warning', icon: 'clock', label: 'Идёт проверка', short: 'Проверка', description: phaseText(v.phase), detail: '' }
  }
}

export function phaseText(phase: JobPhase): string {
  switch (phase) {
    case 'compile': return 'Сборка эталона и изменённой версии'
    case 'run': return 'Запуск обеих версий на одинаковых входах'
    case 'compare': return 'Сравнение вывода, ошибок и кодов выхода'
    case 'done': return 'Готово'
  }
}

/** Что показать как результат прогона: вывод или исключение. */
export function runOutput(run: RunResult): string {
  if (run.timedOut) return 'Превышено время выполнения'
  if (run.exception) return `${run.exception.type}${run.exception.message ? `: ${run.exception.message}` : ''}`
  const exit = run.exitCode !== 0 ? `\n[код выхода ${run.exitCode}]` : ''
  const stderr = run.stderr ? `\n[stderr] ${run.stderr}` : ''
  return `${run.stdout}${stderr}${exit}` || '(пустой вывод)'
}

export function groupTone(status: string): { tone: Tone | 'muted'; icon: IconName; text: string } {
  switch (status) {
    case 'MATCHED': return { tone: 'success', icon: 'check', text: 'Совпало' }
    case 'MISMATCH': return { tone: 'danger', icon: 'close', text: 'Расхождение' }
    case 'BEHAVIOR_CHANGE': return { tone: 'warning', icon: 'info', text: 'Поведение изменено' }
    case 'INCOMPLETE': return { tone: 'warning', icon: 'alert', text: 'Неполно' }
    default: return { tone: 'muted', icon: 'clock', text: status }
  }
}
