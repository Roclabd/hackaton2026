import { ApiError, request } from './client'

// Экспорт тестов по завершённой проверке (бэк: blueprints.export.ExportRoutes).
// GET /api/projects/{id}/export/junit/preview?jobId=… → JSON ниже
// GET /api/projects/{id}/export/junit?jobId=…         → application/zip (мини-проект Maven)

/** Предпросмотр экспорта: что будет в архиве. */
export interface JUnitExportPreview {
  /** имя архива, например MatchDemo-tests.zip */
  readonly zipName: string
  /** путь к классу тестов внутри архива */
  readonly testFile: string
  /** чем запускаются тесты, например «JUnit Jupiter 6.1.3» */
  readonly framework: string
  /** сколько тестов — по одному на сравненный вход */
  readonly tests: number
  /** сколько входов не стали тестами: случайный, неизвестный или слишком большой вывод */
  readonly skipped: number
  /** текст первого метода теста */
  readonly firstTest: string
}

const exportPath = (projectId: string, jobId: string, suffix = '') =>
  `/api/projects/${encodeURIComponent(projectId)}/export/junit${suffix}?jobId=${encodeURIComponent(jobId)}`

export function getJUnitPreview(projectId: string, jobId: string): Promise<JUnitExportPreview> {
  return request<JUnitExportPreview>('GET', exportPath(projectId, jobId, '/preview'))
}

/** Скачивает ZIP: сначала запрос (чтобы показать ошибку тостом), потом сохранение файла браузером. */
export async function downloadJUnitZip(projectId: string, jobId: string, fileName: string): Promise<void> {
  let response: Response
  try {
    response = await fetch(exportPath(projectId, jobId))
  } catch {
    throw new ApiError(0, 'Сервер не отвечает')
  }
  if (!response.ok) {
    const data = await response.json().catch(() => ({}))
    throw new ApiError(response.status, data.error ?? `Не удалось скачать архив (${response.status})`)
  }
  const url = URL.createObjectURL(await response.blob())
  try {
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
  } finally {
    // Браузеру нужен миг, чтобы начать сохранение; потом ссылка не нужна.
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
}
