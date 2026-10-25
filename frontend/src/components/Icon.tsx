// Иконки из макета: один спрайт <symbol> на страницу, ссылки через <use>.

const SYMBOLS: Record<string, string> = {
  flame: '<path d="M12 3c1 4-3 5-3 8 0 1 1 2 2 2 2-1 3-3 3-5 3 3 5 6 5 9a7 7 0 0 1-14 0c0-4 3-6 3-9 1 1 2 2 2 3 2-2 3-5 2-8Z"/>',
  file: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"/><path d="M14 2v6h6M8 13h8M8 17h5"/>',
  upload: '<path d="M12 16V3m-5 5 5-5 5 5M4 16v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4"/>',
  play: '<path d="m8 4 13 8-13 8Z"/>',
  search: '<circle cx="10.5" cy="10.5" r="7.5"/><path d="m16 16 5 5"/>',
  pencil: '<path d="m16 3 5 5L8 21H3v-5Zm-3 3 5 5"/>',
  chevron: '<path d="m9 5 7 7-7 7"/>',
  down: '<path d="m6 9 6 6 6-6"/>',
  up: '<path d="m6 15 6-6 6 6"/>',
  close: '<path d="m6 6 12 12M6 18 18 6"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
  alert: '<circle cx="12" cy="12" r="9"/><path d="M12 7v6m0 4h.01"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10h.01"/>',
  chart: '<path d="M3 3v18h18M7 15l5-5 4 2 5-7"/>',
  list: '<path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/>',
  undo: '<path d="m8 4-5 5 5 5M3 9h10a7 7 0 1 1 0 14"/>',
  download: '<path d="M12 3v13m-5-5 5 5 5-5M4 17v4h16v-4"/>',
  maximize: '<path d="M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5"/>',
  nodes: '<rect x="9" y="2" width="6" height="5" rx="1"/><rect x="1" y="17" width="6" height="5" rx="1"/><rect x="17" y="17" width="6" height="5" rx="1"/><path d="M12 7v5M4 17v-5h16v5"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  help: '<circle cx="12" cy="12" r="9"/><path d="M9.5 8.5a2.5 2.5 0 0 1 5 .5c0 2-2.5 2-2.5 4m0 4h.01"/>',
  code: '<path d="m8 6-6 6 6 6M16 6l6 6-6 6M14 3l-4 18"/>',
  compare: '<path d="M8 3v18M16 3v18M3 7l3-3 3 3M15 17l3 3 3-3"/>',
}

export type IconName = keyof typeof SYMBOLS

export function IconSprite() {
  const defs = Object.entries(SYMBOLS)
    .map(([name, body]) => `<symbol id="i-${name}" viewBox="0 0 24 24">${body}</symbol>`)
    .join('')
  return <svg className="icon-definitions" aria-hidden="true" dangerouslySetInnerHTML={{ __html: `<defs>${defs}</defs>` }} />
}

export function Icon({ name, className = '' }: { name: IconName; className?: string }) {
  return (
    <svg className={`icon ${className}`} aria-hidden="true">
      <use href={`#i-${name}`} />
    </svg>
  )
}
