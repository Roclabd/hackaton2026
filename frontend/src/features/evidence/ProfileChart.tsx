import type { GrowthSeries } from '../../api/profileApi'
import { compactCount, exactCount, timesText, xPositions, yTicks } from './profileChartScale'
import { useElementSize } from './useElementSize'

// График замера роста — как chart() в docs/design/Java-Blueprints-demo.html: ось X — размер входа n,
// ось Y — выполнения тел циклов (операции, а не время), линии «до» и «после». Все числа — из ответа /profile.
// SVG рисуется в настоящих пикселях контейнера: подписи остаются читаемыми и в низкой нижней панели.

const LEFT = 52
const RIGHT = 14
const TOP = 26
const BOTTOM = 32
const MIN_HEIGHT = 110

interface ProfileChartProps {
  sizes: readonly number[]
  before: GrowthSeries
  after: GrowthSeries | null
  factor: number | null
}

export function ProfileChart(props: ProfileChartProps) {
  const [ref, size] = useElementSize<HTMLDivElement>()
  return (
    <div ref={ref} className="profile-chart-box">
      {size && size.width > 0 && <ChartSvg {...props} width={size.width} height={Math.max(MIN_HEIGHT, size.height)} />}
    </div>
  )
}

function ChartSvg({ sizes, before, after, factor, width: w, height: h }: ProfileChartProps & { width: number; height: number }) {
  const xs = xPositions(sizes, LEFT, w - RIGHT)
  const ticks = yTicks(Math.max(...before.counts, ...(after?.counts ?? [0])))
  const top = ticks[ticks.length - 1]
  const y = (v: number) => h - BOTTOM - (v / top) * (h - TOP - BOTTOM)
  const last = sizes.length - 1
  const label = `Выполнения тел циклов по n = ${sizes.join(', ')}: до (${before.model}) ${before.counts.join(', ')}`
    + (after ? `; после (${after.model}) ${after.counts.join(', ')}` : '')

  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} role="img" aria-label={label} className="profile-chart">
      {ticks.map((v) => (
        <g key={v}>
          <line x1={LEFT} y1={y(v)} x2={w - RIGHT} y2={y(v)} stroke="var(--line)" strokeWidth="1" />
          <text x={LEFT - 7} y={y(v) + 4} fill="var(--muted)" fontSize="11" textAnchor="end">{v === 0 ? '0' : compactCount(v)}</text>
        </g>
      ))}
      {sizes.map((n, i) => (
        <text key={n} x={xs[i]} y={h - BOTTOM + 14} fill="var(--muted)" fontSize="11" textAnchor="middle">{n}</text>
      ))}
      <text x="4" y={h - 3} fill="var(--muted)" fontSize="11">↑ выполнения тела цикла — операции, а не время</text>
      <text x={w - 4} y={h - 3} fill="var(--muted)" fontSize="11" textAnchor="end">размер входа n →</text>
      <Line xs={xs} counts={before.counts} y={y} color="var(--orange)" />
      {after && <Line xs={xs} counts={after.counts} y={y} color="var(--blue)" />}
      <text x={xs[last] - 8} y={y(before.counts[last]) - 4} fill="var(--orange)" fontSize="11" textAnchor="end">
        {compactCount(before.counts[last])}
      </text>
      {after && (
        <text x={xs[last] - 6} y={y(after.counts[last]) - 6} fill="var(--blue)" fontSize="11" textAnchor="end">
          {compactCount(after.counts[last])}
        </text>
      )}
      <Legend x={4} color="var(--orange)" text={`до · ${before.model}`} />
      {after && <Legend x={74} color="var(--blue)" text={`после · ${after.model}`} />}
      {factor ? (
        <text x={w - 4} y="15" fill="var(--blue)" fontSize="11.5" fontWeight="600" textAnchor="end">
          меньше {timesText(factor)} при n = {sizes[last]}
        </text>
      ) : null}
    </svg>
  )
}

function Line({ xs, counts, y, color }: { xs: number[]; counts: readonly number[]; y: (v: number) => number; color: string }) {
  const path = counts.map((v, i) => `${i ? 'L' : 'M'} ${xs[i].toFixed(1)} ${y(v).toFixed(1)}`).join(' ')
  return (
    <g>
      <path d={path} fill="none" stroke={color} strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
      {counts.map((v, i) => (
        <circle key={i} cx={xs[i]} cy={y(v)} r="3" fill="var(--surface)" stroke={color} strokeWidth="2.2">
          <title>{exactCount(v)}</title>
        </circle>
      ))}
    </g>
  )
}

function Legend({ x, color, text }: { x: number; color: string; text: string }) {
  return (
    <g>
      <circle cx={x + 4} cy="11" r="3.5" fill={color} />
      <text x={x + 12} y="15" fill="var(--ink)" fontSize="11">{text}</text>
    </g>
  )
}
