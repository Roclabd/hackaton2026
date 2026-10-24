import type { ExprKind, ExprNode, GraphNode } from '../types'

// Демо-данные: деревья выражений (ExprNode) для нескольких методов, написанные вручную по контракту docs/API.md.
// Только для режима моков. Дерево задаём текстом фрагментов, а range считаем поиском фрагмента в исходнике:
// ребёнок ищется внутри родителя, каждый следующий ребёнок — после предыдущего.

interface Spec {
  kind: ExprKind
  label: string
  code: string
  role: string | null
  kids: Spec[]
}

const e = (kind: ExprKind, label: string, code: string, ...kids: Spec[]): Spec => ({ kind, label, code, role: null, kids })
const as = (role: string, spec: Spec): Spec => ({ ...spec, role })
const name = (text: string) => e('name', text, text)
const lit = (text: string) => e('literal', text, text)
const declare = (type: string, variable: string, init: Spec) => e('declare', type, `${type} ${variable} = ${init.code}`, as(variable, init))
const bin = (sign: string, left: Spec, right: Spec) =>
  e(sign === '+' && (left.kind === 'concat' || right.kind === 'concat' || isString(left) || isString(right)) ? 'concat' : 'binary',
    sign, `${left.code} ${sign} ${right.code}`, as('слева', left), as('справа', right))
const isString = (spec: Spec) => spec.kind === 'literal' && spec.label.startsWith('"')

/** Вызов: объект (может не быть) и аргументы «арг N». Статический вызов «Класс.метод» — без ребёнка «объект». */
function call(label: string, object: Spec | null, ...args: Spec[]): Spec {
  const isStatic = object !== null && object.kind === 'name' && /^[A-Z]/.test(object.label)
  const method = isStatic ? label.slice(label.indexOf('.') + 1) : label
  const target = object ? `${object.code}.${method}` : method
  const code = `${target}(${args.map((a) => a.code).join(', ')})`
  const kids = [...(object && !isStatic ? [as('объект', object)] : []), ...args.map((a, i) => as(`арг ${i + 1}`, a))]
  return { kind: 'call', label: isStatic ? target : label, code, role: null, kids }
}

const ternary = (condition: Spec, yes: Spec, no: Spec) =>
  e('ternary', '?:', `${condition.code} ? ${yes.code} : ${no.code}`, as('условие', condition), as('да', yes), as('нет', no))
const lambda = (params: string, body: Spec) => e('lambda', `${params} ->`, `${params} -> ${body.code}`, as('тело', body))
const forEach = (type: string, variable: string, collection: Spec) =>
  e('forEachHeader', 'for-each', `${type} ${variable} : ${collection.code}`,
    as('элемент', e('declare', type, `${type} ${variable}`)), as('коллекция', collection))

/** Ключ — code оператора (или label, если code нет): так мок находит дерево для узла чертежа. */
const SPECS: Record<string, Spec> = {
  // MatchDemo.countMatches
  'long total = 0;': declare('long', 'total', lit('0')),
  'for (Integer x : a)': forEach('Integer', 'x', name('a')),
  'for (Integer y : b)': forEach('Integer', 'y', name('b')),
  'Objects.equals(x, y)': call('Objects.equals', name('Objects'), name('x'), name('y')),
  'total++;': e('unary', '++', 'total++', as('значение', name('total'))),
  'return total;': name('total'),
  // MatchDemo.parse
  'List<Integer> values = new ArrayList<>();': declare('List<Integer>', 'values', e('new', 'new ArrayList<>', 'new ArrayList<>()')),
  'for (String token : line.trim().split("\\\\s+"))': forEach('String', 'token', call('split', call('trim', name('line')), lit('"\\\\s+"'))),
  'token.isEmpty()': call('isEmpty', name('token')),
  'values.add(token.equals("null") ? null : Integer.valueOf(token));':
    call('add', name('values'), ternary(call('equals', name('token'), lit('"null"')), lit('null'), call('Integer.valueOf', name('Integer'), name('token')))),
  'return values;': name('values'),
  // ExprDemo.describe — тернарный, instanceof, приведение, лямбды, склейка строк
  'String kind = value instanceof String s ? "строка из " + s.length() : "другое";':
    declare('String', 'kind', ternary(
      e('instanceof', 'instanceof String s', 'value instanceof String s', as('значение', name('value'))),
      bin('+', lit('"строка из "'), call('length', name('s'))),
      lit('"другое"'))),
  'int limit = value instanceof Integer ? (Integer) value : items.size();':
    declare('int', 'limit', ternary(
      e('instanceof', 'instanceof Integer', 'value instanceof Integer', as('значение', name('value'))),
      e('cast', '(Integer)', '(Integer) value', as('значение', name('value'))),
      call('size', name('items')))),
  'items.removeIf(x -> x == null || x > limit);':
    call('removeIf', name('items'), lambda('x', bin('||', bin('==', name('x'), lit('null')), bin('>', name('x'), name('limit'))))),
  'items.sort((a, b) -> Integer.compare(b, a));':
    call('sort', name('items'), lambda('(a, b)', call('Integer.compare', name('Integer'), name('b'), name('a')))),
  'String text = kind + ": " + items.size();': declare('String', 'text', bin('+', bin('+', name('kind'), lit('": "')), call('size', name('items')))),
  'return items.isEmpty() ? text : text + ", максимум " + Collections.max(items);':
    ternary(call('isEmpty', name('items')), name('text'),
      bin('+', bin('+', name('text'), lit('", максимум "')), call('Collections.max', name('Collections'), name('items')))),
  // ExprDemo.main — глубокое выражение: проверка сворачивания
  'text += "!";': e('assign', '+=', 'text += "!"', as('цель', name('text')), as('значение', lit('"!"'))),
  'List<Integer> items = new ArrayList<>(List.of(5, 1, 9, 3));':
    declare('List<Integer>', 'items', e('new', 'new ArrayList<>', 'new ArrayList<>(List.of(5, 1, 9, 3))',
      as('арг 1', call('List.of', name('List'), lit('5'), lit('1'), lit('9'), lit('3'))))),
  'System.out.println(describe(args.length > 0 ? (Object) args[0] : Integer.valueOf(4), items));':
    call('println', e('field', 'out', 'System.out', as('объект', name('System'))),
      call('describe', null,
        ternary(
          bin('>', e('field', 'length', 'args.length', as('объект', name('args'))), lit('0')),
          e('cast', '(Object)', '(Object) args[0]', as('значение', e('index', 'args[0]', 'args[0]', as('массив', name('args')), as('индекс', lit('0'))))),
          call('Integer.valueOf', name('Integer'), lit('4'))),
        name('items'))),
}

/** Дерево выражения для узла мок-чертежа или null, если для оператора дерева нет. */
export function mockExpr(source: string, node: GraphNode): ExprNode | null {
  const spec = SPECS[node.code ?? ''] ?? SPECS[node.label]
  if (!spec) return null
  const lineStarts = lineOffsets(source)
  const from = lineStarts[node.lines[0] - 1] ?? 0
  return place(spec, `${node.id}/e`, source, from, lineStarts).node
}

function place(spec: Spec, id: string, source: string, from: number, lineStarts: number[]): { node: ExprNode; end: number } {
  const found = source.indexOf(spec.code, from)
  const start = found >= 0 ? found : from
  const end = start + spec.code.length
  let cursor = start
  const children = spec.kids.map((kid, index) => {
    const placed = place(kid, `${id}/${index}`, source, cursor, lineStarts)
    cursor = placed.end
    return placed.node
  })
  const [line, column] = position(lineStarts, start)
  const [endLine, endColumn] = position(lineStarts, Math.max(start, end - 1))
  return {
    node: { id, kind: spec.kind, label: spec.label, code: spec.code, range: [line, column, endLine, endColumn], role: spec.role, children },
    end,
  }
}

function lineOffsets(source: string): number[] {
  const starts = [0]
  for (let i = 0; i < source.length; i++) if (source[i] === '\n') starts.push(i + 1)
  return starts
}

/** Смещение в тексте → [строка, колонка], обе с 1. */
function position(lineStarts: number[], offset: number): [number, number] {
  let line = 0
  while (line + 1 < lineStarts.length && lineStarts[line + 1] <= offset) line++
  return [line + 1, offset - lineStarts[line] + 1]
}
