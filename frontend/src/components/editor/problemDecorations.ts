import type * as Monaco from 'monaco-editor'
import { PROBLEM_STYLES, type Problem } from './problems'

type Model = Monaco.editor.ITextModel

/**
 * Элемент, который подчёркивается: у ошибки — слово под колонкой (или один символ),
 * у находки — текст первой строки без отступа.
 */
export function elementRange(model: Model, problem: Problem): Monaco.IRange {
  const line = problem.line
  const lastColumn = model.getLineMaxColumn(line)
  if (problem.column === null) {
    const start = Math.max(1, model.getLineFirstNonWhitespaceColumn(line))
    return { startLineNumber: line, startColumn: start, endLineNumber: line, endColumn: Math.max(start + 1, model.getLineLastNonWhitespaceColumn(line)) }
  }
  const column = Math.max(1, Math.min(problem.column, lastColumn))
  const word = model.getWordAtPosition({ lineNumber: line, column })
  if (word) return { startLineNumber: line, startColumn: word.startColumn, endLineNumber: line, endColumn: word.endColumn }
  // Колонка на знаке или в конце строки: хотя бы один символ, иначе подчёркивания не видно.
  const start = column < lastColumn ? column : Math.max(1, lastColumn - 1)
  return { startLineNumber: line, startColumn: start, endLineNumber: line, endColumn: Math.max(start + 1, Math.min(lastColumn, start + 1)) }
}

/**
 * Три слоя на проблему: вся строка (или строки находки) с цветным фоном и полосой у номеров,
 * волнистое подчёркивание элемента и метка того же цвета на полосе прокрутки.
 */
export function problemDecorations(monaco: typeof Monaco, model: Model, problems: Problem[]): Monaco.editor.IModelDeltaDecoration[] {
  return problems.flatMap((problem) => {
    const style = PROBLEM_STYLES[problem.kind]
    const hover = { value: `**${style.label} · ${problem.title}**\n\n${problem.detail}` }
    return [
      {
        range: { startLineNumber: problem.line, startColumn: 1, endLineNumber: problem.endLine, endColumn: 1 },
        options: {
          isWholeLine: true,
          className: `problem-line problem-line-${problem.kind}`,
          linesDecorationsClassName: `problem-gutter problem-gutter-${problem.kind}`,
          overviewRuler: { color: style.ruler, position: monaco.editor.OverviewRulerLane.Full },
          stickiness: monaco.editor.TrackedRangeStickiness.NeverGrowsWhenTypingAtEdges,
        },
      },
      {
        range: elementRange(model, problem),
        options: { inlineClassName: `problem-element problem-element-${problem.kind}`, hoverMessage: hover },
      },
    ]
  })
}
