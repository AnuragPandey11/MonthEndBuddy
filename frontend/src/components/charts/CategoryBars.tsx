import { motion } from 'motion/react'
import { chartTransition } from '../../lib/motion'
import type { CategoryShare } from '../../lib/api/types'
import { formatMoney } from '../../lib/money'

/**
 * Spending by category as a single-series bar list: one hue for every bar (never a value ramp on nominal
 * categories), value at the tip, the share from the API. The longest bar spans the track; widths are relative only
 * for drawing.
 */
export function CategoryBars({ categories, limit = 6 }: { categories: CategoryShare[]; limit?: number }) {
  const shown = categories.slice(0, limit)
  const max = Math.max(...shown.map((c) => Number(c.amount.amount)), 0)
  return (
    <ul className="flex flex-col gap-3">
      {shown.map((c, i) => (
        <li key={c.categoryId ?? 'uncategorised'}>
          <div className="mb-1 flex items-baseline justify-between gap-3 text-sm">
            <span className="truncate">{c.name}</span>
            <span className="shrink-0">
              <span className="figure font-medium">{formatMoney(c.amount)}</span>
              <span className="figure ml-2 text-xs text-faint">{c.pct}%</span>
            </span>
          </div>
          <div className="h-2 rounded-full bg-elevated">
            <motion.div
              className="h-full origin-left rounded-full bg-series-income"
              style={{ width: `${max > 0 ? (Number(c.amount.amount) / max) * 100 : 0}%` }}
              initial={{ scaleX: 0 }}
              animate={{ scaleX: 1 }}
              transition={chartTransition(i, 0.07)}
              aria-hidden
            />
          </div>
        </li>
      ))}
      {categories.length > limit && (
        <li className="text-xs text-faint">+ {categories.length - limit} more categories (see the table)</li>
      )}
    </ul>
  )
}
