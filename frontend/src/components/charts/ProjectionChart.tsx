import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { motion, useReducedMotion } from 'motion/react'
import { chartTransition } from '../../lib/motion'
import type { SavingsProjection } from '../../lib/api/types'
import { formatMonth, formatMonthShort } from '../../lib/months'
import { formatAxisRupees, formatMoney, plotValue } from '../../lib/money'

interface Datum {
  label: string
  month: string
  balanceText: string
  balance: number
}

function TooltipContent({ active, payload }: { active?: boolean; payload?: { payload: Datum }[] }) {
  const d = payload?.[0]?.payload
  if (!active || !d) return null
  return (
    <div className="rounded-control border border-line-strong bg-elevated px-3 py-2 text-xs shadow-overlay">
      <p className="text-muted">{formatMonth(d.month)}</p>
      <p className="figure mt-0.5 font-semibold text-ink">{d.balanceText}</p>
    </div>
  )
}

/** Projected balance (one series, so no legend) with the target as a labelled reference line; 0% return assumed. */
export function ProjectionChart({ projection }: { projection: SavingsProjection }) {
  // A clip-path is not a transform, so MotionConfig would not stop it; reduced motion skips the reveal here.
  const reduce = useReducedMotion()
  const data: Datum[] = projection.series.map((p) => ({
    label: formatMonthShort(p.month),
    month: p.month,
    balanceText: formatMoney(p.balance),
    balance: plotValue(p.balance),
  }))
  return (
    // The plot is revealed left to right, so the projection reads as drawing forward in time. Recharts' own line
    // animation is off: the reveal is motion's, on the same tokens as every other chart.
    <motion.div
      className="h-52"
      role="img"
      aria-label="Projected savings balance by month"
      initial={reduce ? false : { clipPath: 'inset(0 100% 0 0)' }}
      animate={{ clipPath: 'inset(0 0% 0 0)' }}
      transition={{ ...chartTransition(), duration: 1.1 }}
    >
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 12, right: 12, bottom: 0, left: 0 }}>
          <CartesianGrid vertical={false} stroke="var(--color-line)" />
          <XAxis
            dataKey="label"
            tickLine={false}
            axisLine={{ stroke: 'var(--color-line-strong)' }}
            tick={{ fill: 'var(--color-faint)', fontSize: 12 }}
            minTickGap={16}
          />
          <YAxis
            tickFormatter={formatAxisRupees}
            tickLine={false}
            axisLine={false}
            width={56}
            tick={{ fill: 'var(--color-faint)', fontSize: 12 }}
          />
          <Tooltip
            content={<TooltipContent />}
            cursor={{ stroke: 'var(--color-line-strong)', strokeWidth: 1 }}
          />
          <ReferenceLine
            y={plotValue(projection.target)}
            stroke="var(--color-muted)"
            strokeWidth={1}
            label={{
              value: `Target ${formatMoney(projection.target)}`,
              fill: 'var(--color-muted)',
              fontSize: 11,
              position: 'insideTopLeft',
            }}
          />
          <Line
            dataKey="balance"
            type="linear"
            stroke="var(--color-series-income)"
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 4, stroke: 'var(--color-surface)', strokeWidth: 2 }}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </motion.div>
  )
}
