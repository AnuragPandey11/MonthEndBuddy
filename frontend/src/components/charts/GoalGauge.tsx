import { motion, useReducedMotion } from 'motion/react'
import { chartTransition, duration, ease } from '../../lib/motion'

const R = 80
const START = 135
const SWEEP = 270

function point(angle: number) {
  const radians = (angle * Math.PI) / 180
  return { x: 100 + R * Math.cos(radians), y: 100 + R * Math.sin(radians) }
}

const from = point(START)
const to = point(START + SWEEP)
const ARC = `M ${from.x} ${from.y} A ${R} ${R} 0 1 1 ${to.x} ${to.y}`

/**
 * A three-quarter dial for a goal's progress, the API's `progressPct` (capped at 100 for drawing). The arc draws in
 * on load and the knob lands at its end; with reduced motion both appear in place. Decorative: the card writes the
 * figures out beside it.
 */
export function GoalGauge({ pct }: { pct: number }) {
  const reduce = useReducedMotion()
  const share = Math.min(Math.max(pct, 0), 100) / 100
  const knob = point(START + SWEEP * share)
  const draw = chartTransition(0)
  return (
    <svg viewBox="0 0 200 172" className="w-full" aria-hidden>
      <path
        d={ARC}
        fill="none"
        stroke="currentColor"
        strokeOpacity={0.12}
        strokeWidth={16}
        strokeLinecap="round"
      />
      {share > 0 && (
        <motion.path
          d={ARC}
          fill="none"
          stroke="var(--color-series-income)"
          strokeWidth={16}
          strokeLinecap="round"
          initial={reduce ? false : { pathLength: 0 }}
          animate={{ pathLength: share }}
          transition={{ ...draw, duration: duration.chart * 1.4 }}
        />
      )}
      <motion.circle
        cx={knob.x}
        cy={knob.y}
        r={9}
        fill="var(--color-surface)"
        stroke="var(--color-series-income)"
        strokeWidth={5}
        initial={reduce ? false : { opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: duration.base, ease, delay: (draw.delay ?? 0) + duration.chart * 1.2 }}
      />
    </svg>
  )
}
