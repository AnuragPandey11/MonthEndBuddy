import { Link } from 'react-router'
import { Skeleton } from '../../components/States'
import { useAlerts, useBudgetStatus, useSummary } from '../../lib/api/finance'
import { todayIso } from '../../lib/dates'
import { currentMonth, formatMonth } from '../../lib/months'
import { formatMoney } from '../../lib/money'

/**
 * What the app can still say when the model is unavailable (architecture §10.2): a few fixed sentences built from
 * the analytics endpoints, no model involved.
 */
export function QuickInsights() {
  const month = currentMonth(todayIso())
  const summary = useSummary(month)
  const budgets = useBudgetStatus(month)
  const alerts = useAlerts(month)

  if (summary.isPending || budgets.isPending || alerts.isPending) {
    return <Skeleton className="h-20" />
  }
  const insights: string[] = []
  if (summary.data) {
    insights.push(
      summary.data.coverage.transactionCount === 0
        ? `No transactions recorded yet for ${formatMonth(month)}.`
        : `${formatMonth(month)} so far: ${formatMoney(summary.data.income)} in, ${formatMoney(summary.data.expenses)} out.`,
    )
  }
  const over = budgets.data?.budgets.filter((b) => b.overspent) ?? []
  if (over.length > 0) {
    insights.push(`Over budget: ${over.map((b) => b.categoryName).join(', ')}.`)
  } else if (budgets.data && budgets.data.budgets.length > 0) {
    insights.push('Every budget is within its limit.')
  }
  const open = alerts.data?.filter((a) => a.status === 'OPEN') ?? []
  if (open.length > 0) {
    insights.push(`${open.length} unusual ${open.length === 1 ? 'transaction needs' : 'items need'} a look.`)
  }

  return (
    <div className="flex flex-col gap-2">
      <p className="text-xs font-medium text-muted">Quick insights from your data</p>
      <ul className="ml-5 list-disc text-sm">
        {insights.map((i) => (
          <li key={i}>{i}</li>
        ))}
      </ul>
      <Link to="/" className="text-sm text-accent hover:underline">
        Open the dashboard
      </Link>
    </div>
  )
}
