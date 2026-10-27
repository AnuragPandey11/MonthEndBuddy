import { Check, LoaderCircle, X } from 'lucide-react'
import { motion } from 'motion/react'
import { enter } from '../../lib/motion'
import type { ChatPhase, ToolStep } from './useAgentChat'

const HEADLINE: Partial<Record<ChatPhase['kind'], string>> = {
  thinking: 'Reading your question…',
  answering: 'Working out the answer…',
}

/** What the copilot is doing right now, with each data lookup it has made this turn (architecture §14). */
export function ToolStatus({ phase, steps }: { phase: ChatPhase; steps: ToolStep[] }) {
  const headline = phase.kind === 'tool_running' ? `${phase.label}…` : (HEADLINE[phase.kind] ?? null)
  return (
    <div role="status" className="flex flex-col gap-2 rounded-card border border-line bg-surface p-4">
      {/* Keyed by its text, so each new phase fades in rather than swapping abruptly. */}
      {headline && (
        <motion.p
          key={headline}
          initial={{ opacity: 0, y: 4 }}
          animate={{ opacity: 1, y: 0 }}
          transition={enter}
          className="flex items-center gap-2 text-sm text-muted"
        >
          <LoaderCircle className="size-4 animate-spin motion-reduce:animate-none" aria-hidden />
          {headline}
        </motion.p>
      )}
      {steps.length > 0 && (
        <ol className="flex flex-col gap-1" aria-label="Data checked">
          {steps.map((step) => (
            <motion.li
              key={step.callId}
              initial={{ opacity: 0, x: -6 }}
              animate={{ opacity: 1, x: 0 }}
              transition={enter}
              className="flex items-center gap-2 text-xs text-faint"
            >
              <StepIcon ok={step.ok} />
              <span>{step.label}</span>
              {step.ok === false && <span className="sr-only">(failed)</span>}
            </motion.li>
          ))}
        </ol>
      )}
    </div>
  )
}

function StepIcon({ ok }: { ok: boolean | null }) {
  if (ok === null)
    return <LoaderCircle className="size-3.5 animate-spin motion-reduce:animate-none" aria-hidden />
  if (ok) return <Check className="size-3.5 text-positive" aria-hidden />
  return <X className="size-3.5 text-negative" aria-hidden />
}
