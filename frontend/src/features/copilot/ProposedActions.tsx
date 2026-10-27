import { useState } from 'react'
import { Sparkles } from 'lucide-react'
import { Button } from '../../components/Button'
import { useBudgets } from '../../lib/api/finance'
import { BudgetDialog } from '../budgets/BudgetsPage'
import { GoalDialog } from '../goals/GoalsPage'
import type { ProposedAction } from './types'

/**
 * Changes the copilot suggests (D4). Each one opens the normal form, pre-filled; nothing changes until the user
 * saves it, and saving calls the same REST endpoint as the Budgets and Goals pages.
 */
export function ProposedActions({ actions }: { actions: ProposedAction[] }) {
  const [open, setOpen] = useState<ProposedAction | null>(null)
  const supported = actions.filter((a) => a.type !== 'UPDATE_GOAL_CONTRIBUTION')
  if (supported.length === 0) return null
  return (
    <div className="flex flex-wrap gap-2">
      {supported.map((action) => (
        <Button
          key={`${action.type}:${action.label}`}
          variant="secondary"
          className="px-3 py-1.5 text-xs"
          onClick={() => setOpen(action)}
        >
          <Sparkles className="size-3.5" aria-hidden /> {action.label}
        </Button>
      ))}
      {open && <ActionForm action={open} onClose={() => setOpen(null)} />}
    </div>
  )
}

const amountOf = (value: unknown) =>
  typeof value === 'object' && value !== null && 'amount' in value ? String(value.amount) : undefined

const text = (value: unknown) => (typeof value === 'string' ? value : undefined)

function ActionForm({ action, onClose }: { action: ProposedAction; onClose: () => void }) {
  const budgets = useBudgets()
  const p = action.payload
  switch (action.type) {
    case 'CREATE_BUDGET':
      return (
        <BudgetDialog
          open
          budget={null}
          draft={{ categoryId: text(p.categoryId), limit: amountOf(p.limit) }}
          onClose={onClose}
        />
      )
    case 'UPDATE_BUDGET': {
      const budget = budgets.data?.find((b) => b.id === p.budgetId)
      if (!budget) return null
      return <BudgetDialog open budget={budget} draft={{ limit: amountOf(p.limit) }} onClose={onClose} />
    }
    case 'CREATE_GOAL':
      return (
        <GoalDialog
          open
          goal={null}
          draft={{
            target: amountOf(p.targetAmount),
            current: amountOf(p.currentAmount),
            targetDate: text(p.targetDate),
          }}
          onClose={onClose}
        />
      )
    default:
      return null
  }
}
