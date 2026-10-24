import type { CompileError, ExprRange, ParseError } from '../../api/types'

/** JavaParser даёт включительный конец, а Monaco — позицию после последнего символа. */
export function expressionEditorRange(range: ExprRange) {
  return { startLineNumber: range[0], startColumn: range[1], endLineNumber: range[2], endColumn: range[3] + 1 }
}

/** Ошибка без позиции остаётся в баннере и получает маркер в начале файла. */
export function diagnosticMarkers(source: string, parse: ParseError[], compile: CompileError[]) {
  const lines = source.split('\n')
  return [...parse.map((e) => ({ line: e.line, column: e.column, text: e.message })), ...compile].map((error) => {
    const line = Math.max(1, Math.min(error.line || 1, lines.length))
    const column = Math.max(1, Math.min(error.column || 1, lines[line - 1].length + 1))
    return { startLineNumber: line, endLineNumber: line, startColumn: column, endColumn: column + 1, message: error.text, severity: 8 }
  })
}
