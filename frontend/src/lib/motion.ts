import type { Transition, Variants } from 'motion/react'

/**
 * Motion tokens (P4.3, architecture §14). Everything moves on one soft ease-out curve, quickly enough that a page is
 * usable before it settles. `<MotionConfig reducedMotion="user">` in main.tsx turns transforms off for people who
 * ask for less motion; animations of other properties check `useReducedMotion()` themselves. Figures are never
 * animated: containers move, numbers arrive fully formed.
 */
export const ease = [0.22, 1, 0.36, 1] as const

export const duration = { fast: 0.18, base: 0.32, slow: 0.6, chart: 0.9 } as const

export const stagger = 0.06

export const enter: Transition = { duration: duration.base, ease }

/** A grid of cards: each child rises in turn. Pair with `revealItem` on the children. */
export const revealContainer: Variants = {
  hidden: {},
  shown: { transition: { staggerChildren: stagger, delayChildren: 0.04 } },
}

export const revealItem: Variants = {
  hidden: { opacity: 0, y: 14, scale: 0.985 },
  shown: { opacity: 1, y: 0, scale: 1, transition: { duration: duration.slow, ease } },
}

/** A chart mark growing in: the n-th mark starts a little after the one before it. */
export function chartTransition(index = 0, step = 0.05): Transition {
  return { duration: duration.chart, ease, delay: 0.15 + index * step }
}
