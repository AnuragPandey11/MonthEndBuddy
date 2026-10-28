import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { Field, Input, Select } from '../../components/Field'
import { Meter } from '../../components/Meter'
import { MonthPicker } from '../../components/MonthPicker'
import { EmptyState, ErrorState, Skeleton } from '../../components/States'
import { PageHeader } from '../../components/PageHeader'
import { api, ApiError } from '../../lib/api/client'
import { useBudgets, useBudgetStatus, useInvalidateFinance } from '../../lib/api/finance'
import type { Budget, BudgetStatus } from '../../lib/api/types'
import { formatDate, todayIso } from '../../lib/dates'
import { currentMonth, formatMonth } from '../../lib/months'
import { formatMoney, formatMoneyMagnitude } from '../../lib/money'
import { useCategories } from '../transactions/api'
import { Card } from '../../components/Card'

const AMOUNT = /^\d{1,15}(\.\d{1,2})?$/

const budgetSchema = z.object({
  categoryId: z.string(),
  limit: z
    .string()
    .trim()
    .regex(AMOUNT, 'Enter an amount like 6000 or 6000.50')
    .refine((v) => /[1-9]/.test(v), 'Limit must be greater than zero'),
  thresholds: z
    .string()
    .trim()
    .regex(/^(\d{1,3})(\s*,\s*\d{1,3}){0,4}$/, 'Up to five percentages, e.g. 80, 100'),
})

type BudgetValues = z.infer<typeof budgetSchema>

/** Monthly budgets (architecture §9.2): status for the chosen month, with the transactions that used the most. */
export function BudgetsPage() {
  const maxMonth = currentMonth(todayIso())
  const [month, setMonth] = useState(maxMonth)
  const status = useBudgetStatus(month)
  const budgets = useBudgets()
  const [editing, setEditing] = useState<{ open: boolean; budget: Budget | null }>({
    open: false,
    budget: null,
  })

  const definition = (id: string) => budgets.data?.find((b) => b.id === id) ?? null

  return (
    <section className="flex flex-col gap-section">
      <PageHeader
        title="Budgets"
        description="Monthly limits, overall and per category. They repeat every month."
        actions={
          <>
            <MonthPicker value={month} max={maxMonth} onChange={setMonth} />
            <Button onClick={() => setEditing({ open: true, budget: null })}>
              <Plus className="size-4" aria-hidden />
              Add budget
            </Button>
          </>
        }
      />

      {status.isPending ? (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <Skeleton className="h-40" />
          <Skeleton className="h-40" />
        </div>
      ) : status.isError ? (
        <ErrorState message="We couldn't load your budgets." onRetry={() => void status.refetch()} />
      ) : status.data.budgets.length === 0 ? (
        <EmptyState
          title="No budgets yet"
          body="Set an overall monthly limit, or one for a category you want to keep an eye on."
          action={<Button onClick={() => setEditing({ open: true, budget: null })}>Add budget</Button>}
        />
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {status.data.budgets.map((b) => (
            <BudgetCard
              key={b.budgetId}
              status={b}
              month={month}
              onEdit={() => setEditing({ open: true, budget: definition(b.budgetId) })}
            />
          ))}
        </div>
      )}

      <BudgetDialog
        open={editing.open}
        budget={editing.budget}
        onClose={() => setEditing({ open: false, budget: null })}
      />
    </section>
  )
}

function BudgetCard({
  status: b,
  month,
  onEdit,
}: {
  status: BudgetStatus
  month: string
  onEdit: () => void
}) {
  return (
    <Card as="article">
      <header className="mb-3 flex items-start justify-between gap-2">
        <div>
          <h2 className="font-medium">{b.categoryName}</h2>
          <p className="text-xs text-faint">{formatMonth(month)}</p>
        </div>
        <Button variant="ghost" className="px-2 py-1 text-xs" onClick={onEdit}>
          Edit
        </Button>
      </header>
      <p className="figure text-lg font-semibold">
        {formatMoney(b.spent)}{' '}
        <span className="text-sm font-normal text-muted">of {formatMoney(b.limit)}</span>
      </p>
      <div className="my-2">
        <Meter
          pct={b.pctUsed}
          kind="budget"
          warnAt={b.alertThresholds[0] ?? 80}
          label={`${b.pctUsed}% used`}
        />
      </div>
      <p className="text-xs text-muted">
        {b.overspent ? `${formatMoneyMagnitude(b.remaining)} over` : `${formatMoney(b.remaining)} left`} ·{' '}
        {b.pctUsed}% used
      </p>
      {b.topTransactions.length > 0 && (
        <div className="mt-3 border-t border-line pt-3">
          <p className="mb-1.5 text-xs text-faint">Largest this month</p>
          <ul className="flex flex-col gap-1 text-sm">
            {b.topTransactions.map((t) => (
              <li key={t.id} className="flex justify-between gap-3">
                <span className="truncate">
                  {t.merchant ?? t.description}{' '}
                  <span className="text-xs text-faint">{formatDate(t.transactionDate)}</span>
                </span>
                <span className="figure shrink-0">{formatMoney(t.amount)}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  )
}

/** Values to pre-fill, e.g. from a copilot proposal. The user still reviews and saves the form. */
export interface BudgetDraft {
  categoryId?: string
  limit?: string
}

export function BudgetDialog({
  open,
  budget,
  draft,
  onClose,
}: {
  open: boolean
  budget: Budget | null
  draft?: BudgetDraft
  onClose: () => void
}) {
  return (
    <Dialog
      title={budget ? `Edit ${budget.categoryName} budget` : 'Add budget'}
      open={open}
      onClose={onClose}
    >
      <BudgetForm key={budget?.id ?? 'new'} budget={budget} draft={draft} onClose={onClose} />
    </Dialog>
  )
}

function BudgetForm({
  budget,
  draft,
  onClose,
}: {
  budget: Budget | null
  draft?: BudgetDraft
  onClose: () => void
}) {
  const categories = useCategories()
  const invalidate = useInvalidateFinance()
  const [formError, setFormError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<BudgetValues>({
    resolver: zodResolver(budgetSchema),
    defaultValues: {
      categoryId: budget?.categoryId ?? draft?.categoryId ?? '',
      limit: draft?.limit ?? budget?.limit.amount ?? '',
      thresholds: (budget?.alertThresholds ?? [80, 100]).join(', '),
    },
  })

  const save = useMutation({
    mutationFn: (values: BudgetValues) => {
      const body = {
        limit: { amount: values.limit, currency: 'INR' },
        alertThresholds: values.thresholds.split(',').map((t) => Number(t.trim())),
      }
      return budget
        ? api<Budget>(`/api/v1/budgets/${budget.id}`, {
            method: 'PUT',
            body: { ...body, version: budget.version },
          })
        : api<Budget>('/api/v1/budgets', {
            method: 'POST',
            body: { ...body, categoryId: values.categoryId || null },
          })
    },
    onSuccess: async () => {
      await invalidate()
      onClose()
    },
    onError: (error) =>
      setFormError(error instanceof ApiError ? error.message : 'Could not save. Try again.'),
  })
  const remove = useMutation({
    mutationFn: () => api<void>(`/api/v1/budgets/${budget?.id}`, { method: 'DELETE' }),
    onSuccess: async () => {
      await invalidate()
      onClose()
    },
  })

  return (
    <form onSubmit={handleSubmit((v) => save.mutate(v))} noValidate className="flex flex-col gap-4">
      {!budget && (
        <Field label="Applies to">
          {(a11y) => (
            <Select {...a11y} {...register('categoryId')}>
              <option value="">All spending (overall budget)</option>
              {(categories.data ?? [])
                .filter((c) => c.kind === 'EXPENSE')
                .map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
            </Select>
          )}
        </Field>
      )}
      <Field label="Monthly limit (₹)" error={errors.limit?.message}>
        {(a11y) => <Input {...a11y} inputMode="decimal" className="figure" {...register('limit')} />}
      </Field>
      <Field
        label="Alert at (% used)"
        error={errors.thresholds?.message}
        hint="We note each one once a month."
      >
        {(a11y) => <Input {...a11y} {...register('thresholds')} />}
      </Field>
      {formError && (
        <p role="alert" className="text-sm text-negative">
          {formError}
        </p>
      )}
      <div className="flex items-center justify-between gap-2">
        {budget ? (
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
