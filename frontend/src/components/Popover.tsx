import { useCallback, useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { useLocation } from 'react-router'
import { motion } from 'motion/react'
import { enter } from '../lib/motion'

interface PopoverProps {
  /** The trigger's accessible name, which also names the panel. */
  label: string
  /** What the trigger shows (an icon, an avatar). */
  trigger: ReactNode
  triggerClassName?: string
  children: ReactNode
}

/**
 * A button that shows a panel below it (a disclosure, not a modal). Escape closes it and returns focus to the
 * trigger; so do a click outside, focus leaving it, and navigating, since it lives in the app shell, which stays
 * mounted across pages.
 * <p>
 * On phones the panel anchors to the nearest positioned ancestor rather than its button, so a row of triggers in a
 * top bar should be `relative`: every panel then lines up with the screen's gutter instead of running off its left
 * edge.
 */
export function Popover({ label, trigger, triggerClassName = '', children }: PopoverProps) {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)
  const panelId = useId()
  const { pathname, search } = useLocation()

  const close = useCallback((returnFocus = false) => {
    setOpen(false)
    if (returnFocus) button.current?.focus()
  }, [])

  // eslint-disable-next-line react-hooks/set-state-in-effect -- a navigation closes the panel
  useEffect(() => setOpen(false), [pathname, search])

  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close(true)
    }
    const onPointer = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) close()
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('pointerdown', onPointer)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('pointerdown', onPointer)
    }
  }, [open, close])

  return (
    <div
      ref={root}
      className="relative max-sm:static"
      onBlur={(event) => {
        if (open && event.relatedTarget && !root.current?.contains(event.relatedTarget as Node)) close()
      }}
    >
      <button
        ref={button}
        type="button"
        aria-label={label}
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((v) => !v)}
        className={triggerClassName}
      >
        {trigger}
      </button>
      {open && (
        // Opens with a short drop-in; closing is instant, so focus and the outside click never wait on an animation.
        <motion.div
          id={panelId}
          role="dialog"
          aria-label={label}
          initial={{ opacity: 0, y: -6, scale: 0.97 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={enter}
          className="absolute top-full right-0 z-[45] mt-2 w-[min(22rem,calc(100vw-2rem))] origin-top-right rounded-[1.25rem] border border-line bg-surface p-2 shadow-overlay"
        >
          {children}
        </motion.div>
      )}
    </div>
  )
}
