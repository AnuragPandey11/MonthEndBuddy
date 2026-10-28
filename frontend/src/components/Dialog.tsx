import { useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'
import { motion } from 'motion/react'
import { enter } from '../lib/motion'

interface DialogProps {
  title: string
  open: boolean
  onClose: () => void
  children: ReactNode
}

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

/** Keeps Tab and Shift+Tab inside the open dialog, wrapping at either end. */
function trapFocus(event: KeyboardEvent, panel: HTMLElement | null) {
  const focusable = [...(panel?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? [])]
  const first = focusable[0]
  const last = focusable.at(-1)
  if (!first || !last) return
  const active = document.activeElement
  const inside = panel?.contains(active) ?? false
  if (event.shiftKey && (active === first || !inside)) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && (active === last || !inside)) {
    event.preventDefault()
    first.focus()
  }
}

/**
 * Modal dialog: Escape and the backdrop close it; focus moves in on open, stays inside while open (Tab and
 * Shift+Tab wrap around), and returns on close.
 */
export function Dialog({ title, open, onClose, children }: DialogProps) {
  const panel = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const previous = document.activeElement as HTMLElement | null
    const first = panel.current?.querySelector<HTMLElement>(
      'input, select, textarea, button:not([data-close])',
    )
    first?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
      if (event.key === 'Tab') trapFocus(event, panel.current)
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      previous?.focus()
    }
  }, [open, onClose])

  if (!open) return null
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center">
      <motion.div
        className="absolute inset-0 bg-scrim backdrop-blur-[2px]"
        onClick={onClose}
        aria-hidden
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={enter}
      />
      <motion.div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
        initial={{ opacity: 0, y: 24, scale: 0.98 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={enter}
        className="relative max-h-[92vh] w-full overflow-y-auto rounded-t-card border border-line bg-surface p-6 shadow-overlay sm:max-w-lg sm:rounded-card"
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 id="dialog-title" className="text-base font-semibold">
            {title}
          </h2>
          <button
            type="button"
            data-close
            onClick={onClose}
            className="rounded-md p-1 text-muted hover:bg-elevated hover:text-ink"
            aria-label="Close"
          >
            <X className="size-4" aria-hidden />
          </button>
        </div>
        {children}
      </motion.div>
    </div>,
    document.body,
  )
}
