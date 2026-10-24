import { useState } from 'react'
import { acceptCurrentBaseline } from '../../state/actions'
import { useAppState } from '../../state/store'

/** Несобираемый оригинал нельзя сравнить: пользователь явно выбирает исправленный эталон. */
export function BaselineNotice() {
  const invalid = useAppState((s) => s.baseline?.invalid)
  const ready = useAppState((s) => !!s.project && s.project.source === s.draft && !s.project.errors.length && !s.project.compileErrors?.length)
  const running = useAppState((s) => !!s.job && (!s.job.verdict || s.job.verdict.status === 'RUNNING'))
  const [busy, setBusy] = useState(false)
  if (!invalid) return null
  const accept = async () => {
    setBusy(true)
    try { await acceptCurrentBaseline() } finally { setBusy(false) }
  }
  return <div className="baseline-notice">
    <span>Эталон не компилируется. Исправьте код и выберите новый эталон; следующие правки будут сравниваться с ним.</span>
    <button type="button" className="btn btn-small" disabled={!ready || busy || running} onClick={() => void accept()}>
      {busy ? 'Сохраняем…' : 'Сделать текущую версию эталоном'}
    </button>
  </div>
}
