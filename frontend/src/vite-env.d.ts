/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** «1» — работать на демо-данных из src/api/mock.ts без бэкенда */
  readonly VITE_USE_MOCKS?: string
}
