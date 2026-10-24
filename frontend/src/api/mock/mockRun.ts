import type { RunPair, RunResult, ScenarioGroup, Verdict } from '../types'
import { firstSyntaxProblem } from './javaText'

// Демо-данные: имитация прогонов MatchDemo в браузере (только режим моков).
// Настоящая проверка запускает javac и JVM на бэке; здесь — лишь те же правила подсчёта на JS.

const INPUTS: { group: string; input: string }[] = [
  { group: 'Обычные значения', input: '1 2 3\n3 4 5' },
  { group: 'Обычные значения', input: '1 1 3\n1 2' },
  { group: 'Обычные значения', input: '5\n5' },
  { group: 'Обычные значения', input: '7 8\n9' },
  { group: 'Повторяющиеся значения', input: '2 2 2\n2 2' },
  { group: 'Повторяющиеся значения', input: '3 3 4\n4 4 3' },
  { group: 'Пустые списки', input: '\n1 2' },
  { group: 'Пустые списки', input: '1 2\n' },
  { group: 'Значения null', input: 'null\nnull' },
  { group: 'Значения null', input: '1 null\nnull 1' },
  { group: 'Значения null', input: 'null\n1' },
  { group: 'Значения null', input: '1\nnull' },
]

type Comparison = 'safe' | 'nullUnsafeLeft' | 'nullUnsafeRight'

/** Как программа сравнивает элементы; null — такой вариант моки выполнить не умеют. */
function comparisonOf(source: string): Comparison | null {
  if (!source.includes('countMatches')) return null
  if (source.includes('freq.getOrDefault(x')) return 'safe'
  const condition = /if \((.*)\) \{/.exec(source.slice(source.indexOf('countMatches')))?.[1]?.replace(/\s+/g, '')
  if (condition === 'Objects.equals(x,y)' || condition === 'Objects.equals(y,x)') return 'safe'
  if (condition === 'x.equals(y)') return 'nullUnsafeLeft'
  if (condition === 'y.equals(x)') return 'nullUnsafeRight'
  return null
}

function parseList(line: string | undefined): (number | null)[] | string {
  const values: (number | null)[] = []
  for (const token of (line ?? '').trim().split(/\s+/)) {
    if (!token) continue
    if (token === 'null') values.push(null)
    else if (/^-?\d+$/.test(token)) values.push(Number(token))
    else return token
  }
  return values
}

function failure(type: string, message: string): RunResult {
  return { stdout: '', stderr: `Exception in thread "main" ${type}: ${message}\n`, exitCode: 1, exception: { type, message }, timedOut: false, durationMs: 1 }
}

export function simulate(comparison: Comparison, input: string): RunResult {
  const [lineA, lineB] = input.split('\n')
  const a = parseList(lineA)
  const b = parseList(lineB)
  if (typeof a === 'string' || typeof b === 'string') {
    return failure('java.lang.NumberFormatException', `For input string: "${typeof a === 'string' ? a : b}"`)
  }
  let total = 0
  for (const x of a) {
    for (const y of b) {
      if ((comparison === 'nullUnsafeLeft' && x === null) || (comparison === 'nullUnsafeRight' && y === null)) {
        return failure('java.lang.NullPointerException', 'Cannot invoke "java.lang.Integer.equals(Object)" because the value is null')
      }
      if (x === y) total++
    }
  }
  return { stdout: `Совпадений: ${total}\n`, stderr: '', exitCode: 0, exception: null, timedOut: false, durationMs: 1 }
}

const sameRun = (left: RunResult, right: RunResult) =>
  left.stdout === right.stdout && left.exitCode === right.exitCode && left.exception?.type === right.exception?.type

const emptyVerdict = (): Verdict => ({
  status: 'RUNNING', phase: 'compile', progress: 0, inputs: INPUTS.length, checked: 0, matched: 0, mismatches: 0, nondeterministic: 0,
  incompleteReason: null, buildErrors: [], smallestMismatch: null, example: null, groups: [],
  changedCovered: null, speedup: null, durationMs: 0,
})

/** Итог проверки кандидата против эталона — считается сразу, показывается постепенно. */
export function finalVerdict(original: string, candidate: string): Verdict {
  const verdict = emptyVerdict()
  const problem = firstSyntaxProblem(candidate)
  if (problem) {
    return { ...verdict, status: 'BUILD_ERROR', phase: 'done', progress: 1, checked: 0, buildErrors: [{ line: problem.line, column: problem.column, text: problem.message }] }
  }
  const before = comparisonOf(original)
  const after = comparisonOf(candidate)
  if (!before || !after) {
    return { ...verdict, status: 'INCOMPLETE', phase: 'done', progress: 1, incompleteReason: 'Демо-моки умеют выполнять только варианты MatchDemo; этот код проверит настоящий бэкенд.' }
  }
  const pairs: RunPair[] = INPUTS.map(({ group, input }) => ({ group, input, original: simulate(before, input), candidate: simulate(after, input) }))
  const mismatched = pairs.filter((p) => !sameRun(p.original, p.candidate))
  const groups: ScenarioGroup[] = [...new Set(INPUTS.map((i) => i.group))].map((name) => {
    const inGroup = pairs.filter((p) => p.group === name)
    const matched = inGroup.filter((p) => sameRun(p.original, p.candidate)).length
    return { name, total: inGroup.length, checked: inGroup.length, matched, status: matched === inGroup.length ? 'MATCHED' : 'MISMATCH' }
  })
  const shortest = (list: RunPair[]) => [...list].sort((l, r) => l.input.length - r.input.length)[0] ?? null
  return {
    ...verdict,
    status: mismatched.length ? 'MISMATCH' : 'MATCHED', phase: 'done', progress: 1, checked: pairs.length, matched: pairs.length - mismatched.length,
    mismatches: mismatched.length, groups,
    smallestMismatch: shortest(mismatched),
    example: shortest(pairs.filter((p) => p.group === 'Повторяющиеся значения')),
  }
}

/** Промежуточное состояние задачи: фаза и прогресс по прошедшему времени. */
export function progressVerdict(final: Verdict, elapsedMs: number, totalMs: number): Verdict {
  const progress = Math.min(1, elapsedMs / totalMs)
  if (progress >= 1) return { ...final, durationMs: Math.round(elapsedMs) }
  const phase = progress < 0.2 ? 'compile' : progress < 0.85 ? 'run' : 'compare'
  const checked = phase === 'compile' ? 0 : Math.round(final.inputs * Math.min(1, (progress - 0.2) / 0.65))
  return { ...emptyVerdict(), phase, progress, checked, durationMs: Math.round(elapsedMs) }
}
