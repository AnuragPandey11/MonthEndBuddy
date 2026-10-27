import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation } from '@tanstack/react-query'
import { Trash2 } from 'lucide-react'
import { Button } from '../../components/Button'
import { Field, Input, Select } from '../../components/Field'
import { ErrorState, Skeleton } from '../../components/States'
import { PageHeader } from '../../components/PageHeader'
import { api, ApiError } from '../../lib/api/client'
import { useCategoryRules, useInvalidateFinance, useSettings } from '../../lib/api/finance'
import type { CategoryRule, Settings } from '../../lib/api/types'
import { useCategories } from '../transactions/api'
import { Card } from '../../components/Card'

const AMOUNT = /^\d{1,15}(\.\d{1,2})?$/
const TIMEZONES = ['Asia/Kolkata', 'Asia/Dubai', 'Asia/Singapore', 'Europe/London', 'America/New_York', 'UTC']

export function SettingsPage() {
  const settings = useSettings()
  return (
    <section className="flex max-w-3xl flex-col gap-6">
      <PageHeader
        title="Settings"
        description="Figures here feed the purchase check, projections and alerts."
      />
      {settings.isPending ? (
        <Skeleton className="h-64" />
      ) : settings.isError ? (
        <ErrorState message="We couldn't load your settings." onRetry={() => void settings.refetch()} />
      ) : (
        <SettingsForm settings={settings.data} />
      )}
      <RulesSection />
    </section>
  )
}

const schema = z.object({
  currentSavings: z
    .string()
    .trim()
    .refine((v) => v === '' || AMOUNT.test(v), 'Use a number like 150000'),
  timezone: z.string(),
  emergencyBufferMonths: z.coerce.number().int().min(0, '0 to 24 months').max(24, '0 to 24 months'),
  largeTxnThreshold: z
    .string()
    .trim()
    .regex(AMOUNT, 'Use a number like 25000')
    .refine((v) => /[1-9]/.test(v), 'Must be more than zero'),
  anomalyMultiplier: z.coerce.number().min(1.5, 'Between 1.5 and 10').max(10, 'Between 1.5 and 10'),
  categorySpikePct: z.coerce.number().int().min(10, '10 to 500').max(500, '10 to 500'),
})

type Values = z.input<typeof schema>

function SettingsForm({ settings }: { settings: Settings }) {
  const invalidate = useInvalidateFinance()
  const [saved, setSaved] = useState(false)
  const initial: Values = {
    currentSavings: settings.currentSavings?.amount ?? '',
    timezone: settings.timezone,
    emergencyBufferMonths: settings.emergencyBufferMonths,
    largeTxnThreshold: settings.largeTxnThreshold.amount,
    anomalyMultiplier: settings.anomalyMultiplier,
    categorySpikePct: settings.categorySpikePct,
  }
  const { register, handleSubmit, formState } = useForm<Values, unknown, z.output<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: initial,
  })
  const save = useMutation({
    mutationFn: (v: z.output<typeof schema>) => {
      // PATCH only what changed; an empty savings field leaves the stored value alone.
      const body: Record<string, unknown> = {}
      if (v.currentSavings !== '' && v.currentSavings !== initial.currentSavings)
        body.currentSavings = { amount: v.currentSavings, currency: 'INR' }
      if (v.timezone !== initial.timezone) body.timezone = v.timezone
      if (v.emergencyBufferMonths !== initial.emergencyBufferMonths)
        body.emergencyBufferMonths = v.emergencyBufferMonths
      if (v.largeTxnThreshold !== initial.largeTxnThreshold)
        body.largeTxnThreshold = { amount: v.largeTxnThreshold, currency: 'INR' }
      if (v.anomalyMultiplier !== initial.anomalyMultiplier) body.anomalyMultiplier = v.anomalyMultiplier
      if (v.categorySpikePct !== initial.categorySpikePct) body.categorySpikePct = v.categorySpikePct
      return api<Settings>('/api/v1/me/settings', { method: 'PATCH', body })
    },
    onSuccess: async () => {
      setSaved(true)
      await invalidate()
    },
  })
  const e = formState.errors

  return (
    <Card
      as="form"
      padding="roomy"
      onSubmit={handleSubmit((v) => save.mutate(v))}
      noValidate
      className="flex flex-col gap-5"
      onChange={() => setSaved(false)}
    >
      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-2 text-sm font-semibold">Your money</legend>
        <Field
          label="Savings today (₹)"
          error={e.currentSavings?.message}
          hint={
            <>
              Everything in your accounts today, including money set aside for goals.{' '}
              {settings.currentSavingsUpdatedAt
                ? `Last updated ${new Date(settings.currentSavingsUpdatedAt).toLocaleDateString('en-IN')}.`
                : 'Not set yet. The purchase check needs it.'}
            </>
          }
        >
          {(a11y) => (
            <Input {...a11y} inputMode="decimal" className="figure" {...register('currentSavings')} />
          )}
        </Field>
        <Field
          label="Emergency buffer (months of spending)"
          error={e.emergencyBufferMonths?.message}
          hint="Kept free on top of your goals. If one of your goals is your emergency fund, set this to 0."
        >
          {(a11y) => (
            <Input {...a11y} type="number" min={0} max={24} {...register('emergencyBufferMonths')} />
          )}
        </Field>
        <Field label="Time zone" hint="Decides which day and month it is for you.">
          {(a11y) => (
            <Select {...a11y} {...register('timezone')}>
              {[...new Set([settings.timezone, ...TIMEZONES])].map((tz) => (
                <option key={tz} value={tz}>
                  {tz}
                </option>
              ))}
            </Select>
          )}
        </Field>
      </fieldset>
      <fieldset className="grid gap-4 sm:grid-cols-3">
        <legend className="mb-2 text-sm font-semibold">Alerts</legend>
        <Field label="Large purchase from (₹)" error={e.largeTxnThreshold?.message}>
          {(a11y) => (
            <Input {...a11y} inputMode="decimal" className="figure" {...register('largeTxnThreshold')} />
          )}
        </Field>
        <Field label="Unusual if × the median" error={e.anomalyMultiplier?.message}>
          {(a11y) => (
            <Input {...a11y} type="number" step="0.1" min={1.5} max={10} {...register('anomalyMultiplier')} />
          )}
        </Field>
        <Field label="Category spike at (+%)" error={e.categorySpikePct?.message}>
          {(a11y) => <Input {...a11y} type="number" min={10} max={500} {...register('categorySpikePct')} />}
        </Field>
      </fieldset>
      {save.isError && (
        <p role="alert" className="text-sm text-negative">
          {save.error instanceof ApiError
            ? (save.error.errors[0]?.message ?? save.error.message)
            : 'Could not save.'}
        </p>
      )}
      <div className="flex items-center gap-3">
        <Button type="submit" loading={save.isPending}>
          Save settings
        </Button>
        {saved && (
          <span role="status" className="text-sm text-positive">
            Saved
          </span>
        )}
      </div>
    </Card>
  )
}

const MATCH_LABELS: Record<CategoryRule['matchType'], string> = {
  CONTAINS: 'contains',
  STARTS_WITH: 'starts with',
  EQUALS: 'is exactly',
}

function RulesSection() {
  const rules = useCategoryRules()
  const categories = useCategories()
  const invalidate = useInvalidateFinance()
  const [error, setError] = useState<string | null>(null)
  const { register, handleSubmit, reset } = useForm<{
    matchType: CategoryRule['matchType']
    pattern: string
    categoryId: string
  }>({
    defaultValues: { matchType: 'CONTAINS', pattern: '', categoryId: '' },
  })
  const refresh = async () => {
    await rules.refetch()
    await invalidate()
  }
  const create = useMutation({
    mutationFn: (v: { matchType: string; pattern: string; categoryId: string }) =>
      api<CategoryRule>('/api/v1/category-rules', { method: 'POST', body: v }),
    onSuccess: async () => {
      setError(null)
      reset({ matchType: 'CONTAINS', pattern: '', categoryId: '' })
      await refresh()
    },
    onError: (e) =>
      setError(e instanceof ApiError ? (e.errors[0]?.message ?? e.message) : 'Could not add the rule.'),
  })
  const remove = useMutation({
    mutationFn: (id: string) => api<void>(`/api/v1/category-rules/${id}`, { method: 'DELETE' }),
    onSuccess: refresh,
  })

  return (
    <Card as="section" padding="roomy" aria-labelledby="rules-heading">
      <h2 id="rules-heading" className="text-heading">
        Category rules
      </h2>
      <p className="mb-4 text-xs text-faint">
        New and imported transactions without a category get one from the first rule that matches the
        description or merchant. A category you pick yourself always wins.
      </p>
      {rules.isPending ? (
        <Skeleton className="h-16" />
      ) : rules.data && rules.data.length > 0 ? (
        <ul className="mb-4 flex flex-col divide-y divide-line">
          {rules.data.map((r) => (
            <li key={r.id} className="flex items-center justify-between gap-3 py-2 text-sm">
              <span>
                <span className="text-muted">If text {MATCH_LABELS[r.matchType]}</span> “{r.pattern}”{' '}
                <span className="text-muted">→</span>{' '}
                {r.category.name ?? <span className="text-faint">archived category</span>}
              </span>
              <button
                type="button"
                className="rounded-md p-1.5 text-muted hover:bg-elevated hover:text-negative"
                onClick={() => remove.mutate(r.id)}
                aria-label={`Delete rule ${r.pattern}`}
              >
                <Trash2 className="size-4" aria-hidden />
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="mb-4 text-sm text-muted">No rules yet.</p>
      )}
      <form className="flex flex-wrap items-end gap-2" onSubmit={handleSubmit((v) => create.mutate(v))}>
        <Select aria-label="Match" className="w-36" {...register('matchType')}>
          <option value="CONTAINS">contains</option>
          <option value="STARTS_WITH">starts with</option>
          <option value="EQUALS">is exactly</option>
        </Select>
        <Input
          aria-label="Text"
          placeholder="swiggy"
          className="w-40"
          maxLength={100}
          {...register('pattern', { required: true })}
        />
        <Select aria-label="Category" className="w-44" {...register('categoryId', { required: true })}>
          <option value="">Choose a category</option>
          {(categories.data ?? []).map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </Select>
        <Button type="submit" variant="secondary" loading={create.isPending}>
          Add rule
        </Button>
      </form>
      {error && (
        <p role="alert" className="mt-2 text-sm text-negative">
          {error}
        </p>
      )}
    </Card>
  )
}
