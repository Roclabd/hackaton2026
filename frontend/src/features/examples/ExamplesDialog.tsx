import { useEffect, useState } from 'react'
import { api, USE_MOCKS } from '../../api/client'
import type { ExampleInfo } from '../../api/types'
import { Dialog } from '../../components/Dialog'
import { Icon } from '../../components/Icon'
import { loadExample, reportError } from '../../state/actions'
import { dispatch } from '../../state/store'

/** Выбор примера: GET /api/examples → GET /api/examples/{fileName} → POST /api/projects. */
export function ExamplesDialog() {
  const [examples, setExamples] = useState<ExampleInfo[] | null>(null)
  const [failed, setFailed] = useState(false)
  const [opening, setOpening] = useState<string | null>(null)
  const close = () => dispatch({ type: 'closeDialog' })

  useEffect(() => {
    api.listExamples().then(setExamples).catch((error) => { setFailed(true); reportError(error) })
  }, [])

  const open = async (fileName: string) => {
    setOpening(fileName)
    await loadExample(fileName)
    setOpening(null)
  }

  return (
    <Dialog title="Открыть Java-пример" className="examples-dialog" onClose={close} actions={<button type="button" className="btn" onClick={close}>Отмена</button>}>
      <p className="dialog-intro">
        {USE_MOCKS
          ? 'Режим демо-данных: ответы сервера имитируются в браузере (src/api/mock.ts).'
          : 'Пример загрузится на сервер как обычный файл: карта, чертежи и проверка — настоящие.'}
      </p>
      {examples === null && !failed && <p className="muted">Загружаем список…</p>}
      {failed && <p className="output-error">Список примеров недоступен. Можно загрузить свой файл кнопкой «Загрузить .java».</p>}
      <div className="example-list">
        {examples?.map((example) => (
          <button key={example.fileName} type="button" className="example-option" disabled={opening !== null} onClick={() => void open(example.fileName)}>
            <Icon name="file" />
            <span>
              <strong>{example.fileName}</strong>
              <span>{example.title}</span>
              <small>{opening === example.fileName ? 'Открываем…' : example.description}</small>
            </span>
            <Icon name="chevron" />
          </button>
        ))}
      </div>
    </Dialog>
  )
}
