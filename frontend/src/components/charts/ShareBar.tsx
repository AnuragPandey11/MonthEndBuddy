import { motion } from 'motion/react'
import type { CategoryShare } from '../../lib/api/types'
import { chartTransition } from '../../lib/motion'
import { formatMoney } from '../../lib/money'

/**
 * Fixed order, never cycled: the three largest categories take these by rank in the API's list. Neutral steps, so
 * they never read as the income and expense series used elsewhere on the page.
 */
const TONES = ['bg-ink', 'bg-faint', 'bg-line-strong'] as const

/**
 * The top categories as one segmented bar, each segment as wide as its share of spending (the API's `pct`); the
 * rest of the track is everything else. The largest is called out above the bar, and the legend names every segment
 * with its share, so identity never rests on colour alone. Segments sweep in from the left one after another.
 */
export function ShareBar({ categories }: { categories: CategoryShare[] }) {
  const top = categories.slice(0, 3)
  const lead = top[0]
  if (!lead) return null
  return (
    <div>
      <div className="relative mb-2 h-8">
        <motion.span
          className="absolute bottom-0 left-0 inline-flex items-center rounded-pill bg-ink px-2.5 py-1 text-xs font-semibold whitespace-nowrap text-surface"
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          transition={chartTransition(4)}
        >
          {lead.name} · {formatMoney(lead.amount)}
        </motion.span>
      </div>
      <div className="flex h-2.5 gap-0.5 overflow-hidden rounded-pill bg-elevated" aria-hidden>
        {top.map((c, i) => (
          <motion.span
            key={c.categoryId ?? c.name}
            className={`h-full origin-left first:rounded-l-pill ${TONES[i]}`}
            style={{ width: `${c.pct}%` }}
            initial={{ scaleX: 0 }}
            animate={{ scaleX: 1 }}
            transition={chartTransition(i, 0.18)}
          />
        ))}
      </div>
      <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted" aria-label="Top categories">
        {top.map((c, i) => (
          <li key={c.categoryId ?? c.name} className="flex items-center gap-1.5">
            <span className={`size-2 rounded-pill ${TONES[i]}`} aria-hidden />
            {c.name} {c.pct}%
          </li>
        ))}
      </ul>
    </div>
  )
}
