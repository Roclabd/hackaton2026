import Editor, { type OnMount } from '@monaco-editor/react'
import type * as Monaco from 'monaco-editor'
import { useEffect, useRef, useState } from 'react'
import { EDITOR_FONT, EDITOR_FONT_SIZE, EDITOR_LINE_HEIGHT, THEME } from '../../components/editor/monacoSetup'
import { diagnosticMarkers, expressionEditorRange } from '../../components/editor/editorRanges'
import { Icon } from '../../components/Icon'
import { editDraft, selectByLine } from '../../state/actions'
import { selectedExpression, selectedNode } from '../../state/selectors'
import { useAppState } from '../../state/store'
import { BaselineNotice } from './BaselineNotice'

type CodeEditor = Monaco.editor.IStandaloneCodeEditor

/** Левая панель: Monaco с кодом; строки выбранного узла подсвечены, клик по строке выбирает узел. */
export function CodePane() {
  const draft = useAppState((s) => s.draft)
  const project = useAppState((s) => s.project)
  const errors = project?.errors
  const compileErrors = project?.compileErrors
  const projectId = useAppState((s) => s.project?.id)
  const node = useAppState(selectedNode)
  const expression = useAppState(selectedExpression)
  const [editor, setEditor] = useState<CodeEditor | null>(null)
  const decorations = useRef<Monaco.editor.IEditorDecorationsCollection | null>(null)
  const monaco = useRef<typeof Monaco | null>(null)

  const onMount: OnMount = (instance, api) => {
    monaco.current = api
    decorations.current = instance.createDecorationsCollection()
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

  useEffect(() => {
    const model = editor?.getModel()
    if (!model || !monaco.current) return
    const markers = project?.source === draft ? diagnosticMarkers(draft, errors ?? [], compileErrors ?? []) : []
    monaco.current.editor.setModelMarkers(model, 'blueprints', markers)
    return () => { monaco.current?.editor.setModelMarkers(model, 'blueprints', []) }
  }, [editor, projectId, project?.source, draft, errors, compileErrors])

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
