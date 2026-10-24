import type { Finding, Graph, Patch } from '../types'
import { buildBlueprint } from './mockGraph'

// Демо-данные: находка «вложенный цикл» и заплатка «индекс частот» для countMatches (только режим моков).

const INDEXED_BODY = [
  '        Map<Integer, Long> freq = new HashMap<>();',
  '        for (Integer y : b) {',
  '            freq.merge(y, 1L, Long::sum);',
  '        }',
  '        long total = 0;',
  '        for (Integer x : a) {',
  '            total += freq.getOrDefault(x, 0L);',
  '        }',
  '        return total;',
]

/** Находки: метод, где цикл вложен в цикл. Заплатка есть только для countMatches. */
export function findNestedLoops(source: string, map: Graph): Finding[] {
  const findings: Finding[] = []
  for (const method of map.nodes.filter((n) => n.kind === 'method')) {
    const blueprint = buildBlueprint(source, method)
    const loops = blueprint.nodes.filter((n) => n.kind === 'loop')
    const outer = loops.find((loop) => loops.some((inner) => inner.parent === loop.id))
    if (!outer) continue
    const isCountMatches = method.id.includes('#countMatches(')
    findings.push({
      id: `R1-${findings.length + 1}`,
      rule: 'R1',
      methodId: method.id,
      lines: outer.lines,
      title: 'Вложенный цикл',
      why: 'Для каждого элемента внешнего списка просматривается весь внутренний: число сравнений растёт как n².',
      growthBefore: 'n²',
      growthAfter: isCountMatches ? 'n' : 'n²',
      conditions: isCountMatches ? [
        'Ключи — Integer: equals и hashCode согласованы',
        'Повторы учитываются: индекс хранит число вхождений',
        'null — допустимый ключ HashMap, обработка null сохраняется',
        'Входные списки не изменяются',
      ] : ['Заплатка для этого метода в демо-данных не подготовлена'],
      patches: isCountMatches ? [indexPatch(source, method.lines)] : [],
    })
  }
  return findings
}

function indexPatch(source: string, [start, end]: [number, number]): Patch {
  const lines = source.split('\n')
  const patched = [...lines.slice(0, start), ...INDEXED_BODY, ...lines.slice(end - 1)]
  const imports = ['import java.util.HashMap;', 'import java.util.Map;'].filter((line) => !lines.includes(line))
  const lastImport = patched.reduce((last, line, i) => (line.startsWith('import ') ? i : last), -1)
  patched.splice(lastImport + 1, 0, ...imports)
  const firstChanged = start + imports.length + 1
  return {
    id: 'R1-1-index',
    intent: 'REFACTOR',
    title: 'Индекс частот вместо перебора',
    description: 'Один проход по b строит индекс частот, второй суммирует совпадения для каждого элемента a.',
    source: patched.join('\n'),
    changedLines: [firstChanged, firstChanged + INDEXED_BODY.length - 1],
  }
}
