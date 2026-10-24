import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
import './components/editor/monacoSetup'
import './styles/fonts.css'
import './styles/tokens.css'
import './styles/layout.css'
import './styles/app.css'
import './styles/blueprint.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
