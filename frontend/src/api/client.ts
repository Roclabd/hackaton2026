import type {
  Blueprint, CommandResponse, EditStatementCommand, ExampleInfo, ExampleSource,
  Finding, GateRequest, ProjectResponse, Verdict,
} from './types'

// Все запросы к бэкенду — только через этот файл. Пути и формы — docs/API.md и docs/ПРОТОТИП_КОНТРАКТ.md.

/** Ошибка ответа сервера: статус нужен, чтобы отличить 409 (версия устарела) и 404. */
export class ApiError extends Error {
  readonly status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

/** Общий запрос к бэку. Экспортирован, чтобы модули (экспорт, замер роста) держали свои вызовы в своих файлах. */
export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      method,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'Сервер не отвечает')
  }
  const data = await response.json().catch(() => ({}))
  if (!response.ok) {
    const fallback = response.status === 404
      ? `Маршрут не найден (404): ${method} ${path}`
      : response.status >= 500
        ? `Сервер не ответил (${response.status}): ${method} ${path} — запущен ли бэкенд на :8080?`
        : `Ошибка ${response.status}: ${method} ${path}`
    const text = data.error ?? fallback
    throw new ApiError(response.status, text)
  }
  return data as T
}

const projectPath = (id: string) => `/api/projects/${encodeURIComponent(id)}`

const realApi = {
  health: () => request<{ status: string }>('GET', '/api/health'),

  uploadProject: (fileName: string, source: string) =>
    request<ProjectResponse>('POST', '/api/projects', { fileName, source }),

  getProject: (id: string) => request<ProjectResponse>('GET', projectPath(id)),

  updateSource: (id: string, source: string, baseVersion: number) =>
    request<ProjectResponse>('PUT', `${projectPath(id)}/source`, { source, baseVersion }),

  getMethod: (id: string, methodId: string) =>
    request<Blueprint>('GET', `${projectPath(id)}/methods/${encodeURIComponent(methodId)}`),

  editStatement: (id: string, command: EditStatementCommand) =>
    request<CommandResponse>('POST', `${projectPath(id)}/commands`, command),

  getFindings: (id: string) => request<Finding[]>('GET', `${projectPath(id)}/findings`),

  startGate: (id: string, gate: GateRequest) =>
    request<{ jobId: string }>('POST', `${projectPath(id)}/gate`, gate),

  getJob: (jobId: string) => request<Verdict>('GET', `/api/jobs/${encodeURIComponent(jobId)}`),

  listExamples: () => request<ExampleInfo[]>('GET', '/api/examples'),

  getExample: (fileName: string) =>
    request<ExampleSource>('GET', `/api/examples/${encodeURIComponent(fileName)}`),
}

export type Api = typeof realApi

/** Моки включаются только вручную: VITE_USE_MOCKS=1 npm run dev. Без флага код моков в сборку не попадает. */
export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === '1'

function lazyMockApi(): Api {
  const load = () => import('./mock').then((m) => m.mockApi)
  const names = Object.keys(realApi) as (keyof Api)[]
  return Object.fromEntries(names.map((name) => [
    name,
    async (...args: unknown[]) => {
      const mock = await load()
      return (mock[name] as (...a: unknown[]) => unknown)(...args)
    },
  ])) as unknown as Api
}

export const api: Api = USE_MOCKS ? lazyMockApi() : realApi
