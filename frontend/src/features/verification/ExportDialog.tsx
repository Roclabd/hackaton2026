import { useEffect, useState } from 'react'
import { downloadJUnitZip, getJUnitPreview, type JUnitExportPreview } from '../../api/exportApi'
import { Dialog } from '../../components/Dialog'
import { Icon } from '../../components/Icon'
import { reportError, showToast } from '../../state/actions'
import { describeExport } from './exportText'

interface ExportDialogProps {
  projectId: string
  jobId: string
  onClose: () => void
}

/** Окно «Экспорт теста», как showExport() в макете: файл, фреймворк, первый тест и кнопка «Скачать ZIP». */
export function ExportDialog({ projectId, jobId, onClose }: ExportDialogProps) {
  const [preview, setPreview] = useState<JUnitExportPreview | null>(null)
  const [downloading, setDownloading] = useState(false)

  useEffect(() => {
    let active = true
    getJUnitPreview(projectId, jobId)
      .then((loaded) => { if (active) setPreview(loaded) })
      .catch((error) => { if (active) { reportError(error); onClose() } })
    return () => { active = false }
  }, [projectId, jobId, onClose])

  const download = async () => {
    if (!preview) return
    setDownloading(true)
    try {
      await downloadJUnitZip(projectId, jobId, preview.zipName)
      showToast(`Скачан ${preview.zipName}`)
    } catch (error) {
      reportError(error)
    } finally {
      setDownloading(false)
    }
  }

  const actions = (
    <>
      <button type="button" className="btn" onClick={onClose}>Закрыть</button>
      <button type="button" className="btn btn-blue" onClick={download} disabled={!preview || downloading}>
        <Icon name="download" />{downloading ? 'Скачиваем…' : 'Скачать ZIP'}
      </button>
    </>
  )

  return (
    <Dialog title="Экспорт теста" className="wide-dialog" onClose={onClose} actions={actions}>
      {!preview ? (
        <p className="dialog-intro">Готовим предпросмотр…</p>
      ) : (
        <>
          <p className="dialog-intro">{describeExport(preview).intro}</p>
          <div className="export-file"><Icon name="file" />{fileName(preview.testFile)}<span>{preview.framework}</span></div>
          <pre className="export-code">{preview.firstTest}</pre>
          <div className="inline-demo-note"><Icon name="info" />{describeExport(preview).note}</div>
        </>
      )}
    </Dialog>
  )
}

function fileName(path: string): string {
  return path.slice(path.lastIndexOf('/') + 1)
}
