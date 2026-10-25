import Editor, { type OnMount } from '@monaco-editor/react'
import type * as Monaco from 'monaco-editor'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { EDITOR_FONT, EDITOR_FONT_SIZE, EDITOR_LINE_HEIGHT, THEME } from '../../components/editor/monacoSetup'
import { expressionEditorRange } from '../../components/editor/editorRanges'
import { problemDecorations } from '../../components/editor/problemDecorations'
import { collectProblems } from '../../components/editor/problems'
import { Icon } from '../../components/Icon'
import { editDraft, goToLine, selectByLine } from '../../state/actions'
import { onReveal, revealDone } from '../../state/reveal'
import { selectedExpression, selectedNode } from '../../state/selectors'
import { useAppState } from '../../state/store'
import { BaselineNotice } from './BaselineNotice'
import { ProblemsBar } from './ProblemsBar'

type CodeEditor = Monaco.editor.IStandaloneCodeEditor

/** Левая панель: Monaco с кодом; строки выбранного узла подсвечены, клик по строке выбирает узел. */
export function CodePane() {
  const draft = useAppState((s) => s.draft)
  const project = useAppState((s) => s.project)
  const errors = project?.errors
  const compileErrors = project?.compileErrors
  const projectId = useAppState((s) => s.project?.id)
  const findings = useAppState((s) => s.findings)
  const node = useAppState(selectedNode)
  const expression = useAppState(selectedExpression)
  const [editor, setEditor] = useState<CodeEditor | null>(null)
  const decorations = useRef<Monaco.editor.IEditorDecorationsCollection | null>(null)
  const problemLayer = useRef<Monaco.editor.IEditorDecorationsCollection | null>(null)
  const step = useRef<(delta: number) => void>(() => {})
  const monaco = useRef<typeof Monaco | null>(null)

  const onMount: OnMount = (instance, api) => {
    monaco.current = api
    decorations.current = instance.createDecorationsCollection()
    problemLayer.current = instance.createDecorationsCollection()
    // F8 / Shift+F8 — как «следующая ошибка» в IDE, но по нашему списку: javac и находки вместе.
    instance.addCommand(api.KeyCode.F8, () => step.current(1))
    instance.addCommand(api.KeyMod.Shift | api.KeyCode.F8, () => step.current(-1))
    instance.onMouseDown((event) => {
      const line = event.target.position?.lineNumber
      if (line && event.event.leftButton) selectByLine(line)
    })
    setEditor(instance)
  }

  const [first, last] = node?.lines ?? [0, 0]
  useEffect(() => {
    if (!editor || !decorations.current) return
    if (!first) {
      decorations.current.clear()
      return
    }
    const range = expression ? expressionEditorRange(expression.range) : null
    decorations.current.set([{
      range: { startLineNumber: first, startColumn: 1, endLineNumber: last, endColumn: 1 },
      options: { isWholeLine: true, className: 'code-selected-line', linesDecorationsClassName: 'code-selected-gutter' },
    }, ...(range ? [{ range, options: { inlineClassName: 'code-selected-expression' } }] : [])])
    if (range) {
      editor.setSelection(range)
      editor.revealRangeInCenterIfOutsideViewport(range)
    } else editor.revealLinesInCenterIfOutsideViewport(first, last)
  }, [editor, first, last, draft, expression])

  // Строки находок и ошибок относятся к тексту проекта: пока черновик не сохранён, подсветки нет.
  const problems = useMemo(() => project && project.source === draft
    ? collectProblems(draft, errors ?? [], compileErrors ?? [], findings) : [], [project, draft, errors, compileErrors, findings])
  const [current, setCurrent] = useState(-1)
  useEffect(() => setCurrent(-1), [problems])

  useEffect(() => {
    const model = editor?.getModel()
    if (!model || !monaco.current || !problemLayer.current) return
    problemLayer.current.set(problemDecorations(monaco.current, model, problems))
  }, [editor, problems])

  // Переход к строке извне (экран проверки, F8): курсор на строку, даже если узла в открытом методе нет.
  useEffect(() => {
    if (!editor) return
    return onReveal((line) => {
      const model = editor.getModel()
      if (!model) return
      const target = Math.max(1, Math.min(line, model.getLineCount()))
      editor.revealLineInCenter(target)
      editor.setPosition({ lineNumber: target, column: model.getLineFirstNonWhitespaceColumn(target) || 1 })
      editor.focus()
      revealDone()
    })
  }, [editor])

  const goTo = useCallback((index: number) => {
    const problem = problems[index]
    if (!editor || !problem) return
    setCurrent(index)
    void goToLine(problem.line)
    const column = problem.column ?? editor.getModel()?.getLineFirstNonWhitespaceColumn(problem.line) ?? 1
    editor.revealLineInCenter(problem.line)
    editor.setPosition({ lineNumber: problem.line, column: Math.max(1, column) })
    editor.focus()
  }, [editor, problems])
  step.current = (delta) => {
    if (problems.length === 0) return
    goTo(current < 0 ? (delta > 0 ? 0 : problems.length - 1) : (current + delta + problems.length) % problems.length)
  }

  const firstError = errors?.[0]
  const firstCompileError = compileErrors?.[0]
  return (
    <div className="pane code-pane">
      <div className="pane-header">
        <div className="pane-heading"><Icon name="code" /><strong>Исходный код</strong></div>
        <span className="muted">Java</span>
      </div>
      {firstError && (
        <div className="parse-banner" role="alert">
          <Icon name="alert" />
          <span><strong>Код не разбирается: строка {firstError.line}.</strong> {firstError.message} Схема показана для последней рабочей версии.</span>
        </div>
      )}
      {firstCompileError && !firstError && <div className="parse-banner" role="alert">
        <Icon name="alert" /><span><strong>Код не компилируется: строка {firstCompileError.line || 1}.</strong> {firstCompileError.text}</span>
      </div>}
      <BaselineNotice />
      <ProblemsBar problems={problems} current={current} onGo={goTo} onStep={(delta) => step.current(delta)} />
      <div className="code-editor">
        <Editor
          path={projectId ? `${projectId}.java` : 'empty.java'}
          defaultLanguage="java"
          value={draft}
          theme={THEME}
          onMount={onMount}
          onChange={(value) => editDraft(value ?? '')}
          options={{
            fontFamily: EDITOR_FONT, fontSize: EDITOR_FONT_SIZE, lineHeight: EDITOR_LINE_HEIGHT, minimap: { enabled: false },
            scrollBeyondLastLine: false, automaticLayout: true, renderLineHighlight: 'none',
            tabSize: 4, padding: { top: 8 }, lineNumbersMinChars: 3,
          }}
        />
      </div>
      <div className="code-footer">
        <span><Icon name="nodes" /> Связано со схемой</span>
        <span>{expression ? `Фрагмент ${expression.range[0]}:${expression.range[1]}–${expression.range[2]}:${expression.range[3]}` : first ? (first === last ? `Строка ${first}` : `Строки ${first}–${last}`) : 'Узел не выбран'}</span>
      </div>
    </div>
  )
}
