import { useRef } from 'react'
import { USE_MOCKS } from '../../api/client'
import { Icon } from '../../components/Icon'
import { uploadFile } from '../../state/actions'
import { runEditorCheck } from '../../state/gateActions'
import { dispatch, useAppState } from '../../state/store'
import { StageNav } from './StageNav'

/** Шапка в одну строку: бренд, выбор примера, этапы, загрузка .java, «Запустить и сравнить». */
export function Header() {
  const fileName = useAppState((s) => s.project?.fileName)
  const hasProject = useAppState((s) => s.project !== null)
  const running = useAppState((s) => s.job?.verdict?.status === 'RUNNING' || (s.job !== null && s.job.verdict === null))
  const fileInput = useRef<HTMLInputElement>(null)

  const onFileChosen = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (file) void uploadFile(file)
    event.target.value = ''
  }

  return (
    <header className="app-header">
      <a className="brand" href="#workspace" aria-label="Java Blueprints, рабочая область">
        <svg className="brand-mark" viewBox="0 0 28 28" aria-hidden="true">
          <path d="M7 4v20M21 4v20M7 10h14M7 18h14" fill="none" stroke="currentColor" strokeWidth="1.7" />
          <rect x="3" y="6" width="8" height="8" rx="2" fill="#F37022" />
          <rect x="17" y="14" width="8" height="8" rx="2" fill="#003FA9" />
        </svg>
        <span>Java Blueprints</span>
      </a>
      <span className="brand-context">кейс Axenix</span>
      <div className="header-divider" />
      <button type="button" className="file-selector" onClick={() => dispatch({ type: 'openDialog', dialog: 'examples' })} title="Открыть пример">
        <Icon name="file" />
        <span>{fileName ?? 'Выберите пример'}</span>
        <Icon name="down" />
      </button>
      {USE_MOCKS && <span className="prototype-badge"><Icon name="info" />Демо-данные</span>}
      <StageNav />
      <div className="header-actions">
        <button type="button" className="btn upload-button" onClick={() => fileInput.current?.click()}>
          <Icon name="upload" /><span>Загрузить .java</span>
        </button>
        <input ref={fileInput} type="file" accept=".java,text/x-java" hidden onChange={onFileChosen} />
        <button
          type="button"
          className="btn btn-orange run-button"
          disabled={!hasProject || running}
          aria-busy={running}
          onClick={() => void runEditorCheck()}
        >
          <Icon name="play" /><span>{running ? 'Идёт проверка…' : 'Запустить и сравнить'}</span>
        </button>
      </div>
    </header>
  )
}
