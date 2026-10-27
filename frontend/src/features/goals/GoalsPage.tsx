import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation } from '@tanstack/react-query'
import { CircleAlert, Plus } from 'lucide-react'
import { AssumptionChips } from '../../components/AssumptionChips'
import { Button } from '../../components/Button'
import { ChartCard, DataTable } from '../../components/ChartCard'
import { ProjectionChart } from '../../components/charts/ProjectionChart'
import { Dialog } from '../../components/Dialog'
import { Field, Input } from '../../components/Field'
import { Meter } from '../../components/Meter'
import { EmptyState, ErrorState, Skeleton } from '../../components/States'
import { PageHeader } from '../../components/PageHeader'
import { api, ApiError } from '../../lib/api/client'
import {
  useGoalProjection,
  useGoals,
  useInvalidateFinance,
  usePurchaseSimulation,
} from '../../lib/api/finance'
import type { AffordabilityResult, Goal, ProjectionStatus } from '../../lib/api/types'
import { formatDate, todayIso } from '../../lib/dates'
import { formatMonth } from '../../lib/months'
import { formatMoney } from '../../lib/money'
import { Card } from '../../components/Card'

const AMOUNT = /^\d{1,15}(\.\d{1,2})?$/
const amount = (message: string) => z.string().trim().regex(AMOUNT, message)

/** Savings goals, what-if projections and the purchase check (architecture §9.3, §9.4). Nothing here is advice. */
export function GoalsPage() {
  const goals = useGoals()
  const [editing, setEditing] = useState<{ open: boolean; goal: Goal | null }>({ open: false, goal: null })
  const [selected, setSelected] = useState<string | null>(null)
  const selectedGoal = goals.data?.find((g) => g.id === selected) ?? goals.data?.[0] ?? null

  return (
    <section className="flex flex-col gap-section">
      <PageHeader
        title="Goals & plans"
        description="Projections assume a 0% return and the same contribution every month."
        actions={
          <Button onClick={() => setEditing({ open: true, goal: null })}>
            <Plus className="size-4" aria-hidden />
            Add goal
          </Button>
        }
      />

      {goals.isPending ? (
        <Skeleton className="h-32" />
      ) : goals.isError ? (
        <ErrorState message="We couldn't load your goals." onRetry={() => void goals.refetch()} />
      ) : goals.data.length === 0 ? (
        <EmptyState
          title="No goals yet"
          body="Saving for a laptop, a trip or an emergency fund? Add it to see what to put aside each month."
          action={<Button onClick={() => setEditing({ open: true, goal: null })}>Add goal</Button>}
        />
      ) : (
        <div className="grid gap-5 lg:grid-cols-5">
          <ul className="flex flex-col gap-3 lg:col-span-2" aria-label="Goals">
            {goals.data.map((g) => (
              <li key={g.id}>
                <div
                  className={`rounded-card border bg-surface p-4 ${selectedGoal?.id === g.id ? 'border-accent/60' : 'border-line'}`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <button type="button" className="text-left" onClick={() => setSelected(g.id)}>
                      <span className="block font-medium">{g.name}</span>
                      <span className="block text-xs text-faint">By {formatDate(g.targetDate)}</span>
                    </button>
                    <Button
                      variant="ghost"
                      className="px-2 py-1 text-xs"
                      onClick={() => setEditing({ open: true, goal: g })}
                    >
                      Edit
                    </Button>
                  </div>
                  <p className="figure mt-2 text-sm">
                    {formatMoney(g.current)} <span className="text-muted">of {formatMoney(g.target)}</span>
                  </p>
                  <div className="mt-2">
                    <Meter pct={g.progressPct} kind="goal" label={`${g.name}: ${g.progressPct}% saved`} />
                  </div>
                </div>
              </li>
            ))}
          </ul>
          <div className="lg:col-span-3">
            {selectedGoal && <ProjectionPanel key={selectedGoal.id} goal={selectedGoal} />}
          </div>
        </div>
      )}

      <PurchaseCheck />

      <GoalDialog
        open={editing.open}
        goal={editing.goal}
        onClose={() => setEditing({ open: false, goal: null })}
      />
    </section>
  )
}

const STATUS_TEXT: Record<ProjectionStatus, string> = {
  ON_TRACK: 'On track for the target date',
  BEHIND: 'Behind: this reaches the target after the target date',
  ACHIEVED: 'Already achieved',
  DEADLINE_PASSED: 'The target date has passed',
  NEVER: 'With nothing saved each month, this goal is never reached',
}

function ProjectionPanel({ goal }: { goal: Goal }) {
  const [contribution, setContribution] = useState('')
  const [submitted, setSubmitted] = useState('')
  const projection = useGoalProjection(goal.id, submitted)
  const valid = contribution === '' || AMOUNT.test(contribution.trim())
  const run = (value: string) => setSubmitted(value.trim())
  const p = projection.data

  return (
    <ChartCard
      title={`What if · ${goal.name}`}
      subtitle="Change the monthly amount to see when you'd get there"
      table={
        p && (
          <DataTable
            headers={['Month', 'Balance']}
            rows={p.series.map((s) => [formatMonth(s.month), formatMoney(s.balance)])}
          />
        )
      }
    >
      <form
        className="mb-4 flex flex-wrap items-end gap-2"
        onSubmit={(event) => {
          event.preventDefault()
          if (valid) run(contribution)
        }}
      >
        <div className="w-48">
          <Field label="Save each month (₹)" error={valid ? undefined : 'Use a number like 5000'}>
            {(a11y) => (
              <Input
                {...a11y}
                inputMode="decimal"
                className="figure"
                placeholder={p?.requiredMonthly?.amount ?? ''}
                value={contribution}
                onChange={(e) => setContribution(e.target.value)}
              />
            )}
          </Field>
        </div>
        <Button type="submit" variant="secondary" loading={projection.isFetching}>
          Project
        </Button>
      </form>
      {projection.isError ? (
        <ErrorState message="We couldn't project this goal." onRetry={() => void projection.refetch()} />
      ) : !p ? (
        <Skeleton className="h-52" />
      ) : (
        <div className={projection.isFetching ? 'opacity-60' : ''}>
          <div className="mb-3 grid grid-cols-2 gap-3 sm:grid-cols-3">
            <div>
              <p className="text-xs text-muted">Needed each month</p>
              <p className="figure font-semibold">
                {p.requiredMonthly ? formatMoney(p.requiredMonthly) : '—'}
              </p>
            </div>
            <div>
              <p className="text-xs text-muted">Reached in</p>
              <p className="font-semibold">
                {p.projectedCompletion ? formatMonth(p.projectedCompletion) : 'Never'}
              </p>
            </div>
            <div className="col-span-2 sm:col-span-1">
              <p className="text-xs text-muted">Status</p>
              <p className="text-sm">{STATUS_TEXT[p.status]}</p>
            </div>
          </div>
          {p.series.length > 0 && <ProjectionChart projection={p} />}
          <div className="mt-4">
            <AssumptionChips assumptions={p.assumptions} />
          </div>
        </div>
      )}
    </ChartCard>
  )
}

const VERDICT: Record<AffordabilityResult['verdict'], { title: string; tone: string }> = {
  AFFORDABLE: { title: 'Affordable', tone: 'text-positive' },
  AFFORDABLE_WITH_TRADEOFFS: { title: 'Affordable, with trade-offs', tone: 'text-warning' },
  NOT_AFFORDABLE: { title: 'Not affordable right now', tone: 'text-negative' },
  INSUFFICIENT_DATA: { title: 'Not enough information', tone: 'text-muted' },
}

function PurchaseCheck() {
  const simulate = usePurchaseSimulation()
  const schema = z.object({
    amount: amount('Enter an amount like 60000'),
    savings: z
      .string()
      .trim()
      .refine((v) => v === '' || AMOUNT.test(v), 'Use a number, or leave it blank'),
  })
  const { register, handleSubmit, formState } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { amount: '', savings: '' },
  })
  const r = simulate.data

  return (
    <Card as="section" aria-labelledby="purchase-check">
      <h2 id="purchase-check" className="text-heading">
        Can I afford it?
      </h2>
      <p className="mb-3 text-xs text-faint">
        Uses your average income and spending over the last three complete months, your savings and your
        goals. Money already saved toward a goal counts as set aside, not free to spend.
      </p>
      <form
        className="flex flex-wrap items-end gap-3"
        noValidate
        onSubmit={handleSubmit((v) =>
          simulate.mutate({
            purchaseAmount: { amount: v.amount, currency: 'INR' },
            currentSavingsOverride: v.savings ? { amount: v.savings, currency: 'INR' } : undefined,
          }),
        )}
      >
        <div className="w-44">
          <Field label="Purchase (₹)" error={formState.errors.amount?.message}>
            {(a11y) => (
              <Input
                {...a11y}
                inputMode="decimal"
                placeholder="60000"
                className="figure"
                {...register('amount')}
              />
            )}
          </Field>
        </div>
        <div className="w-56">
          <Field label="My total savings today (optional)" error={formState.errors.savings?.message}>
            {(a11y) => (
              <Input
                {...a11y}
                inputMode="decimal"
                placeholder="From settings"
                className="figure"
                {...register('savings')}
              />
            )}
          </Field>
        </div>
        <Button type="submit" loading={simulate.isPending}>
          Check
        </Button>
      </form>

      {simulate.isError && (
        <p role="alert" className="mt-3 text-sm text-negative">
          {simulate.error instanceof ApiError ? simulate.error.message : 'Something went wrong.'}
        </p>
      )}
      {r && (
        <div className="mt-4 grid gap-4 border-t border-line pt-4 md:grid-cols-2">
          <div>
            <p className={`text-lg font-semibold ${VERDICT[r.verdict].tone}`}>{VERDICT[r.verdict].title}</p>
            <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1.5 text-sm">
              {(
                [
                  ['Savings today', r.currentSavings],
                  ['Set aside for goals', r.savingsSetAside],
                  ['Free to spend', r.availableSavings],
                  ['Free savings after purchase', r.savingsAfterPurchase],
                  ['Emergency buffer', r.emergencyBuffer],
                  ['Monthly surplus', r.monthlySurplus],
                  ['Goal contributions / month', r.goalNeed],
                ] as const
              ).map(([label, money]) => (
                <div key={label} className="contents">
                  <dt className="text-muted">{label}</dt>
                  <dd className="figure text-right">{money ? formatMoney(money) : '—'}</dd>
                </div>
              ))}
              <dt className="text-muted">Earned back in</dt>
              <dd className="text-right">{r.recoveryMonths === null ? '—' : `${r.recoveryMonths} months`}</dd>
            </dl>
            {r.goalImpacts.map((g) => (
              <p key={g.goalId} className="mt-2 text-xs text-muted">
                {g.name}:{' '}
                {g.delayMonths === null
                  ? 'not counted'
                  : `about ${g.delayMonths} month${g.delayMonths === 1 ? '' : 's'} later`}
              </p>
            ))}
            {r.dataQuality.map((q) => (
              <p key={q.code + q.message} className="mt-2 inline-flex gap-1.5 text-xs text-warning">
                <CircleAlert className="mt-0.5 size-3.5 shrink-0" aria-hidden /> {q.message}
              </p>
            ))}
          </div>
          <div>
            <p className="mb-2 text-xs text-faint">What this is based on</p>
            <AssumptionChips assumptions={r.assumptions} />
          </div>
          <p className="text-xs text-faint md:col-span-2">
            A simulation from your own figures, not financial advice. Projections are not guaranteed.
          </p>
        </div>
      )}
    </Card>
  )
}

/** Values to pre-fill, e.g. from a copilot proposal. The user still names, reviews and saves the goal. */
export interface GoalDraft {
  target?: string
  current?: string
  targetDate?: string
}

export function GoalDialog({
  open,
  goal,
  draft,
  onClose,
}: {
  open: boolean
  goal: Goal | null
  draft?: GoalDraft
  onClose: () => void
}) {
  return (
    <Dialog title={goal ? `Edit ${goal.name}` : 'Add goal'} open={open} onClose={onClose}>
      <GoalForm key={goal?.id ?? 'new'} goal={goal} draft={draft} onClose={onClose} />
    </Dialog>
  )
}

function GoalForm({ goal, draft, onClose }: { goal: Goal | null; draft?: GoalDraft; onClose: () => void }) {
  const today = todayIso()
  const invalidate = useInvalidateFinance()
  const [formError, setFormError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const schema = z.object({
    name: z.string().trim().min(1, 'Name your goal').max(80),
    target: amount('Enter a target like 100000').refine(
      (v) => /[1-9]/.test(v),
      'Target must be more than zero',
    ),
    current: amount('Enter what you have saved, or 0'),
    targetDate: z.string().refine((v) => v > today, 'Pick a date in the future'),
  })
  type Values = z.infer<typeof schema>
  const { register, handleSubmit, formState } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: goal?.name ?? '',
      target: goal?.target.amount ?? draft?.target ?? '',
      current: goal?.current.amount ?? draft?.current ?? '0',
      targetDate: goal?.targetDate ?? draft?.targetDate ?? '',
    },
  })
  const save = useMutation({
    mutationFn: (v: Values) => {
      const body = {
        name: v.name,
        targetAmount: { amount: v.target, currency: 'INR' },
        currentAmount: { amount: v.current, currency: 'INR' },
        targetDate: v.targetDate,
      }
      return goal
        ? api<Goal>(`/api/v1/goals/${goal.id}`, { method: 'PUT', body: { ...body, version: goal.version } })
        : api<Goal>('/api/v1/goals', { method: 'POST', body })
    },
    onSuccess: async () => {
      await invalidate()
      onClose()
    },
    onError: (error) =>
      setFormError(error instanceof ApiError ? error.message : 'Could not save. Try again.'),
  })
  const remove = useMutation({
    mutationFn: () => api<void>(`/api/v1/goals/${goal?.id}`, { method: 'DELETE' }),
    onSuccess: async () => {
      await invalidate()
      onClose()
    },
  })
  return (
    <form onSubmit={handleSubmit((v) => save.mutate(v))} noValidate className="flex flex-col gap-4">
      <Field label="Goal" error={formState.errors.name?.message}>
        {(a11y) => <Input {...a11y} placeholder="New laptop" {...register('name')} />}
      </Field>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Target (₹)" error={formState.errors.target?.message}>
          {(a11y) => <Input {...a11y} inputMode="decimal" className="figure" {...register('target')} />}
        </Field>
        <Field label="Saved so far (₹)" error={formState.errors.current?.message}>
          {(a11y) => <Input {...a11y} inputMode="decimal" className="figure" {...register('current')} />}
        </Field>
      </div>
      <Field label="By" error={formState.errors.targetDate?.message}>
        {(a11y) => <Input {...a11y} type="date" min={today} {...register('targetDate')} />}
      </Field>
      {formError && (
        <p role="alert" className="text-sm text-negative">
          {formError}
        </p>
      )}
      <div className="flex items-center justify-between gap-2">
        {goal ? (
          <Button
            variant="danger"
            loading={remove.isPending}
            onClick={() => (confirmDelete ? remove.mutate() : setConfirmDelete(true))}
          >
            {confirmDelete ? 'Click again to delete' : 'Delete'}
          </Button>
        ) : (
          <span />
        )}
        <div className="flex gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            Save
          </Button>
        </div>
      </div>
    </form>
  )
}
