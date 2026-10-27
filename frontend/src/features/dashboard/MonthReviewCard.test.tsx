import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { inr, renderPage } from '../../test/render'
import { MonthReviewCard } from './MonthReviewCard'

const due = {
  month: '2026-09',
  kept: inr('23749.50'),
  transactionCount: 42,
  status: 'DUE',
  currentSavings: inr('80000.00'),
  currentSavingsUpdatedAt: '2026-09-20T10:00:00Z',
  savingsUpdatedSinceMonthEnd: false,
  addedToSavings: null,
  setAsideForGoals: null,
}

const goals = [
  {
    id: 'g1',
    name: 'iPhone',
    target: inr('80000.00'),
    current: inr('20000.00'),
    remaining: inr('60000.00'),
    progressPct: 25,
    targetDate: '2027-01-01',
    status: 'ACTIVE',
    priority: 100,
    version: 0,
  },
]

/** The review POST; the session refresh is a POST too. */
function reviewPost(fetchMock: ReturnType<typeof renderPage>) {
  return fetchMock.mock.calls.find(
    ([url, init]) => init?.method === 'POST' && String(url) === '/api/v1/month-review',
  )
}

describe('MonthReviewCard', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('stays hidden when nothing is due', async () => {
    const fetchMock = renderPage(<MonthReviewCard />, {
      'GET /api/v1/month-review': { ...due, status: 'NOTHING_KEPT' },
    })
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled())
    expect(screen.queryByRole('region')).toBeNull()
  })

  it('offers what the month kept and sends exactly what the user chose', async () => {
    const fetchMock = renderPage(<MonthReviewCard />, {
      'GET /api/v1/month-review': due,
      'GET /api/v1/goals': goals,
      'POST /api/v1/month-review': {
        ...due,
        status: 'DONE',
        currentSavings: inr('103749.50'),
        addedToSavings: inr('23749.50'),
        setAsideForGoals: inr('10000.00'),
      },
    })
    expect(await screen.findByText('September 2026 is done. You kept ₹23,749.50.')).toBeInTheDocument()
    // Pre-filled with the API's figure, as given.
    expect(screen.getByLabelText('Amount to add to Savings today (₹)')).toHaveValue('23749.50')

    await userEvent.selectOptions(await screen.findByLabelText('Set aside for a goal (optional)'), 'g1')
    await userEvent.type(screen.getByLabelText('Amount to set aside (₹)'), '10000')
    await userEvent.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(
      await screen.findByText(
        'September 2026 recorded: added ₹23,749.50 to Savings today and set aside ₹10,000.00 for your goal.',
      ),
    ).toBeInTheDocument()
    expect(JSON.parse(String(reviewPost(fetchMock)?.[1]?.body))).toEqual({
      month: '2026-09',
      addToSavings: inr('23749.50'),
      goalContributions: [{ goalId: 'g1', amount: inr('10000') }],
    })
  })

  it('starts unticked when Savings today was updated after the month ended, so nothing is counted twice', async () => {
    renderPage(<MonthReviewCard />, {
      'GET /api/v1/month-review': {
        ...due,
        currentSavingsUpdatedAt: '2026-10-27T10:00:00Z',
        savingsUpdatedSinceMonthEnd: true,
      },
      'GET /api/v1/goals': goals,
    })
    expect(await screen.findByRole('checkbox', { name: 'Add to Savings today' })).not.toBeChecked()
    expect(screen.getByText(/after September 2026 ended, so it may already include this/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm' })).toBeDisabled()
  })

  it('never offers to add to savings that are not set, and can skip the month', async () => {
    const fetchMock = renderPage(<MonthReviewCard />, {
      'GET /api/v1/month-review': { ...due, currentSavings: null },
      'GET /api/v1/goals': [],
      'POST /api/v1/month-review': { ...due, status: 'SKIPPED', currentSavings: null },
    })
    expect(await screen.findByRole('checkbox', { name: 'Add to Savings today' })).toBeDisabled()
    expect(screen.getByRole('link', { name: 'Set them in Settings' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: 'Skip this month' }))
    expect(await screen.findByText('September 2026 skipped. Nothing was changed.')).toBeInTheDocument()
    expect(JSON.parse(String(reviewPost(fetchMock)?.[1]?.body))).toEqual({ month: '2026-09' })
  })

  it('rejects an amount that is not a positive number before sending anything', async () => {
    const fetchMock = renderPage(<MonthReviewCard />, {
      'GET /api/v1/month-review': due,
      'GET /api/v1/goals': goals,
    })
    const amount = await screen.findByLabelText('Amount to add to Savings today (₹)')
    await userEvent.clear(amount)
    await userEvent.type(amount, '0')
    await userEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Enter the amount to add to your savings')
    expect(reviewPost(fetchMock)).toBeUndefined()
  })
})
