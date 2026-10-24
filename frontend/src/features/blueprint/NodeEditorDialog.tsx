import { useState } from 'react'
import type { CompileError } from '../../api/types'
import { Dialog } from '../../components/Dialog'
import { Icon } from '../../components/Icon'
import { editNode } from '../../state/actions'
import { methodName, selectedNode } from '../../state/selectors'
import { dispatch, useAppState } from '../../state/store'

const isCondition = (kind: string) => kind === 'if' || kind === 'loop'

/** Правка узла схемы: текст → POST /commands (editStatement). При ошибке компиляции окно не закрывается. */
export function NodeEditorDialog() {
  const node = useAppState(selectedNode)
  const methodId = useAppState((s) => s.methodId)
  const [text, setText] = useState(node?.code ?? '')
  const [errors, setErrors] = useState<CompileError[]>([])
  const [busy, setBusy] = useState(false)
  const close = () => dispatch({ type: 'closeDialog' })

  if (!node || !node.editable) return null

  const submit = async () => {
    if (!text.trim()) {
      setErrors([{ line: 0, column: 0, text: 'Введите выражение или отмените правку.' }])
      return
    }
    setBusy(true)
    const result = await editNode(node.id, text.trim())
    setBusy(false)
    if (result.length) setErrors(result)
    else close()
  }

  const onKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      void submit()
    }
  }

  return (
    <Dialog
      title={isCondition(node.kind) ? 'Редактирование условия' : 'Редактирование оператора'}
      className="node-editor"
      onClose={close}
      actions={<>
        <button type="button" className="btn" onClick={close}>Отмена</button>
        <button type="button" className="btn btn-blue" onClick={() => void submit()} disabled={busy}>{busy ? 'Проверяем…' : 'Применить'}</button>
      </>}
    >
      <div className="dialog-path"><Icon name="nodes" />{methodId ? methodName(methodId) : ''} · строки {node.lines[0]}–{node.lines[1]}</div>
      <label className="field-label" htmlFor="node-expression">{isCondition(node.kind) ? 'Java-выражение условия' : 'Java-оператор'}</label>
      <textarea
        id="node-expression"
        className="text-input mono node-text"
        value={text}
        rows={Math.min(6, Math.max(1, text.split('\n').length))}
        aria-invalid={errors.length > 0}
        autoFocus
        spellCheck={false}
        onChange={(event) => setText(event.target.value)}
        onKeyDown={onKeyDown}
      />
      {errors.length > 0 ? (
        <ul className="compile-errors" role="alert">
          {errors.map((e, i) => <li key={i}>{e.line > 0 ? `Строка ${e.line}:${e.column} — ` : ''}{e.text}</li>)}
        </ul>
      ) : (
        <p className="field-help">Enter — применить. Правка проходит через дерево разбора и javac; код и схема обновятся вместе.</p>
      )}
    </Dialog>
  )
}
