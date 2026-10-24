import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

// Проверяем чистые функции и переходы состояния без браузера и новых пакетов.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const require = createRequire(import.meta.url)
const cache = new Map()
function load(relative) {
  const file = path.resolve(root, relative)
  if (cache.has(file)) return cache.get(file)
  const result = { exports: {} }
  cache.set(file, result.exports)
  const source = readFileSync(file, 'utf8')
  const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } })
  const localRequire = (name) => name.startsWith('.') ? load(path.relative(root, path.resolve(path.dirname(file), `${name}.ts`))) : require(name)
  new Function('require', 'exports', 'module', outputText)(localRequire, result.exports, result)
  return result.exports
}
const { buildMap, buildBlueprint } = load('src/api/mock/mockGraph.ts')
const { reducer, initialState } = load('src/state/reducer.ts')
const { selectedNode, selectedExpression } = load('src/state/selectors.ts')
const { diagnosticMarkers, expressionEditorRange } = load('src/components/editor/editorRanges.ts')
const { findingCategory, growthEstimate, findingGrowth } = load('src/features/evidence/findingPresentation.ts')
const { describeVerdict } = load('src/features/verification/verdictText.ts')
const fixture = JSON.parse(readFileSync(path.join(root, 'src/fixtures/frontend-behavior.json'), 'utf8'))
let expressionCount = 0
const kinds = new Set()

function offsets(source, range) {
  const lines = source.split('\n')
  const offset = (line, column) => lines.slice(0, line - 1).reduce((n, text) => n + text.length + 1, 0) + column - 1
  return [offset(range[0], range[1]), offset(range[2], range[3]) + 1]
}
for (const name of ['src/fixtures/MatchDemo.java', 'src/api/mock/ExprDemo.java']) {
  const source = readFileSync(path.join(root, name), 'utf8')
  const map = buildMap(source)
  const project = { id: 'fixture', version: 1, fileName: name, source, errors: [], compileErrors: [], map }
  for (const method of map.nodes.filter((node) => node.kind === 'method')) {
    const blueprint = buildBlueprint(source, method)
    let state = reducer(initialState, { type: 'projectLoaded', project })
    state = reducer(state, { type: 'methodOpened', blueprint, selectedNodeId: null })
    for (const node of blueprint.nodes) {
      function check(expr, parent) {
        if (!expr) return
        expressionCount++
        kinds.add(expr.kind)
        const [start, end] = offsets(source, expr.range)
        assert.equal(source.slice(start, end), expr.code, `${name}: точный range ${expr.id}`)
        if (parent) {
          const [parentStart, parentEnd] = offsets(source, parent.range)
          assert.ok(start >= parentStart && end <= parentEnd, 'Ребёнок внутри диапазона родителя')
          assert.ok(expr.role, 'У вложенного выражения есть роль')
        }
        state = reducer(state, { type: 'selectExpr', nodeId: node.id, expressionId: expr.id })
        assert.equal(selectedNode(state)?.id, node.id, 'Выбран оператор для editStatement')
        assert.equal(selectedExpression(state)?.id, expr.id, 'Выбрано точное выражение')
        const monaco = expressionEditorRange(expr.range)
        assert.equal(monaco.endColumn, expr.range[3] + 1, 'Включительный конец преобразован для Monaco')
        for (const child of expr.children) check(child, expr)
      }
      check(node.expr, null)
    }
    state = reducer(state, { type: 'toggleExpressions' })
    assert.equal(state.detailedExpressions, false)
    assert.equal(state.selectedExprId, null)
    state = reducer(state, { type: 'draftChanged', text: `${source}\n` })
    const target = blueprint.nodes.find((node) => node.expr)
    if (target) {
      const rejected = reducer(state, { type: 'selectExpr', nodeId: target.id, expressionId: target.expr.id })
      assert.equal(rejected.selectedExprId, null, 'Устаревший range после правки не выбирается')
    }
  }
}
for (const kind of ['call', 'binary', 'ternary', 'instanceof', 'cast', 'lambda', 'new', 'assign']) assert.ok(kinds.has(kind), `Есть ${kind}`)
let realExpressionCount = 0
let syntheticCount = 0
for (const [fixtureName, sourceName] of [
  ['matchdemo.countMatches.blueprint.json', 'MatchDemo/MatchDemo.java'],
  ['minilang.largest.blueprint.json', 'MiniLang/MiniLang.java'],
]) {
  const blueprint = JSON.parse(readFileSync(path.join(root, 'src/fixtures', fixtureName), 'utf8'))
  const source = readFileSync(path.join(root, '../corpus/ours', sourceName), 'utf8')
  const project = { id: fixtureName, version: 1, fileName: sourceName, source, errors: [], map: { nodes: [], edges: [] } }
  let state = reducer(initialState, { type: 'projectLoaded', project })
  state = reducer(state, { type: 'methodOpened', blueprint, selectedNodeId: null })
  let count = 0
  let synthetic = 0
  for (const node of blueprint.nodes) {
    function checkReal(expr, parent) {
      if (!expr) return
      const [start, end] = offsets(source, expr.range)
      assert.ok(start >= 0 && end > start && end <= source.length, `${fixtureName}: диапазон внутри исходника`)
      assert.ok(expr.range[0] >= node.lines[0] && expr.range[2] <= node.lines[1], 'Выражение внутри строк оператора')
      assert.ok(expr.id.startsWith(`${node.id}/e`), 'Идентификатор выражения принадлежит оператору')
      if (expr.kind === 'other' && /^ещё \d+$/.test(expr.label)) {
        synthetic++
      } else {
        const fragment = source.slice(start, end)
        const expected = fragment.length > 200 ? `${fragment.slice(0, 199)}…` : fragment
        assert.equal(expr.code, expected, `${fixtureName}: code соответствует inclusive range ${expr.id}`)
        count++
      }
      if (parent) {
        const [parentStart, parentEnd] = offsets(source, parent.range)
        assert.ok(start >= parentStart && end <= parentEnd, 'Ребёнок внутри диапазона родителя')
      }
      state = reducer(state, { type: 'selectExpr', nodeId: node.id, expressionId: expr.id })
      assert.equal(selectedNode(state)?.id, node.id, 'Настоящее выражение выбирает свой оператор')
      assert.equal(selectedExpression(state)?.id, expr.id, 'Настоящее выражение выбрано по AST')
      assert.equal(expressionEditorRange(expr.range).endColumn, expr.range[3] + 1)
      for (const child of expr.children) checkReal(child, expr)
    }
    checkReal(node.expr, null)
  }
  assert.ok(count > 0, `${fixtureName}: есть настоящие выражения`)
  realExpressionCount += count
  syntheticCount += synthetic
  console.log(`OK: ${fixtureName}: ${count} настоящих выражений, ${synthetic} синтетических, ${blueprint.nodes.length} операторов`)
}
const markers = diagnosticMarkers(fixture.source, [], fixture.compileErrors)
assert.deepEqual(markers[0], { startLineNumber: 2, endLineNumber: 2, startColumn: 17, endColumn: 18, message: fixture.compileErrors[0].text, severity: 8 })
assert.equal(diagnosticMarkers(fixture.source, [], [])[0], undefined, 'Маркеры исчезают после исправления')
for (const finding of fixture.findingCases) {
  assert.equal(findingCategory(finding), finding.expectedCategory)
  assert.equal(!!growthEstimate(finding), finding.expectedGrowth)
  assert.ok(!findingGrowth(finding).includes('null'))
}
const bad = { id: 'compile', version: 1, source: fixture.source, fileName: 'Broken.java', errors: [], compileErrors: fixture.compileErrors, map: { nodes: [], edges: [] } }
let state = reducer(initialState, { type: 'projectLoaded', project: bad })
assert.equal(state.baseline.invalid, true)
assert.equal(reducer(state, { type: 'baselineAccepted' }).baseline.invalid, true, 'Несобираемый код не становится эталоном')
const fixed = { ...bad, version: 2, source: bad.source.replace('missing', '1'), compileErrors: [] }
state = reducer(state, { type: 'projectSaved', project: fixed })
assert.equal(reducer(state, { type: 'baselineAccepted' }).baseline.invalid, true, 'Несохранённый текст не теряется при смене эталона')
state = reducer(state, { type: 'draftChanged', text: fixed.source })
state = reducer(state, { type: 'baselineAccepted' })
assert.equal(state.baseline.source, fixed.source)
assert.equal(state.baseline.version, 2)
assert.equal(state.baseline.invalid, false)
// Два оператора на одной строке разных версий — это два оператора, а не одна строка или итерации.
const verdict = {
  status: 'MATCHED', durationMs: 10, checked: 4, inputs: 4, matched: 4, nondeterministic: 0,
  changedCovered: { lines: 2, covered: 2, minHits: 3, inputs: 4, inputsReached: 4, onOriginal: false,
    statements: [{ line: 7, inputs: 3, onOriginal: true }, { line: 7, inputs: 4, onOriginal: false }] },
}
const coveredText = describeVerdict(verdict).detail
assert.ok(coveredText.includes('2 из 2 операторов'))
assert.ok(coveredText.includes('3 разных входах'))
assert.doesNotMatch(coveredText, /строк|итерац|\d+ раз\b/)
assert.ok(describeVerdict({ ...verdict, changedCovered: null }).detail.includes('покрытие изменений не измерено'))
console.log(`OK: ${expressionCount} мок-выражений + ${realExpressionCount} настоящих + ${syntheticCount} синтетических; inclusive range, выбор оператора, краткий режим, диагностика, категории, смена эталона и текст покрытия обеих сторон`)
