// Демо-данные: грубый построчный «разбор» Java только для режима моков (VITE_USE_MOCKS=1).
// Настоящий разбор — на бэке (JavaParser). Здесь хватает аккуратно отформатированного кода вроде MatchDemo.

/** Строка без строковых литералов и комментария — чтобы считать скобки. */
export function codeOnly(line: string): string {
  return line
    .replace(/"(?:\\.|[^"\\])*"/g, '""')
    .replace(/'(?:\\.|[^'\\])*'/g, "''")
    .replace(/\/\/.*$/, '')
}

export function braceDelta(line: string): number {
  const text = codeOnly(line)
  return (text.match(/\{/g)?.length ?? 0) - (text.match(/\}/g)?.length ?? 0)
}

/** Номер строки (с 1), где закрывается блок, открытый на строке start. */
export function blockEnd(lines: string[], start: number): number {
  let depth = 0
  for (let i = start - 1; i < lines.length; i++) {
    depth += braceDelta(lines[i])
    if (depth <= 0) return i + 1
  }
  return lines.length
}

/** Первая строка, где нарушен баланс фигурных скобок, или null — «код разбирается». */
export function firstSyntaxProblem(source: string): { line: number; column: number; message: string } | null {
  const lines = source.split('\n')
  let depth = 0
  for (let i = 0; i < lines.length; i++) {
    depth += braceDelta(lines[i])
    if (depth < 0) return { line: i + 1, column: 1, message: 'Лишняя закрывающая скобка «}»' }
  }
  if (depth !== 0) return { line: lines.length, column: 1, message: 'Не хватает закрывающей скобки «}»' }
  return null
}

/** Короткий стабильный хэш текста (FNV-1a) — для id операторов без позиций, как на бэке. */
export function shortHash(text: string): string {
  let hash = 0x811c9dc5
  for (const char of text.replace(/\s+/g, ' ').trim()) {
    hash ^= char.charCodeAt(0)
    hash = Math.imul(hash, 0x01000193) >>> 0
  }
  return hash.toString(36)
}

/** Делит список параметров по запятым верхнего уровня (запятые внутри <…> не считаются). */
export function splitParams(params: string): string[] {
  const parts: string[] = []
  let depth = 0
  let current = ''
  for (const char of params) {
    if (char === '<') depth++
    if (char === '>') depth--
    if (char === ',' && depth === 0) {
      parts.push(current)
      current = ''
    } else current += char
  }
  if (current.trim()) parts.push(current)
  return parts.map((p) => p.trim()).filter(Boolean)
}

/** Тип параметра «List<Integer> a» → «List<Integer>». */
export function paramType(param: string): string {
  const withoutFinal = param.replace(/^final\s+/, '')
  return withoutFinal.slice(0, withoutFinal.lastIndexOf(' ')).trim()
}
