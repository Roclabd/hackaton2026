import { loader } from '@monaco-editor/react'
import * as monaco from 'monaco-editor'
import EditorWorker from 'monaco-editor/editor/editor.worker?worker'

// Monaco из локального пакета, а не с CDN: на площадке может не быть интернета.
// Путь воркера — через exports пакета 0.57: «monaco-editor/editor/…» = esm/vs/editor/….
// Для Java хватает базового воркера редактора — языковые сервисы TS/CSS/JSON не нужны.

self.MonacoEnvironment = { getWorker: () => new EditorWorker() }
loader.config({ monaco })

/** Светлая тема в цветах макета: синий — ключевые слова, приглушённый — комментарии. */
monaco.editor.defineTheme('blueprints-light', {
  base: 'vs',
  inherit: true,
  rules: [
    { token: 'keyword', foreground: '003fa9' },
    { token: 'type', foreground: '5f438a' },
    { token: 'string', foreground: '81501a' },
    { token: 'number', foreground: '8a4d1a' },
    { token: 'comment', foreground: '768397' },
  ],
  colors: {
    'editor.background': '#ffffff',
    'editorLineNumber.foreground': '#8491a4',
    'editorLineNumber.activeForeground': '#003fa9',
    'editor.lineHighlightBackground': '#f3f6fb',
  },
})

export const EDITOR_FONT = "'JetBrains Mono', Consolas, monospace"
// Плотнее, чем в макете: на ноутбуке при 100% масштабе страницы видно больше строк.
export const EDITOR_FONT_SIZE = 13
export const EDITOR_LINE_HEIGHT = 20
export const THEME = 'blueprints-light'
