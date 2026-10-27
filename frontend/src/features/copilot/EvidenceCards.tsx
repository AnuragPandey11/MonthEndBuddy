import type { ComponentType, ReactNode } from 'react'
import { Database } from 'lucide-react'
import { CategoryBars } from '../../components/charts/CategoryBars'
import { ProjectionChart } from '../../components/charts/ProjectionChart'
import { Meter } from '../../components/Meter'
import type {
  AffordabilityResult,
  CategoryBreakdown,
  MoneyDto,
  MonthlySummary,
  PeriodComparison,
  SavingsProjection,
} from '../../lib/api/types'
import { formatDate } from '../../lib/dates'
import { formatMonth } from '../../lib/months'
import { formatMoney, formatMoneyMagnitude } from '../../lib/money'
import type { Evidence } from './types'
import { Card as Panel } from '../../components/Card'

/*
 * Evidence cards (architecture §10.6): the verified figures of a copilot answer, drawn only from `evidence[].result`,
 * through a registry keyed by tool name. A result whose shape a card does not recognise, or a tool without a card,
 * gets the generic card, which lists the result's own values and nothing else.
 */

interface CardProps<T> {
  result: T
}

interface CardEntry<T> {
  title: string
  accepts: (result: unknown) => result is T
  Card: ComponentType<CardProps<T>>
}

const isObject = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

const isMoney = (value: unknown): value is MoneyDto =>
  isObject(value) && typeof value.amount === 'string' && typeof value.currency === 'string'

const has =
  <T,>(...keys: string[]) =>
  (result: unknown): result is T =>
    isObject(result) && keys.every((k) => k in result)

function entry<T>(title: string, accepts: (r: unknown) => r is T, Card: ComponentType<CardProps<T>>) {
  return { title, accepts, Card } as CardEntry<unknown>
}

interface ToolTransactionRow {
  id: string
  date: string
  type: 'INCOME' | 'EXPENSE'
  amount: MoneyDto
  category: string | null
  description: string
  merchant: string | null
}

interface TransactionRows {
  from: string
  to: string
  rows: ToolTransactionRow[]
  truncated: boolean
}

interface BudgetRow {
  budgetId: string
  categoryName: string | null
  limit: MoneyDto
  spent: MoneyDto
  remaining: MoneyDto
  pctUsed: number
  overspent: boolean
}

interface AlertRow {
  id: string
  ruleId: string
  severity: 'INFO' | 'WARNING'
  subject: string | null
  message: string
}

const REGISTRY: Record<string, CardEntry<unknown>> = {
  getMonthlySummary: entry<MonthlySummary>('Monthly summary', has('income', 'expenses', 'net'), SummaryCard),
  getExpensesByCategory: entry<CategoryBreakdown>(
    'Spending by category',
    has('total', 'categories'),
    CategoryCard,
  ),
  compareMonthlySpending: entry<PeriodComparison>(
    'Month comparison',
    has('totals', 'categories'),
    ComparisonCard,
  ),
  getTransactions: entry<TransactionRows>('Transactions', has('rows'), TransactionsCard),
  calculateSavingsProjection: entry<SavingsProjection>(
    'Savings projection',
    has('target', 'status'),
    ProjectionCard,
  ),
  simulatePurchase: entry<AffordabilityResult>('Purchase check', has('verdict'), AffordabilityCard),
  getBudgetStatus: entry<{ month: string; budgets: BudgetRow[] }>('Budgets', has('budgets'), BudgetCard),
  findUnusualTransactions: entry<AlertRow[]>(
    'Unusual spending',
    (r): r is AlertRow[] => Array.isArray(r),
    AlertsCard,
  ),
}

/** One card for one piece of evidence. {@code asOf} marks a snapshot from a reopened conversation. */
export function EvidenceCard({ evidence, asOf }: { evidence: Evidence; asOf?: string }) {
  const registered = REGISTRY[evidence.tool]
  const known = registered?.accepts(evidence.result) ? registered : undefined
  const title = known?.title ?? 'Data used'
  const Body = known?.Card ?? FallbackCard
  const p = evidence.provenance
  const footer = [
    p?.period && `Period ${p.period}`,
    p?.transactionCount != null && `${p.transactionCount} transaction${p.transactionCount === 1 ? '' : 's'}`,
    evidence.truncated && 'shortened to fit',
    asOf && `as of ${new Date(asOf).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}`,
  ].filter(Boolean)
  return (
    <Panel
      as="article"
      padding="compact"
      aria-label={title}

      data-tool={evidence.tool}
    >
      <h3 className="mb-2 flex items-center gap-1.5 text-xs font-medium text-muted">
        <Database className="size-3.5" aria-hidden /> {title}
      </h3>
      <Body result={evidence.result} />
      {footer.length > 0 && <p className="mt-2 text-caption text-faint">{footer.join(' · ')}</p>}
    </Panel>
  )
}

function Figures({ rows }: { rows: [string, ReactNode][] }) {
  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
      {rows.map(([label, value]) => (
        <div key={label} className="contents">
          <dt className="text-muted">{label}</dt>
          <dd className="figure text-right">{value}</dd>
        </div>
      ))}
    </dl>
  )
}

const money = (value: MoneyDto | null | undefined) => (value ? formatMoney(value) : '—')

function SummaryCard({ result: s }: CardProps<MonthlySummary>) {
  if (s.coverage?.transactionCount === 0) {
    return <p className="text-sm text-muted">No transactions recorded for {formatMonth(s.month)}.</p>
  }
  return (
    <Figures
      rows={[
        ['Income', money(s.income)],
        ['Spending', money(s.expenses)],
        ['Net', money(s.net)],
        ['Savings rate', s.savingsRate == null ? '—' : `${s.savingsRate}%`],
      ]}
    />
  )
}

function CategoryCard({ result: b }: CardProps<CategoryBreakdown>) {
  if (b.categories.length === 0) {
    return <p className="text-sm text-muted">No spending recorded in this period.</p>
  }
  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm">
        <span className="text-muted">Total </span>
        <span className="figure font-medium">{money(b.total)}</span>
      </p>
      <CategoryBars categories={b.categories} limit={5} />
    </div>
  )
}

function ComparisonCard({ result: c }: CardProps<PeriodComparison>) {
  return (
    <div className="flex flex-col gap-2">
      <Figures
        rows={[
          [formatMonth(c.current), money(c.totals.current)],
          [formatMonth(c.previous), money(c.totals.previous)],
          [
            'Change',
            c.totals.deltaPct == null
              ? money(c.totals.delta)
              : `${money(c.totals.delta)} (${c.totals.deltaPct}%)`,
          ],
        ]}
      />
      {c.categories.length > 0 && (
        <ul className="mt-1 flex flex-col gap-1 border-t border-line pt-2 text-xs">
          {c.categories.slice(0, 5).map((cat) => (
            <li key={cat.categoryId ?? cat.name} className="flex justify-between gap-3">
              <span className="truncate text-muted">{cat.name}</span>
              <span className="figure">
                {money(cat.previous)} → {money(cat.current)}
                {cat.isNew && <span className="ml-1 text-faint">new</span>}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function TransactionsCard({ result: t }: CardProps<TransactionRows>) {
  if (t.rows.length === 0) return <p className="text-sm text-muted">No matching transactions.</p>
  const shown = t.rows.slice(0, 8)
  return (
    <div>
      <ul className="flex flex-col gap-1 text-sm">
        {shown.map((r) => (
          <li key={r.id} className="flex justify-between gap-3">
            <span className="truncate">
              {r.merchant ?? r.description} <span className="text-xs text-faint">{formatDate(r.date)}</span>
            </span>
            <span className="figure shrink-0">{money(r.amount)}</span>
          </li>
        ))}
      </ul>
      {(t.rows.length > shown.length || t.truncated) && (
        <p className="mt-1 text-xs text-faint">
          {t.rows.length > shown.length && `${t.rows.length - shown.length} more read by the copilot. `}
          {t.truncated && 'More transactions match; see the Transactions page.'}
        </p>
      )}
    </div>
  )
}

const PROJECTION_STATUS: Record<SavingsProjection['status'], string> = {
  ON_TRACK: 'On track',
  BEHIND: 'Behind',
  ACHIEVED: 'Achieved',
  DEADLINE_PASSED: 'Deadline passed',
  NEVER: 'Never reaches the target',
}

function ProjectionCard({ result: p }: CardProps<SavingsProjection>) {
  return (
    <div className="flex flex-col gap-2">
      <Figures
        rows={[
          ['Target', `${money(p.target)} by ${formatDate(p.targetDate)}`],
          ['Saved', money(p.current)],
          ['Needed each month', money(p.requiredMonthly)],
          ['Status', PROJECTION_STATUS[p.status] ?? p.status],
          ...(p.projectedCompletion
            ? ([['Reached', formatMonth(p.projectedCompletion)]] as [string, ReactNode][])
            : []),
        ]}
      />
      {Array.isArray(p.series) && p.series.length > 1 && <ProjectionChart projection={p} />}
    </div>
  )
}

const VERDICT: Record<AffordabilityResult['verdict'], string> = {
  AFFORDABLE: 'Affordable',
  AFFORDABLE_WITH_TRADEOFFS: 'Affordable, with trade-offs',
  NOT_AFFORDABLE: 'Not affordable now',
  INSUFFICIENT_DATA: 'Not enough data to say',
}

function AffordabilityCard({ result: a }: CardProps<AffordabilityResult>) {
  return (
    <div className="flex flex-col gap-2">
      <p className="font-medium">{VERDICT[a.verdict] ?? a.verdict}</p>
      <Figures
        rows={[
          ['Purchase', money(a.purchaseAmount)],
          ['Set aside for goals', money(a.savingsSetAside)],
          ['Free savings after', money(a.savingsAfterPurchase)],
          ['Emergency buffer', money(a.emergencyBuffer)],
          ['Monthly surplus', money(a.monthlySurplus)],
        ]}
      />
      {a.goalImpacts?.length > 0 && (
        <ul className="border-t border-line pt-2 text-xs">
          {a.goalImpacts.map((g) => (
            <li key={g.goalId} className="flex justify-between gap-3">
              <span className="truncate text-muted">{g.name}</span>
              <span>{g.delayMonths == null ? 'cannot recover' : `delayed ${g.delayMonths} mo`}</span>
            </li>
          ))}
        </ul>
      )}
      {a.dataQuality?.map((q) => (
        <p key={q.code} className="text-xs text-warning">
          {q.message}
        </p>
      ))}
    </div>
  )
}

function BudgetCard({ result: r }: CardProps<{ month: string; budgets: BudgetRow[] }>) {
  if (r.budgets.length === 0) return <p className="text-sm text-muted">No budgets set.</p>
  return (
    <ul className="flex flex-col gap-3">
      {r.budgets.map((b) => (
        <li key={b.budgetId}>
          <div className="mb-1 flex justify-between gap-3 text-sm">
            <span className="truncate">{b.categoryName ?? 'Overall'}</span>
            <span className="figure">
              {money(b.spent)} <span className="text-muted">of {money(b.limit)}</span>
            </span>
          </div>
          <Meter pct={b.pctUsed} kind="budget" label={`${b.categoryName ?? 'Overall'}: ${b.pctUsed}% used`} />
          {b.overspent && <p className="text-xs text-muted">{formatMoneyMagnitude(b.remaining)} over</p>}
        </li>
      ))}
    </ul>
  )
}

function AlertsCard({ result: alerts }: CardProps<AlertRow[]>) {
  if (alerts.length === 0) return <p className="text-sm text-muted">Nothing unusual found.</p>
  return (
    <ul className="flex flex-col gap-1.5 text-sm">
      {alerts.map((a) => (
        <li key={a.id}>{a.message}</li>
      ))}
    </ul>
  )
}

/** Lists the result's own scalar and money values, so even an unknown tool shows only what it returned. */
function FallbackCard({ result }: CardProps<unknown>) {
  const rows: [string, ReactNode][] = isObject(result)
    ? Object.entries(result)
        .filter(([, v]) => isMoney(v) || ['string', 'number', 'boolean'].includes(typeof v))
        .slice(0, 12)
        .map(([k, v]) => [k, isMoney(v) ? formatMoney(v) : String(v)])
    : []
  if (rows.length === 0) {
    return <p className="text-sm text-muted">The copilot used data from this tool.</p>
  }
  return <Figures rows={rows} />
}
