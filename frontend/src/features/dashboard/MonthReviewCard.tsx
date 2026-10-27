import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { motion } from 'motion/react'
import { CalendarCheck, CheckCircle2 } from 'lucide-react'
import { Button } from '../../components/Button'
import { Card, Chip, IconChip } from '../../components/Card'
import { Input, Select } from '../../components/Field'
import { ApiError } from '../../lib/api/client'
import { useCompleteMonthReview, useGoals, useMonthReview } from '../../lib/api/finance'
import type { MonthReview } from '../../lib/api/types'
import { enter } from '../../lib/motion'
import { formatDate, todayIso } from '../../lib/dates'
import { formatMonth } from '../../lib/months'
import { formatMoney } from '../../lib/money'

/** A positive amount with at most two decimals, as typed; the API checks it again. */
const AMOUNT = /^(?!0+(\.0+)?$)\d+(\.\d{1,2})?$/

/**
 * The month-end review (architecture §9.6): once a month has ended in surplus, it offers to add what was kept to
 * Savings today and to set some aside for a goal. Nothing changes until the user confirms, and the API records the
 * review once. The amounts are pre-filled with the API's own figure and never computed here. The card hides itself
 * when nothing is due or the review cannot be loaded.
 */
export function MonthReviewCard() {
  const review = useMonthReview()
  const [finished, setFinished] = useState<MonthReview | null>(null)
  if (finished) return <Finished review={finished} />
  if (review.data?.status !== 'DUE') return null
  return <ReviewForm review={review.data} onFinished={setFinished} />
}

function ReviewForm({ review, onFinished }: { review: MonthReview; onFinished: (r: MonthReview) => void }) {
  const goals = useGoals()
  const complete = useCompleteMonthReview()
  const savingsKnown = review.currentSavings !== null
  // Unticked when Savings today was updated after the month ended: it may already include this money.
  const [addToSavings, setAddToSavings] = useState(savingsKnown && !review.savingsUpdatedSinceMonthEnd)
  const [savingsAmount, setSavingsAmount] = useState(review.kept.amount)
  const [goalId, setGoalId] = useState('')
  const [goalAmount, setGoalAmount] = useState('')
  const [invalid, setInvalid] = useState<string | null>(null)
  const active = (goals.data ?? []).filter((g) => g.status === 'ACTIVE')
  const month = formatMonth(review.month)

  function submit(skip: boolean) {
    setInvalid(null)
    if (skip) {
      complete.mutate({ month: review.month }, { onSuccess: onFinished })
      return
    }
    if (addToSavings && !AMOUNT.test(savingsAmount)) {
      setInvalid('Enter the amount to add to your savings, like 23749.50.')
      return
    }
    if (goalId && !AMOUNT.test(goalAmount)) {
      setInvalid('Enter the amount to set aside for the goal, like 5000.')
      return
    }
    complete.mutate(
      {
        month: review.month,
        addToSavings: addToSavings ? { amount: savingsAmount, currency: review.kept.currency } : undefined,
        goalContributions: goalId
          ? [{ goalId, amount: { amount: goalAmount, currency: review.kept.currency } }]
          : undefined,
      },
      { onSuccess: onFinished },
    )
  }

  const nothingChosen = !addToSavings && !goalId

  return (
    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={enter}>
      <Card as="section" aria-labelledby="month-review-title" className="flex flex-col gap-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <IconChip className="bg-lime text-on-lime">
              <CalendarCheck />
            </IconChip>
            <div>
              <h2 id="month-review-title" className="text-heading">
                {month} is done. You kept {formatMoney(review.kept)}.
              </h2>
              <p className="text-sm text-muted">
                Record what happened to it. Nothing changes until you confirm, and you can do this once.
              </p>
            </div>
          </div>
          <Chip>Month-end review</Chip>
        </div>

        <form
          noValidate
          className="grid gap-4 md:grid-cols-2"
          onSubmit={(event: FormEvent) => {
            event.preventDefault()
            submit(false)
          }}
        >
          <fieldset className="flex flex-col gap-2 rounded-[1.125rem] bg-elevated p-4">
            <legend className="sr-only">Add to savings</legend>
            <label className="flex items-center gap-2 text-sm font-medium">
              <input
                type="checkbox"
                checked={addToSavings}
                disabled={!savingsKnown}
                onChange={(event) => setAddToSavings(event.target.checked)}
                className="size-4 accent-[var(--color-accent)]"
              />
              Add to Savings today
            </label>
            {savingsKnown ? (
              <>
                <label className="sr-only" htmlFor="review-savings">
                  Amount to add to Savings today (₹)
                </label>
                <Input
                  id="review-savings"
                  inputMode="decimal"
                  className="figure"
                  value={savingsAmount}
                  disabled={!addToSavings}
                  onChange={(event) => setSavingsAmount(event.target.value)}
                />
                {review.savingsUpdatedSinceMonthEnd && review.currentSavingsUpdatedAt ? (
                  <p className="text-xs text-warning">
                    You updated Savings today ({formatMoney(review.currentSavings!)}) on{' '}
                    {formatDate(todayIso(undefined, new Date(review.currentSavingsUpdatedAt)))}, after {month}{' '}
                    ended, so it may already include this. Tick the box only if it doesn't.
                  </p>
                ) : (
                  <p className="text-xs text-faint">
                    Savings today is {formatMoney(review.currentSavings!)}. Only add this if you haven't
                    already updated it yourself.
                  </p>
                )}
              </>
            ) : (
              <p className="text-xs text-faint">
                Your savings today aren't set, so there is nothing to add to.{' '}
                <Link to="/settings" className="font-medium text-accent">
                  Set them in Settings
                </Link>
              </p>
            )}
          </fieldset>

          <fieldset className="flex flex-col gap-2 rounded-[1.125rem] bg-elevated p-4">
            <legend className="sr-only">Set aside for a goal</legend>
            <label htmlFor="review-goal" className="text-sm font-medium">
              Set aside for a goal (optional)
            </label>
            {active.length === 0 ? (
              <p className="text-xs text-faint">
                No active goals.{' '}
                <Link to="/goals" className="font-medium text-accent">
                  Add one
                </Link>
              </p>
            ) : (
              <div className="flex gap-2">
                <Select
                  id="review-goal"
                  value={goalId}
                  onChange={(event) => setGoalId(event.target.value)}
                  className="min-w-0 flex-1"
                >
                  <option value="">No goal</option>
                  {active.map((g) => (
                    <option key={g.id} value={g.id}>
                      {g.name}
                    </option>
                  ))}
                </Select>
                <label className="sr-only" htmlFor="review-goal-amount">
                  Amount to set aside (₹)
                </label>
                {/* The control fills its box, so the box sets the width. */}
                <div className="w-36 shrink-0">
                  <Input
                    id="review-goal-amount"
                    inputMode="decimal"
                    placeholder="Amount"
                    className="figure"
                    value={goalAmount}
                    disabled={!goalId}
                    onChange={(event) => setGoalAmount(event.target.value)}
                  />
                </div>
              </div>
            )}
            <p className="text-xs text-faint">
              This marks part of your savings for the goal. It doesn't move any money.
            </p>
          </fieldset>

          <div className="flex flex-wrap items-center gap-2 md:col-span-2">
            <Button type="submit" loading={complete.isPending} disabled={nothingChosen}>
              Confirm
            </Button>
            <Button variant="ghost" disabled={complete.isPending} onClick={() => submit(true)}>
              Skip this month
            </Button>
            {(invalid || complete.isError) && (
              <p role="alert" className="text-sm text-negative">
                {invalid ??
                  (complete.error instanceof ApiError ? complete.error.message : 'Something went wrong.')}
              </p>
            )}
          </div>
        </form>
      </Card>
    </motion.div>
  )
}

/** What the confirmed review did, from the API's answer. */
function Finished({ review }: { review: MonthReview }) {
  const done: string[] = []
  if (review.addedToSavings) done.push(`added ${formatMoney(review.addedToSavings)} to Savings today`)
  if (review.setAsideForGoals) done.push(`set aside ${formatMoney(review.setAsideForGoals)} for your goal`)
  return (
    <motion.div initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={enter}>
      <Card className="flex items-center gap-3" role="status">
        <CheckCircle2 className="size-5 shrink-0 text-positive" aria-hidden />
        <p className="text-sm">
          {review.status === 'SKIPPED'
            ? `${formatMonth(review.month)} skipped. Nothing was changed.`
            : `${formatMonth(review.month)} recorded: ${done.join(' and ')}.`}
        </p>
      </Card>
    </motion.div>
  )
}
