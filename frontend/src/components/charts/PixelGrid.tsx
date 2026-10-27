import { motion } from 'motion/react'
import type { TrendPoint } from '../../lib/api/types'
import { chartTransition } from '../../lib/motion'
import { plotValue } from '../../lib/money'

const ROWS = 6

/**
 * The hero's pixel chart (decoration drawn from real data; the cash-flow card carries the values): one column per
 * month, as tall as that month's income relative to the window's highest. Each column is income (the income
 * colour), of which the expense-coloured cells at its foot were spent; pale cells are headroom. Cells pop in column by
 * column.
 */
export function PixelGrid({ points }: { points: TrendPoint[] }) {
  const maxIncome = Math.max(...points.map((p) => plotValue(p.income)), 0)
  return (
    <div
      className="grid grid-flow-col gap-1"
      style={{ gridTemplateRows: `repeat(${ROWS}, 1fr)` }}
      aria-hidden
    >
      {points.map((p, col) => {
        const income = plotValue(p.income)
        const filled = maxIncome > 0 ? Math.round((income / maxIncome) * ROWS) : 0
        const spent =
          maxIncome > 0 ? Math.round((Math.min(plotValue(p.expenses), income) / maxIncome) * ROWS) : 0
        // Rows are listed top to bottom; row ROWS-1 is the baseline.
        return Array.from({ length: ROWS }, (_, row) => {
          const fromBottom = ROWS - 1 - row
          const tone = !p.hasData
            ? 'bg-elevated'
            : fromBottom < spent
              ? 'bg-series-expense'
              : fromBottom < filled
                ? 'bg-series-income'
                : fromBottom < filled + 1
                  ? 'bg-series-income/15'
                  : 'bg-transparent'
          return (
            <motion.span
              key={`${p.month}-${row}`}
              className={`size-5 rounded-[5px] ${tone}`}
              initial={{ opacity: 0, scale: 0.4 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={chartTransition(col * ROWS + fromBottom, 0.018)}
            />
          )
        })
      })}
    </div>
  )
}
