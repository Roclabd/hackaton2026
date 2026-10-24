// Расчёты для графика замера роста — отдельно от разметки, чтобы компонент только рисовал.

/** Шаг сетки по оси Y: «круглое» число (1, 2, 5 × 10^k), чтобы делений было около четырёх. */
export function niceStep(max: number, divisions = 4): number {
  if (max <= 0) return 1
  const raw = max / divisions
  const power = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 5, 10].map((m) => m * power).find((s) => s >= raw)
  return step ?? 10 * power
}

/** Деления оси Y от 0 до значения не меньше max. */
export function yTicks(max: number): number[] {
  const step = niceStep(max)
  const top = Math.max(step, Math.ceil(max / step) * step)
  const ticks: number[] = []
  for (let v = 0; v <= top + step / 2; v += step) ticks.push(v)
  return ticks
}

/** Короткая подпись числа операций: 2 561 600 → «2,56 млн», 40 200 → «40,2 тыс». */
export function compactCount(value: number): string {
  const format = (v: number) => v.toLocaleString('ru-RU', { maximumFractionDigits: v < 10 ? 2 : 1 })
  if (value >= 1e9) return `${format(value / 1e9)} млрд`
  if (value >= 1e6) return `${format(value / 1e6)} млн`
  if (value >= 1e4) return `${format(value / 1e3)} тыс`
  return value.toLocaleString('ru-RU')
}

/** Полное число с разрядами — для подсказок у точек. */
export function exactCount(value: number): string {
  return value.toLocaleString('ru-RU')
}

/**
 * Позиции по оси X: размеры удваиваются, поэтому шкала логарифмическая — шаги одинаковой ширины.
 * На линейной шкале 100, 200 и 400 слиплись бы у левого края.
 */
export function xPositions(sizes: readonly number[], left: number, right: number): number[] {
  if (sizes.length === 1) return [(left + right) / 2]
  const first = Math.log(sizes[0])
  const span = Math.log(sizes[sizes.length - 1]) - first
  return sizes.map((n) => left + ((Math.log(n) - first) / span) * (right - left))
}

/** «в 800,5 раза», «в 37 раз», «в 2 раза» — множитель словами. */
export function timesText(factor: number): string {
  const text = factor.toLocaleString('ru-RU', { maximumFractionDigits: 1 })
  if (!Number.isInteger(factor)) return `в ${text} раза`
  const last = factor % 10
  const teen = factor % 100 >= 12 && factor % 100 <= 14
  return `в ${text} ${!teen && last >= 2 && last <= 4 ? 'раза' : 'раз'}`
}
