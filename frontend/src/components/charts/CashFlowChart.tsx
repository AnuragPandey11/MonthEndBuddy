import { useState } from 'react'
import { motion, useReducedMotion } from 'motion/react'
import type { TrendPoint } from '../../lib/api/types'
import { chartTransition, duration, ease } from '../../lib/motion'
import { formatMonth, formatMonthShort } from '../../lib/months'
import { formatMoney, plotValue } from '../../lib/money'
import { DataTable } from '../ChartCard'

/** A drawn height, as a share of the tallest value in the window. Drawing only: labels show the API's strings. */
function heightPct(value: number, max: number): number {
  return max > 0 ? (value / max) * 100 : 0
}

/**
 * Income vs expenses per month on one axis, as rounded columns: the larger value is the back pill and the smaller
 * one sits in front of it with a surface ring, so both read from the same baseline (never a dual axis). A month with
 * no transactions is hatched, never drawn as zero. The latest month with data is highlighted with its values pinned
 * above it; hovering or focusing a month moves the highlight.
 */
export function CashFlowChart({ points }: { points: TrendPoint[] }) {
  const reduce = useReducedMotion()
  const latest = points.findLastIndex((p) => p.hasData)
  const [hovered, setHovered] = useState<number | null>(null)
  const active = hovered ?? (latest >= 0 ? latest : points.length - 1)
  const max = Math.max(...points.flatMap((p) => [plotValue(p.income), plotValue(p.expenses)]), 0)
  const activePoint = points[active]
  const activeTop = activePoint?.hasData
    ? heightPct(Math.max(plotValue(activePoint.income), plotValue(activePoint.expenses)), max)
    : 0
  const n = points.length

  return (
    <div>
      <ul className="mb-3 flex gap-4 text-xs text-muted" aria-label="Legend">
        <li className="flex items-center gap-1.5">
          <span className="size-2.5 rounded-pill bg-series-income" aria-hidden /> Income
        </li>
        <li className="flex items-center gap-1.5">
          <span className="size-2.5 rounded-pill bg-series-expense" aria-hidden /> Expenses
        </li>
      </ul>

      {/* Headroom above the bars for the pinned values. */}
      <div className="relative pt-28">
        <div
          role="group"
          aria-label="Monthly income and expenses; use the Table button for every value"
          className="relative grid h-44 gap-2.5"
          style={{ gridTemplateColumns: `repeat(${n}, minmax(0, 1fr))` }}
          onMouseLeave={() => setHovered(null)}
        >
          {activePoint?.hasData && (
            <motion.div
              className="pointer-events-none absolute inset-x-0 border-t border-dashed border-faint/60"
              initial={false}
              animate={{ bottom: `${activeTop}%` }}
              transition={reduce ? { duration: 0 } : { duration: duration.base, ease }}
              aria-hidden
            >
              <span className="absolute -top-5 left-0 text-caption text-faint">Amount (₹)</span>
            </motion.div>
          )}

          {points.map((p, i) => {
            const income = heightPct(plotValue(p.income), max)
            const expenses = heightPct(plotValue(p.expenses), max)
            const incomeInFront = income <= expenses
            const isActive = i === active
            return (
              <button
                key={p.month}
                type="button"
                onMouseEnter={() => setHovered(i)}
                onFocus={() => setHovered(i)}
                onBlur={() => setHovered(null)}
                aria-label={
                  p.hasData
                    ? `${formatMonth(p.month)}: income ${formatMoney(p.income)}, expenses ${formatMoney(p.expenses)}`
                    : `${formatMonth(p.month)}: no transactions recorded`
                }
                className="relative flex h-full items-end justify-center rounded-[1.25rem] focus-visible:outline-offset-4"
              >
                {p.hasData ? (
                  <span
                    className={`relative block h-full w-full max-w-16 transition-opacity duration-200 ${isActive ? '' : 'opacity-35'}`}
                    aria-hidden
                  >
                    <Pill
                      heightPct={incomeInFront ? expenses : income}
                      className={incomeInFront ? 'bg-series-expense' : 'bg-series-income'}
                      index={i}
                    />
                    <Pill
                      heightPct={incomeInFront ? income : expenses}
                      className={`ring-2 ring-surface ${incomeInFront ? 'bg-series-income' : 'bg-series-expense'}`}
                      index={i}
                      front
                    />
                  </span>
                ) : (
                  <motion.span
                    className="hatch block h-[85%] w-full max-w-16 origin-bottom rounded-[1.25rem] border border-line"
                    initial={{ scaleY: 0, opacity: 0 }}
                    animate={{ scaleY: 1, opacity: 1 }}
                    transition={chartTransition(i)}
                    aria-hidden
                  />
                )}
              </button>
            )
          })}
        </div>

        {activePoint && (
          <PinnedValues
            point={activePoint}
            index={active}
            count={n}
            topPct={activePoint.hasData ? activeTop : 85}
          />
        )}
      </div>

      <div
        className="mt-2 grid gap-2.5 text-center text-xs"
        style={{ gridTemplateColumns: `repeat(${n}, minmax(0, 1fr))` }}
        aria-hidden
      >
        {points.map((p, i) => (
          <span key={p.month} className={i === active ? 'font-semibold text-ink' : 'text-faint'}>
            {formatMonthShort(p.month).toUpperCase()}
          </span>
        ))}
      </div>
    </div>
  )
}

function Pill({
  heightPct,
  className,
  index,
  front = false,
}: {
  heightPct: number
  className: string
  index: number
  front?: boolean
}) {
  return (
    <motion.span
      className={`absolute inset-x-0 bottom-0 block origin-bottom rounded-[1.25rem] ${className}`}
      style={{ height: `${Math.max(heightPct, 4)}%` }}
      initial={{ scaleY: 0 }}
      animate={{ scaleY: 1 }}
      // The back pill rises first and the front one follows it.
      transition={chartTransition(index + (front ? 1.5 : 0), 0.07)}
    />
  )
}

/** The dark card above the highlighted month, with its values written out. */
function PinnedValues({
  point,
  index,
  count,
  topPct,
}: {
  point: TrendPoint
  index: number
  count: number
  topPct: number
}) {
  // Anchored to the column's outer edge on its side of the plot, so the card always grows inwards and never past
  // the card's edge, at any width.
  const anchor =
    index < count / 2
      ? { left: `${(index / count) * 100}%` }
      : { right: `${(1 - (index + 1) / count) * 100}%` }
  return (
    <motion.div
      key={point.month}
      className="pointer-events-none absolute z-10 w-max max-w-full min-w-44 rounded-2xl bg-night p-3 text-xs text-on-night shadow-overlay"
      style={{ ...anchor, bottom: `calc(${(topPct / 100) * 11}rem + 0.75rem)` }}
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: duration.fast, ease }}
      aria-hidden
    >
      <p className="mb-2 inline-block rounded-pill bg-on-night px-2.5 py-0.5 font-medium text-night">
        {formatMonth(point.month)}
      </p>
      {point.hasData ? (
        <ul className="flex flex-col gap-1">
          {(
            [
              ['Income', point.income, 'bg-series-income'],
              ['Expenses', point.expenses, 'bg-series-expense'],
            ] as const
          ).map(([name, money, color]) => (
            <li key={name} className="flex items-center justify-between gap-4">
              <span className="flex items-center gap-2 text-on-night/80">
                <span className={`size-2 rounded-pill ${color}`} aria-hidden />
                {name}
              </span>
              <span className="figure font-semibold">{formatMoney(money)}</span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-on-night/80">No transactions recorded</p>
      )}
    </motion.div>
  )
}

export function CashFlowTable({ points }: { points: TrendPoint[] }) {
  return (
    <DataTable
      headers={['Month', 'Income', 'Expenses', 'Net']}
      rows={points.map((p) =>
        p.hasData
          ? [formatMonth(p.month), formatMoney(p.income), formatMoney(p.expenses), formatMoney(p.net)]
          : [formatMonth(p.month), 'No data', 'No data', '—'],
      )}
    />
  )
}
