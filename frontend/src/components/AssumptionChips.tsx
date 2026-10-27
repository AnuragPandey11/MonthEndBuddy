import type { Assumption, AssumptionLabel } from '../lib/api/types'

/** Architecture §14: every assumption is shown with where it came from, so an estimate never reads as a fact. */
const LABELS: Record<AssumptionLabel, string> = {
  DERIVED: 'From your data',
  USER_STATED: 'You said',
  USER_PROFILE: 'From your profile',
  DEFAULT_ASSUMPTION: 'Default',
}

const NAMES: Record<string, string> = {
  currentSavings: 'Current savings',
  goalSaved: 'Saved toward this goal',
  savingsSetAside: 'Set aside for goals',
  monthlyContribution: 'Monthly contribution',
  return: 'Return',
  contributions: 'Contributions',
  averageMonthlyIncome: 'Average income',
  averageMonthlyExpenses: 'Average spending',
  emergencyBuffer: 'Emergency buffer',
  goalNeed: 'Goal contributions',
  goalDelay: 'Goals',
}

export function AssumptionChips({ assumptions }: { assumptions: Assumption[] }) {
  return (
    <ul className="flex flex-col gap-1.5" aria-label="Assumptions">
      {assumptions.map((a) => (
        <li key={`${a.name}:${a.value}`} className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5 text-xs">
          <span className="rounded-full border border-line px-2 py-0.5 text-faint">{LABELS[a.label]}</span>
          <span className="text-muted">{NAMES[a.name] ?? a.name}:</span>
          <span className="text-ink">{a.value}</span>
        </li>
      ))}
    </ul>
  )
}
