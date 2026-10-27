import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { emptyCoverage, inr, renderPage } from '../../test/render'
import { currentMonth } from '../../lib/months'
import { todayIso } from '../../lib/dates'
import { DashboardPage } from './DashboardPage'

const month = currentMonth(todayIso())
const coverage = {
  transactionCount: 87,
  firstDate: `${month}-01`,
  lastDate: `${month}-20`,
  monthsWithData: [month],
}

const routes = {
  'GET /api/v1/analytics/summary': {
    month,
    income: inr('95000.00'),
    expenses: inr('71250.50'),
    net: inr('23749.50'),
    savingsRate: 25,
    savingsRateReason: null,
    coverage,
  },
  'GET /api/v1/analytics/compare': {
    current: month,
    previous: month,
    totals: {
      current: inr('71250.50'),
      previous: inr('64300.00'),
      delta: inr('6950.50'),
      deltaPct: 10.8,
      isNew: false,
    },
    categories: [],
  },
  'GET /api/v1/analytics/categories': {
    from: `${month}-01`,
    to: `${month}-28`,
    total: inr('71250.50'),
    categories: [
      { categoryId: 'c1', name: 'Rent', amount: inr('25000.00'), pct: 35.1, count: 1 },
      { categoryId: null, name: 'Uncategorised', amount: inr('99.99'), pct: 0.1, count: 1 },
    ],
    coverage,
  },
  'GET /api/v1/analytics/trends': {
    points: [
      { month: '2026-05', income: inr('0.00'), expenses: inr('0.00'), net: inr('0.00'), hasData: false },
      { month, income: inr('95000.00'), expenses: inr('71250.50'), net: inr('23749.50'), hasData: true },
    ],
  },
  'GET /api/v1/budgets/status': {
    month,
    coverage,
    budgets: [
      {
        budgetId: 'b1',
        categoryId: 'c2',
        categoryName: 'Dining',
        limit: inr('6000.00'),
        spent: inr('8400.00'),
        remaining: inr('-2400.00'),
        pctUsed: 140,
        overspent: true,
        alertThresholds: [80, 100],
        thresholdsCrossed: [80, 100],
        topTransactions: [],
      },
    ],
  },
  'GET /api/v1/goals': [],
  'GET /api/v1/alerts': [
    {
      id: 'a1',
      ruleId: 'LARGE_VS_CATEGORY',
      subjectType: 'TRANSACTION',
      subjectId: 't1',
      subjectLabel: 'The Leela Palace',
      periodStart: `${month}-01`,
      severity: 'WARNING',
      status: 'OPEN',
      message: '₹8,400.00 at The Leela Palace is 11.0× your typical Dining expense.',
      createdAt: '2026-10-26T10:00:00Z',
    },
  ],
  'GET /api/v1/transactions': { items: [], nextCursor: null },
  'GET /api/v1/month-review': {
    month: '2026-09',
    kept: inr('0.00'),
    transactionCount: 0,
    status: 'NOTHING_KEPT',
    currentSavings: null,
    addedToSavings: null,
    setAsideForGoals: null,
  },
}

describe('DashboardPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows every figure exactly as the API returned it', async () => {
    renderPage(<DashboardPage />, routes)
    expect(await screen.findByText(/^Good (morning|afternoon|evening), Asha$/)).toBeInTheDocument()
    // Net leads the hero and reappears as "Kept"; income sits on its card and in the trend's pinned values.
    expect((await screen.findAllByText('₹23,749.50')).length).toBeGreaterThan(0)
    expect(screen.getAllByText('₹95,000.00').length).toBeGreaterThan(0)
    expect(screen.getAllByText('₹71,250.50').length).toBeGreaterThan(0)
    expect(screen.getByText('25%')).toBeInTheDocument()
    expect(screen.getByText(/\+10\.8% vs/)).toBeInTheDocument()
    expect(screen.getByText('From 87 transactions')).toBeInTheDocument()
    // The largest category is called out on the expenses card and listed in the category card.
    expect((await screen.findAllByText(/₹25,000\.00/)).length).toBeGreaterThan(0)
    expect(screen.getByText('35.1%')).toBeInTheDocument()
    expect(screen.getByText('Rent 35.1%')).toBeInTheDocument()
    expect(await screen.findByText('₹8,400.00 of ₹6,000.00')).toBeInTheDocument()
    expect(screen.getByText('Over budget')).toBeInTheDocument()
    expect(screen.getByText(/The Leela Palace is 11\.0×/)).toBeInTheDocument()
  })

  it('says "no data" instead of zero for a month without transactions, in the chart table too', async () => {
    renderPage(<DashboardPage />, {
      ...routes,
      'GET /api/v1/analytics/summary': {
        month,
        income: inr('0.00'),
        expenses: inr('0.00'),
        net: inr('0.00'),
        savingsRate: null,
        savingsRateReason: 'NO_INCOME',
        coverage: emptyCoverage,
      },
    })
    expect(await screen.findByText('No transactions recorded this month.')).toBeInTheDocument()
    expect(screen.getByText('—')).toBeInTheDocument()
    expect(screen.getByText('No income this month')).toBeInTheDocument()

    const cashFlow = screen.getByRole('region', { name: 'Cash flow' })
    await userEvent.click(within(cashFlow).getByRole('button', { name: /table/i }))
    const rows = within(cashFlow).getAllByRole('row')
    expect(rows[1]).toHaveTextContent('May 2026No dataNo data—')
  })

  it('resolves an alert through the API', async () => {
    const fetchMock = renderPage(<DashboardPage />, {
      ...routes,
      'PATCH /api/v1/alerts/a1': { ...routes['GET /api/v1/alerts'][0], status: 'EXPECTED' },
    })
    await userEvent.click(await screen.findByRole('button', { name: 'This was expected' }))
    const patch = fetchMock.mock.calls.find(([, init]) => init?.method === 'PATCH')
    expect(String(patch?.[0])).toBe('/api/v1/alerts/a1')
    expect(JSON.parse(String(patch?.[1]?.body))).toEqual({ status: 'EXPECTED' })
  })
})
