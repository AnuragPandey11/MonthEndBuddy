import { useDeferredValue, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import {
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  BarChart3,
  FileUp,
  History,
  PieChart,
  Plus,
  Search,
  ShieldAlert,
  SlidersHorizontal,
  Sparkles,
  Target,
  Upload,
  Wallet,
} from 'lucide-react'
import { buttonClass } from '../../components/buttonClass'
import { Card, CardHeader, Chip, IconChip } from '../../components/Card'
import { ChartCard, DataTable } from '../../components/ChartCard'
import { CashFlowChart, CashFlowTable } from '../../components/charts/CashFlowChart'
import { CategoryBars } from '../../components/charts/CategoryBars'
import { GoalGauge } from '../../components/charts/GoalGauge'
import { PixelGrid } from '../../components/charts/PixelGrid'
import { ShareBar } from '../../components/charts/ShareBar'
import { Figure } from '../../components/Figure'
import { Meter } from '../../components/Meter'
import { MonthPicker } from '../../components/MonthPicker'
import { PageHeader } from '../../components/PageHeader'
import { Reveal, RevealGrid } from '../../components/Reveal'
import { EmptyState, ErrorState, Skeleton } from '../../components/States'
import {
  useAlerts,
  useBudgetStatus,
  useCategoryBreakdown,
  useComparison,
  useGoals,
  useSummary,
  useTrends,
} from '../../lib/api/finance'
import type { Change } from '../../lib/api/types'
import { formatDate, formatLongToday, greeting, todayIso } from '../../lib/dates'
import { addMonths, currentMonth, formatMonth, formatMonthShort } from '../../lib/months'
import { formatMoney } from '../../lib/money'
import { useAuth } from '../auth/useAuth'
import { useTransactions } from '../transactions/api'
import { TransactionList } from '../transactions/TransactionList'
import { AlertList } from '../alerts/AlertList'
import { MonthReviewCard } from './MonthReviewCard'

function endOfMonth(month: string): string {
  const [y, m] = month.split('-').map(Number)
  const day = new Date(Date.UTC(y ?? 1970, m ?? 1, 0)).getUTCDate()
  return `${month}-${String(day).padStart(2, '0')}`
}

/** Expense change vs last month, as computed by the API; more spending is shown as the bad direction. */
function expenseDelta(change: Change | undefined, previous: string) {
  if (!change) return undefined
  if (change.isNew)
    return { text: `New this month (nothing in ${formatMonth(previous)})`, tone: 'neutral' as const }
  if (change.deltaPct === null) return undefined
  const up = change.deltaPct > 0
  return {
    text: `${up ? '+' : ''}${change.deltaPct}% vs ${formatMonth(previous)}`,
    tone: change.deltaPct === 0 ? ('neutral' as const) : up ? ('bad' as const) : ('good' as const),
  }
}

const toneClass = {
  good: 'bg-positive/12 text-positive',
  bad: 'bg-negative/10 text-negative',
  neutral: 'bg-elevated text-muted',
}

const roundLink =
  'inline-flex size-9 shrink-0 items-center justify-center rounded-pill border border-line bg-surface text-ink transition-colors hover:bg-elevated'

/**
 * The overview (spec §4.3, §7), laid out as a bento grid whose cards rise in one after another. Every figure comes
 * from an API response and is only formatted here. One month filter scopes the summary, categories, budgets and
 * alerts; the trend always shows the last six months.
 */
export function DashboardPage() {
  const { me } = useAuth()
  const maxMonth = currentMonth(todayIso())
  const [month, setMonth] = useState(maxMonth)
  const previous = addMonths(month, -1)
  const monthChip = `${formatMonthShort(month)} ${month.slice(0, 4)}`

  const summary = useSummary(month)
  const compare = useComparison(month, previous)
  const categories = useCategoryBreakdown(month, `${month}-01`, endOfMonth(month))
  const trends = useTrends(6)
  const budgets = useBudgetStatus(month)
  const goals = useGoals()
  const alerts = useAlerts(month)
  const [query, setQuery] = useState('')
  const recent = useTransactions({ q: useDeferredValue(query.trim()) || undefined })

  return (
    <section className="flex flex-col gap-section">
      <PageHeader
        title={`${greeting()}${me ? `, ${me.displayName}` : ''}`}
        description={formatLongToday()}
        actions={<MonthPicker value={month} max={maxMonth} onChange={setMonth} />}
      />

      {/* Only when last month ended in surplus and has not been reviewed yet. */}
      <MonthReviewCard />

      <RevealGrid className="grid grid-cols-1 gap-section md:grid-cols-2 xl:grid-cols-12">
        {/* Row 1: the month's headline, money in, money out. */}
        <Reveal className="md:col-span-2 xl:col-span-6">
          <HeroCard summary={summary} trends={trends.data?.points} />
        </Reveal>

        <Reveal className="xl:col-span-3">
          <Card className="flex h-full flex-col">
            <CardHeader title="Income" icon={<ArrowDownLeft />} action={<Chip>{monthChip}</Chip>} />
            {summary.isPending ? (
              <Skeleton className="h-28" />
            ) : summary.isError ? (
              <p className="text-sm text-muted">Unavailable right now.</p>
            ) : (
              <>
                <Figure text={formatMoney(summary.data.income)} className="text-figure" />
                <p className="mt-3 inline-flex w-fit rounded-pill border border-line px-3 py-1 text-xs text-muted">
                  {summary.data.coverage.transactionCount === 0
                    ? 'No transactions recorded this month.'
                    : `From ${summary.data.coverage.transactionCount} transactions`}
                </p>
                <div className="mt-auto grid grid-cols-2 gap-3 pt-5">
                  <SubFigure
                    label="Spent"
                    value={formatMoney(summary.data.expenses)}
                    tone="border-series-expense"
                  />
                  <SubFigure label="Kept" value={formatMoney(summary.data.net)} tone="border-positive" />
                </div>
              </>
            )}
          </Card>
        </Reveal>

        <Reveal className="xl:col-span-3">
          <Card className="flex h-full flex-col gap-4">
            <div className="rounded-[1.125rem] bg-elevated p-4">
              <CardHeader
                title="Expenses"
                icon={<ArrowUpRight />}
                action={<Chip className="bg-surface">{monthChip}</Chip>}
              />
              {summary.isPending ? (
                <Skeleton className="h-16" />
              ) : summary.isError ? (
                <p className="text-sm text-muted">Unavailable right now.</p>
              ) : (
                <>
                  <Figure text={formatMoney(summary.data.expenses)} className="text-figure" />
                  <DeltaPill delta={expenseDelta(compare.data?.totals, previous)} />
                </>
              )}
            </div>
            <div className="mt-auto">
              {categories.isPending ? (
                <Skeleton className="h-16" />
              ) : categories.isError || categories.data.categories.length === 0 ? (
                <p className="text-xs text-faint">No category split this month.</p>
              ) : (
                <ShareBar categories={categories.data.categories} />
              )}
            </div>
          </Card>
        </Reveal>

        {/* Row 2–3: goals and budgets down the left, the trend and shortcuts beside them. */}
        <Reveal className="xl:col-span-4 xl:row-span-2">
          <Card as="section" aria-labelledby="targets-title" className="flex h-full flex-col gap-3">
            <CardHeader
              title="Goals & budgets"
              titleId="targets-title"
              icon={<Target />}
              action={
                <>
                  <Link to="/goals" className={roundLink} aria-label="Plan a goal">
                    <Plus className="size-4" aria-hidden />
                  </Link>
                  <Link to="/budgets" className={roundLink} aria-label="Manage budgets">
                    <SlidersHorizontal className="size-4" aria-hidden />
                  </Link>
                </>
              }
            />
            <GoalPanel goals={goals} />
            <BudgetRows budgets={budgets} />
          </Card>
        </Reveal>

        <Reveal className="xl:col-span-4">
          <ChartCard
            title="Cash flow"
            subtitle="Income and expenses, last six months"
            icon={<BarChart3 />}
            className="h-full"
            table={trends.data && <CashFlowTable points={trends.data.points} />}
          >
            {trends.isPending ? (
              <Skeleton className="h-72" />
            ) : trends.isError ? (
              <ErrorState message="We couldn't load the trend." onRetry={() => void trends.refetch()} />
            ) : (
              <CashFlowChart points={trends.data.points} />
            )}
          </ChartCard>
        </Reveal>

        <Reveal className="md:col-span-2 xl:col-span-4">
          <div className="grid h-full gap-section sm:grid-cols-2">
            <CopilotPromo />
            <Card className="flex flex-col gap-3">
              <Chip className="w-fit">This month</Chip>
              <p className="flex items-center gap-2">
                <span className="text-figure">{alerts.data ? alerts.data.length : '·'}</span>
                <span className="inline-flex items-center gap-1 rounded-pill bg-negative/10 px-2 py-0.5 text-xs font-medium text-negative">
                  <ShieldAlert className="size-3.5" aria-hidden /> to review
                </span>
              </p>
              <div className="mt-auto flex items-end justify-between gap-2">
                <p className="text-sm font-medium">Unusual spending</p>
                <a href="#insights" className={roundLink} aria-label="See unusual spending">
                  <ArrowRight className="size-4" aria-hidden />
                </a>
              </div>
            </Card>
            <Card className="flex flex-col gap-3">
              <div className="flex items-start justify-between">
                <IconChip>
                  <FileUp />
                </IconChip>
                <Link to="/import" className={roundLink} aria-label="Import a statement">
                  <ArrowUpRight className="size-4" aria-hidden />
                </Link>
              </div>
              <div className="mt-auto">
                <Chip className="mb-2">CSV statements</Chip>
                <p className="text-heading">Import statement</p>
              </div>
            </Card>
          </div>
        </Reveal>

        <Reveal className="md:col-span-2 xl:col-span-8">
          <ChartCard
            title="Recent transactions"
            icon={<History />}
            className="h-full"
            action={
              <>
                <label className="relative hidden sm:block">
                  <span className="sr-only">Search transactions</span>
                  <Search
                    className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-faint"
                    aria-hidden
                  />
                  <input
                    type="search"
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Search"
                    className="w-56 rounded-pill border border-line bg-surface py-2 pr-3 pl-9 text-sm placeholder:text-faint focus:border-accent focus:outline-none"
                  />
                </label>
                <Link to="/transactions" className={roundLink} aria-label="All transactions">
                  <ArrowRight className="size-4" aria-hidden />
                </Link>
              </>
            }
          >
            {recent.isPending ? (
              <Skeleton className="h-40" />
            ) : recent.isError ? (
              <ErrorState message="We couldn't load transactions." onRetry={() => void recent.refetch()} />
            ) : (
              <TransactionList transactions={(recent.data.pages[0]?.items ?? []).slice(0, 5)} />
            )}
          </ChartCard>
        </Reveal>

        {/* Row 4: where the money went, and what looks unusual. */}
        <Reveal className="xl:col-span-6">
          <ChartCard
            title="Spending by category"
            subtitle={formatMonth(month)}
            icon={<PieChart />}
            className="h-full"
            table={
              categories.data && (
                <DataTable
                  headers={['Category', 'Amount', 'Share', 'Count']}
                  rows={categories.data.categories.map((c) => [
                    c.name,
                    formatMoney(c.amount),
                    `${c.pct}%`,
                    String(c.count),
                  ])}
                />
              )
            }
          >
            {categories.isPending ? (
              <Skeleton className="h-52" />
            ) : categories.isError ? (
              <ErrorState message="We couldn't load categories." onRetry={() => void categories.refetch()} />
            ) : categories.data.categories.length === 0 ? (
              <EmptyState title="No spending recorded" body="There are no expenses in this month." />
            ) : (
              <CategoryBars categories={categories.data.categories} />
            )}
          </ChartCard>
        </Reveal>

        <Reveal className="xl:col-span-6">
          <div id="insights" className="h-full scroll-mt-24">
            <ChartCard
              title="Insights"
              subtitle={`Unusual spending · ${formatMonth(month)}`}
              icon={<ShieldAlert />}
              className="h-full"
            >
              {alerts.isPending ? (
                <Skeleton className="h-32" />
              ) : alerts.isError ? (
                <ErrorState message="We couldn't load insights." onRetry={() => void alerts.refetch()} />
              ) : alerts.data.length === 0 ? (
                <p className="text-sm text-muted">Nothing unusual this month.</p>
              ) : (
                <AlertList alerts={alerts.data} />
              )}
            </ChartCard>
          </div>
        </Reveal>
      </RevealGrid>
    </section>
  )
}

type SummaryQuery = ReturnType<typeof useSummary>

/** Net cash flow, the one number the month leads with, and the three things people come to do. */
function HeroCard({
  summary,
  trends,
}: {
  summary: SummaryQuery
  trends: Parameters<typeof PixelGrid>[0]['points'] | undefined
}) {
  return (
    <Card padding="roomy" className="flex h-full flex-col gap-5">
      <CardHeader title="Net cash flow" icon={<Wallet />} action={<Chip>₹ INR</Chip>} />
      {summary.isPending ? (
        <Skeleton className="h-24 w-72" />
      ) : summary.isError ? (
        <ErrorState message="We couldn't load this month's summary." onRetry={() => void summary.refetch()} />
      ) : (
        <div className="flex flex-1 flex-wrap items-center justify-between gap-6">
          <div className="min-w-0">
            <p className={`text-display ${summary.data.net.amount.startsWith('-') ? 'text-negative' : ''}`}>
              {formatMoney(summary.data.net)}
            </p>
            <p className="mt-3 flex flex-wrap items-center gap-2 text-sm text-muted">
              <span
                className={`inline-flex items-center gap-1 rounded-pill px-2 py-0.5 text-xs font-semibold ${
                  summary.data.savingsRate === null
                    ? 'bg-elevated text-muted'
                    : 'bg-positive/12 text-positive'
                }`}
              >
                <ArrowUpRight className="size-3.5" aria-hidden />
                <span>{summary.data.savingsRate === null ? '—' : `${summary.data.savingsRate}%`}</span>
              </span>
              {summary.data.savingsRate === null ? 'No income this month' : 'of income kept (net ÷ income)'}
            </p>
          </div>
          {trends && trends.length > 0 && (
            <div className="hidden sm:block">
              <PixelGrid points={trends} />
            </div>
          )}
        </div>
      )}
      <div className="mt-auto grid gap-2 sm:grid-cols-3">
        <Link to="/transactions?new=1" className={buttonClass('primary', 'py-3')}>
          <Plus className="size-4" aria-hidden /> Add transaction
        </Link>
        <Link to="/import" className={buttonClass('dark', 'py-3')}>
          <Upload className="size-4" aria-hidden /> Import statement
        </Link>
        <Link to="/copilot" className={buttonClass('secondary', 'py-3 border-transparent')}>
          <Sparkles className="size-4" aria-hidden /> Ask copilot
        </Link>
      </div>
    </Card>
  )
}

function DeltaPill({ delta }: { delta: ReturnType<typeof expenseDelta> }) {
  if (!delta) return null
  return (
    <p
      className={`mt-3 inline-flex w-fit items-center rounded-pill px-2.5 py-1 text-xs font-medium ${toneClass[delta.tone]}`}
    >
      {delta.text}
    </p>
  )
}

function SubFigure({ label, value, tone }: { label: string; value: string; tone: string }) {
  return (
    <div className={`border-l-[3px] pl-3 ${tone}`}>
      <p className="text-xs text-faint">{label}</p>
      <p className="figure truncate text-base font-semibold">{value}</p>
    </div>
  )
}

/** The highest-priority goal on the dark panel: its dial, what is saved and the target, from the API. */
function GoalPanel({ goals }: { goals: ReturnType<typeof useGoals> }) {
  const goal = goals.data?.find((g) => g.status === 'ACTIVE') ?? goals.data?.[0]
  return (
    <div className="relative rounded-[1.25rem] bg-night p-4 text-on-night">
      {goals.isPending ? (
        <Skeleton className="h-48 bg-on-night/10" />
      ) : goals.isError ? (
        <p className="py-10 text-center text-sm text-on-night/70">We couldn't load goals.</p>
      ) : !goal ? (
        <div className="flex flex-col items-center gap-2 py-10 text-center">
          <p className="font-medium">No goals yet</p>
          <Link to="/goals" className="text-sm text-lime underline-offset-4 hover:underline">
            Set a goal
          </Link>
        </div>
      ) : (
        <>
          <div className="flex items-start justify-between gap-2">
            <p className="truncate text-sm font-medium">{goal.name}</p>
            <p className="shrink-0 text-xs text-on-night/60">By {formatDate(goal.targetDate)}</p>
          </div>
          <div className="relative mx-auto mt-1 max-w-60">
            <GoalGauge pct={goal.progressPct} />
            <div className="absolute inset-x-0 top-[38%] text-center">
              <p className="text-xs text-on-night/60">Saved</p>
              <Figure
                text={formatMoney(goal.current)}
                className="block text-2xl font-semibold tracking-tight"
              />
              <p className="text-xs text-on-night/60">
                of {formatMoney(goal.target)} · {goal.progressPct}%
              </p>
            </div>
          </div>
          <Link
            to="/goals"
            className="absolute right-3 bottom-3 inline-flex size-9 items-center justify-center rounded-pill bg-lime text-on-lime transition-transform hover:scale-105 motion-reduce:hover:scale-100"
            aria-label="Open savings goals"
          >
            <Plus className="size-4" aria-hidden />
          </Link>
        </>
      )}
    </div>
  )
}

/** Each budget as a row: the share used, a meter that fills on load, and what was spent of the limit. */
function BudgetRows({ budgets }: { budgets: ReturnType<typeof useBudgetStatus> }): ReactNode {
  if (budgets.isPending) return <Skeleton className="h-32" />
  if (budgets.isError)
    return <ErrorState message="We couldn't load budgets." onRetry={() => void budgets.refetch()} />
  if (budgets.data.budgets.length === 0)
    return (
      <p className="rounded-[1.125rem] bg-elevated px-4 py-3 text-sm text-muted">
        No budgets yet.{' '}
        <Link to="/budgets" className="font-medium text-accent">
          Create one
        </Link>
      </p>
    )
  return (
    <ul className="flex flex-col gap-2">
      {budgets.data.budgets.map((b, i) => (
        <li key={b.budgetId} className="rounded-[1.125rem] border border-line px-4 py-3">
          <div className="mb-2 flex items-baseline justify-between gap-2">
            <span className="truncate text-sm font-medium">{b.categoryName}</span>
            <span className="figure shrink-0 text-xs font-semibold">{b.pctUsed}%</span>
          </div>
          <Meter
            pct={b.pctUsed}
            kind="budget"
            index={i}
            warnAt={b.alertThresholds[0] ?? 80}
            label={`${b.categoryName}: ${b.pctUsed}% of budget used`}
          />
          <p className="figure mt-1.5 text-xs text-faint">
            {formatMoney(b.spent)} of {formatMoney(b.limit)}
          </p>
        </li>
      ))}
    </ul>
  )
}

/** The reference's upsell card, given to the copilot: same numbers as this page, asked in words. */
function CopilotPromo() {
  return (
    <Card className="stripes relative flex flex-col gap-3 overflow-hidden sm:col-span-2">
      <div className="flex items-center justify-between gap-2">
        <span className="inline-flex items-center gap-1 rounded-pill bg-accent-strong px-2.5 py-1 text-xs font-medium text-white">
          <Sparkles className="size-3.5" aria-hidden /> AI Copilot
        </span>
        <div className="flex items-center gap-1.5">
          <Link to="/copilot" className={buttonClass('dark', 'px-3 py-1.5 text-xs')}>
            Open copilot
          </Link>
          <Link to="/copilot" className={roundLink} aria-label="Ask the copilot">
            <ArrowUpRight className="size-4" aria-hidden />
          </Link>
        </div>
      </div>
      <div>
        <p className="text-title">Ask anything about your money</p>
        <p className="mt-1 text-sm text-muted">Answers use the same figures as this dashboard.</p>
      </div>
    </Card>
  )
}
