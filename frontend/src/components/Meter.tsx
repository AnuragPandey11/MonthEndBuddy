import { AlertTriangle, CheckCircle2, CircleAlert } from 'lucide-react'
import { motion } from 'motion/react'
import { chartTransition } from '../lib/motion'

interface MeterProps {
  /** Percentage from the API; may exceed 100. */
  pct: number
  label: string
  /** Budgets turn amber at the first alert threshold and red past 100%. Goals stay accent until achieved. */
  kind: 'budget' | 'goal'
  warnAt?: number
  /** Where it sits in a list, so a column of meters fills one after another. */
  index?: number
}

/**
 * A progress meter. The fill carries severity and the track is a lighter step of the same hue; severity is also
 * written out with an icon, so it never depends on colour alone (dataviz status rule).
 */
export function Meter({ pct, label, kind, warnAt = 80, index = 0 }: MeterProps) {
  const width = Math.min(Math.max(pct, 0), 100)
  const state =
    kind === 'goal' ? (pct >= 100 ? 'done' : 'ok') : pct > 100 ? 'over' : pct >= warnAt ? 'warn' : 'ok'
  const fill = {
    ok: 'bg-accent',
    warn: 'bg-warning',
    over: 'bg-negative',
    done: 'bg-positive',
  }[state]
  const track = {
    ok: 'bg-accent/15',
    warn: 'bg-warning/15',
    over: 'bg-negative/15',
    done: 'bg-positive/15',
  }[state]
  return (
    <div>
      <div
        className={`h-2 overflow-hidden rounded-full ${track}`}
        role="meter"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(pct)}
        aria-valuetext={`${pct}%`}
      >
        {/* Grows from the left on load; a transform, so reduced motion shows it full at once. */}
        <motion.div
          className={`h-full origin-left rounded-full ${fill}`}
          style={{ width: `${width}%` }}
          initial={{ scaleX: 0 }}
          animate={{ scaleX: 1 }}
          transition={chartTransition(index, 0.08)}
        />
      </div>
      {state === 'over' && (
        <p className="mt-1 inline-flex items-center gap-1 text-xs text-negative">
          <CircleAlert className="size-3.5" aria-hidden /> Over budget
        </p>
      )}
      {state === 'warn' && (
        <p className="mt-1 inline-flex items-center gap-1 text-xs text-warning">
          <AlertTriangle className="size-3.5" aria-hidden /> Nearly used up
        </p>
      )}
      {state === 'done' && (
        <p className="mt-1 inline-flex items-center gap-1 text-xs text-positive">
          <CheckCircle2 className="size-3.5" aria-hidden /> Achieved
        </p>
      )}
    </div>
  )
}
