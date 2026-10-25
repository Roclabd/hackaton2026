import type { CompileError, Finding, ParseError } from '../../api/types'

/**
 * Всё, что подсвечивается в коде, — одним списком: ошибки разбора и javac, находки правил.
 * У каждого вида свой цвет строки, подчёркивания и метки на полосе прокрутки (классы — в app.css).
 */
export type ProblemKind = 'syntax' | 'compile' | 'defect' | 'inefficiency' | 'deadCode'

export interface Problem {
  kind: ProblemKind
  /** первая и последняя строка, с 1 */
  line: number
  endLine: number
  /** колонка ошибки с 1; null — у находки, подчёркивается весь текст первой строки */
  column: number | null
  /** «Несовпадение типов», «Вложенный цикл»… */
  title: string
  detail: string
}

export interface ProblemStyle {
  label: string
  /** цвет метки на полосе прокрутки — тот же, что у подчёркивания в app.css */
  ruler: string
}

/** Порядок — по тяжести: сначала то, что мешает запуску. */
export const PROBLEM_KINDS: ProblemKind[] = ['syntax', 'compile', 'defect', 'inefficiency', 'deadCode']

export const PROBLEM_STYLES: Record<ProblemKind, ProblemStyle> = {
  syntax: { label: 'Синтаксис', ruler: '#d42a2f' },
  compile: { label: 'Компиляция', ruler: '#c0267a' },
  defect: { label: 'Дефект', ruler: '#e0620d' },
  inefficiency: { label: 'Неэффективность', ruler: '#6d3fd1' },
  deadCode: { label: 'Мёртвый код', ruler: '#7b8798' },
}

/** Подвид ошибки javac по тексту: английский текст JDK или наш русский. */
export function compileTitle(text: string): string {
  const t = text.toLowerCase()
  if (/incompatible types|cannot be converted|bad operand|несовместим/.test(t)) return 'Несовпадение типов'
  if (/cannot find symbol|не найден/.test(t)) return 'Неизвестное имя'
  if (/missing return|unreachable|might not have been initialized|not have been initialized/.test(t)) return 'Поток выполнения'
  if (/unreported exception|must be caught/.test(t)) return 'Необработанное исключение'
  if (/expected|illegal start/.test(t)) return 'Синтаксис javac'
  return 'Ошибка компиляции'
}

function findingKind(finding: Finding): ProblemKind {
  if (finding.category === 'DEFECT') return 'defect'
  if (finding.category === 'DEAD_CODE') return 'deadCode'
  return 'inefficiency'
}

function clampLine(line: number, lineCount: number): number {
  return Math.max(1, Math.min(line || 1, lineCount))
}

/** Строки находок считаются по тексту проекта, поэтому список строится только для неизменённого черновика. */
export function collectProblems(source: string, parse: ParseError[], compile: CompileError[], findings: Finding[]): Problem[] {
  const lineCount = Math.max(1, source.split('\n').length)
  const problems: Problem[] = [
    ...parse.map((e): Problem => {
      const line = clampLine(e.line, lineCount)
      return { kind: 'syntax', line, endLine: line, column: e.column || 1, title: 'Код не разбирается', detail: e.message }
    }),
    ...compile.map((e): Problem => {
      const line = clampLine(e.line, lineCount)
      return { kind: 'compile', line, endLine: line, column: e.column || 1, title: compileTitle(e.text), detail: e.text }
    }),
    ...findings.map((f): Problem => {
      const line = clampLine(f.lines[0], lineCount)
      return {
        kind: findingKind(f), line, endLine: Math.max(line, clampLine(f.lines[1], lineCount)), column: null,
        title: `${f.rule}: ${f.title}`, detail: f.why,
      }
    }),
  ]
  // Сверху вниз по файлу; на одной строке первой идёт более тяжёлая проблема.
  return problems.sort((a, b) => a.line - b.line || PROBLEM_KINDS.indexOf(a.kind) - PROBLEM_KINDS.indexOf(b.kind))
}

export function countByKind(problems: Problem[]): Record<ProblemKind, number> {
  const counts = { syntax: 0, compile: 0, defect: 0, inefficiency: 0, deadCode: 0 }
  for (const p of problems) counts[p.kind]++
  return counts
}
