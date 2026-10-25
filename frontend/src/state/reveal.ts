// Просьба «прокрути редактор к строке N». Отдельно от выбора узла: строка может быть вне открытого метода,
// а редактор может ещё не существовать (переход с экрана проверки) — тогда просьба ждёт его появления.

type Listener = (line: number) => void

let pending: number | null = null
const listeners = new Set<Listener>()

export function requestReveal(line: number): void {
  pending = line
  listeners.forEach((listener) => listener(line))
}

/** Подписка редактора; невыполненная просьба отдаётся сразу. Возвращает отписку. */
export function onReveal(listener: Listener): () => void {
  listeners.add(listener)
  if (pending !== null) listener(pending)
  return () => { listeners.delete(listener) }
}

/** Редактор выполнил просьбу — при следующем монтировании не прыгать снова. */
export function revealDone(): void {
  pending = null
}
