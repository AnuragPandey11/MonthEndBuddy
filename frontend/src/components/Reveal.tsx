import type { ReactNode } from 'react'
import { motion } from 'motion/react'
import { revealContainer, revealItem } from '../lib/motion'

/** A grid whose `Reveal` children rise in one after another when it mounts. */
export function RevealGrid({ className = '', children }: { className?: string; children: ReactNode }) {
  return (
    <motion.div className={className} variants={revealContainer} initial="hidden" animate="shown">
      {children}
    </motion.div>
  )
}

/** One cell of a `RevealGrid`; it carries the cell's grid placement, so the card inside stays a plain `Card`. */
export function Reveal({ className = '', children }: { className?: string; children: ReactNode }) {
  return (
    <motion.div className={`min-w-0 ${className}`} variants={revealItem}>
      {children}
    </motion.div>
  )
}
