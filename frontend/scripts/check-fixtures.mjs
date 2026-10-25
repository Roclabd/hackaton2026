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
const { buildTree, CONTAINER_KINDS } = load('src/features/blueprint/blueprintTree.ts')
const { buildClassCards } = load('src/features/map/mapModel.ts')
const { reducer, initialState } = load('src/state/reducer.ts')
const { selectedNode, selectedExpression } = load('src/state/selectors.ts')
const { diagnosticMarkers, expressionEditorRange } = load('src/components/editor/editorRanges.ts')
const { findingCategory, growthEstimate, findingGrowth } = load('src/features/evidence/findingPresentation.ts')
const { describeVerdict } = load('src/features/verification/verdictText.ts')
const fixture = JSON.parse(readFileSync(path.join(root, 'src/fixtures/frontend-behavior.json'), 'utf8'))

// Проверяем отображение enum-констант по полному настоящему графу из API.
const scope5Graph = JSON.parse(readFileSync(path.join(root, 'src/fixtures/scope5.map.json'), 'utf8'))
const scope5Cards = buildClassCards(scope5Graph)
for (const name of ['ADD', 'MUL']) {
  const original = scope5Graph.nodes.find((node) => node.kind === 'class' && node.id.endsWith(`<constant:${name}>`))
  assert.ok(original, `Scope5 API fixture содержит enum-константу ${name}`)
  const card = scope5Cards.find((item) => item.id === original.id)
  assert.ok(card, `Есть карточка enum-константы ${name}`)
  assert.equal(card.name, name, `${name}: имя карточки без технического owner segment`)
  assert.equal(card.fullName, original.id.slice(2).replace(/<constant:([^>]+)>/g, '$1'),
    `${name}: полное отображаемое имя очищено, raw id сохранён отдельно`)
  assert.equal(card.kind, 'константа', `${name}: подпись типа взята из API label`)
  assert.equal(card.id, original.id, `${name}: внутренний id не нормализован`)
  assert.ok(card.methods.some((method) => method.label.includes('apply(')), `${name}: метод apply доступен в карточке`)
}
assert.ok(scope5Cards.flatMap((card) => card.calls).every((call) => !call.target.includes('<constant:')),
  'Подписи целей вызовов не показывают технический owner segment')

// Эти настоящие backend fixtures обязательны: CI не должен молча пропускать проверку.
const bonusBlueprintPath = path.join(root, 'src/fixtures/bonusblocks.run.blueprint.json')
const bonusSourcePath = path.join(root, 'src/fixtures/BonusBlocks.java')
{
  const bonusBlueprint = JSON.parse(readFileSync(bonusBlueprintPath, 'utf8'))
  const bonusSource = readFileSync(bonusSourcePath, 'utf8')
  const tree = buildTree(bonusBlueprint)
  const treeById = new Map()
  const visit = (items) => items.forEach((item) => {
    assert.ok(!treeById.has(item.node.id), `Узел ${item.node.id} не дублируется в дереве`)
    treeById.set(item.node.id, item)
    item.branches.forEach((branch) => visit(branch.children))
  })
  visit(tree)
  assert.equal(treeById.size, bonusBlueprint.nodes.length, 'Все узлы BonusBlocks fixture достижимы в дереве ровно один раз')
  const bonusBranchKeys = bonusBlueprint.nodes.flatMap((node) => node.branches ?? []).map((branch) => branch.key)
  assert.ok(bonusBranchKeys.some((key) => key.startsWith('lambda:')), 'Fixture содержит ветку block lambda')
  assert.ok(bonusBranchKeys.some((key) => key.startsWith('switch:')), 'Fixture содержит ветку switch expression')
  let nestedSelectionChecked = false
  let nestedEditableSelectionChecked = false
  for (const node of bonusBlueprint.nodes) {
    assert.ok(treeById.has(node.id), `Узел fixture ${node.id} доступен в дереве`)
    if ((node.branches?.length ?? 0) > 0) {
      assert.ok(CONTAINER_KINDS.includes(node.kind) || treeById.get(node.id).branches.length > 0,
        `Ветвящийся оператор ${node.id} отображается контейнером`)
      for (const branch of node.branches) {
        const children = treeById.get(node.id).branches.find((entry) => entry.key === branch.key)?.children ?? []
        const actualChildren = bonusBlueprint.nodes
          .filter((child) => child.parent === node.id && (child.branch ?? 'body') === branch.key)
          .sort((left, right) => left.lines[0] - right.lines[0] || left.lines[1] - right.lines[1])
          .map((child) => child.id)
        assert.deepEqual(children.map((child) => child.node.id), actualChildren,
          `Ветка ${branch.key} узла ${node.id} содержит дочерние операторы из fixture в порядке строк`)
        for (const child of children) {
          let state = reducer(initialState, { type: 'methodOpened', blueprint: bonusBlueprint, selectedNodeId: null })
          state = reducer(state, { type: 'select', nodeId: child.node.id })
          assert.equal(selectedNode(state)?.id, child.node.id, `${bonusSourcePath}: вложенный оператор ${child.node.id} выбирается`)
          nestedSelectionChecked = true
          if (child.node.editable) nestedEditableSelectionChecked = true
        }
      }
    }
  }
  assert.ok(nestedSelectionChecked, 'В BonusBlocks fixture есть реальный вложенный оператор для проверки выбора')
  assert.ok(nestedEditableSelectionChecked, 'В BonusBlocks fixture выбирается вложенный editable оператор')
  assert.ok(bonusSource.length > 0, 'Java fixture BonusBlocks.java не пустой')
  console.log(`OK: bonus blocks — ${treeById.size} реальных узлов, ветвящиеся операторы и выбор вложенных узлов`)
}
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

// Частота исполнения: ноль приходит с сервера, отсутствие счётчика не означает ноль.
const { executionCount, heatColor } = load('src/features/blueprint/heatModel.ts')
const heatResult = { methodId: 'M:Heat#run()', runs: 1, counts: { zero: 0, loop: 1, body: 9 }, maxCount: 9, note: 'fixture только для переходов состояния' }
assert.equal(executionCount(heatResult, 'zero'), 0)
assert.equal(executionCount(heatResult, 'missing'), null)
assert.equal(executionCount(null, 'zero'), null)
assert.equal(executionCount({ ...heatResult, counts: { invalid: -1 } }, 'invalid'), null)
assert.equal(heatColor(0, 9), 'rgb(255, 249, 240)')
assert.equal(heatColor(9, 9), 'rgb(243, 153, 80)')
assert.equal(heatColor(0, 0), 'rgb(255, 249, 240)')
const heatProject = { ...fixed, id: 'heat-project', map: { nodes: [], edges: [] } }
const heatBlueprint = { methodId: heatResult.methodId, label: 'run()', nodes: [], edges: [] }
const heatRequest = { requestId: 1, projectId: heatProject.id, version: heatProject.version, methodId: heatResult.methodId, loading: true, result: null }
let heatState = reducer(reducer(initialState, { type: 'projectLoaded', project: heatProject }), { type: 'methodOpened', blueprint: heatBlueprint, selectedNodeId: null })
assert.equal(reducer({ ...heatState, level: 'map' }, { type: 'heatStarted', heat: heatRequest }).heat, null, 'С карты запрос не запускается')
heatState = reducer(heatState, { type: 'heatStarted', heat: heatRequest })
assert.equal(heatState.heat.loading, true)
assert.equal(reducer(heatState, { type: 'heatLoaded', requestId: 0, result: heatResult }).heat.result, null, 'Старый запрос не заменяет новый')
assert.equal(reducer(heatState, { type: 'heatLoaded', requestId: 1, result: { ...heatResult, methodId: 'another' } }).heat.result, null, 'Другой метод не получает чужие счётчики')
const heatLoaded = reducer(heatState, { type: 'heatLoaded', requestId: 1, result: heatResult })
assert.deepEqual(heatLoaded.heat.result, heatResult)
assert.equal(heatLoaded.heat.loading, false)
for (const action of [
  { type: 'draftChanged', text: `${heatProject.source}\n` },
  { type: 'projectSaved', project: { ...heatProject, version: heatProject.version + 1 } },
  { type: 'patchApplied', project: heatProject },
  { type: 'commandApplied', response: { ok: true, compileErrors: [], source: heatProject.source, version: heatProject.version + 1 } },
  { type: 'methodOpened', blueprint: { ...heatBlueprint, methodId: 'another' }, selectedNodeId: null },
  { type: 'blueprintRefreshed', blueprint: heatBlueprint },
  { type: 'showMap' },
  { type: 'projectLoaded', project: { ...heatProject, id: 'another-project' } },
]) {
  const changed = reducer(heatLoaded, action)
  assert.equal(changed.heat, null, `${action.type}: измерение сброшено`)
  assert.equal(reducer(changed, { type: 'heatLoaded', requestId: 1, result: heatResult }).heat, null, `${action.type}: поздний ответ игнорируется`)
}
assert.equal(reducer(heatLoaded, { type: 'heatCleared', requestId: 0 }).heat, heatLoaded.heat, 'Старая ошибка не убирает актуальное измерение')
assert.equal(reducer(heatLoaded, { type: 'heatCleared' }).heat, null)
console.log('OK: heat — реальные счётчики, отсутствие ≠ 0, шкала, загрузка, смена метода/проекта/версии и устаревшие ответы')

// Асинхронный action проверяем на управляемом ответе API: запросы и ошибки не выдумывают результатов.
let asyncState = heatState
let heatCalls = 0
let finishHeat
const heatErrors = []
const actionModule = { exports: {} }
const actionSource = ts.transpileModule(readFileSync(path.join(root, 'src/state/heatActions.ts'), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText
new Function('require', 'exports', 'module', actionSource)((name) => ({
  '../api/heatApi': { measureHeat: () => { heatCalls++; return new Promise((resolve, reject) => { finishHeat = { resolve, reject } }) } },
  './actions': { flushDraft: async () => {}, reportError: (error) => heatErrors.push(error.message) },
  './store': { store: { getState: () => asyncState }, dispatch: (action) => { asyncState = reducer(asyncState, action) } },
})[name], actionModule.exports, actionModule)
const { toggleHeat } = actionModule.exports
asyncState = { ...heatState, heat: null, level: 'map' }
await toggleHeat()
assert.equal(heatCalls, 0, 'Невыбранный метод не запрашивается')
asyncState = { ...heatState, heat: null }
let pendingHeat = toggleHeat()
await Promise.resolve()
await toggleHeat()
assert.equal(heatCalls, 1, 'Повторный клик не запускает параллельное измерение')
finishHeat.resolve(heatResult)
await pendingHeat
assert.deepEqual(asyncState.heat.result, heatResult)
await toggleHeat()
assert.equal(asyncState.heat, null, 'Готовое измерение выключается без запроса')
pendingHeat = toggleHeat()
await Promise.resolve()
asyncState = reducer(asyncState, { type: 'draftChanged', text: 'changed' })
finishHeat.resolve(heatResult)
await pendingHeat
assert.equal(asyncState.heat, null, 'Поздний ответ после правки игнорируется')
asyncState = { ...heatState, heat: null }
pendingHeat = toggleHeat()
await Promise.resolve()
finishHeat.reject(new Error('Точный текст ошибки сервера'))
await pendingHeat
assert.deepEqual(heatErrors, ['Точный текст ошибки сервера'])
assert.equal(asyncState.heat, null)
asyncState = { ...heatState, heat: null }
pendingHeat = toggleHeat()
await Promise.resolve()
asyncState = reducer(asyncState, { type: 'showMap' })
finishHeat.reject(new Error('Уже устаревшая ошибка'))
await pendingHeat
assert.equal(heatErrors.length, 1, 'Устаревшая ошибка не тревожит новый экран')
console.log('OK: heat action — реальный запрос только открытого метода, защита двойного клика, поздние ответы/ошибки, серверный текст ошибки')
