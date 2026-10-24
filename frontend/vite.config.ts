import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Разработка: фронт на :5173, запросы /api проксируются на бэкенд :8080 — CORS не нужен.
// Сборка кладётся в ресурсы бэкенда, и сервер раздаёт её сам: один процесс, один порт.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
  build: {
    outDir: '../backend/src/main/resources/web',
    emptyOutDir: true,
  },
})
