import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { inr, renderPage } from '../../test/render'
import { expectNoSeriousA11yViolations } from '../../test/axe'
import { GoalsPage } from './GoalsPage'

const goal = {
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
}

const projection = {
  target: inr('80000.00'),
  current: inr('20000.00'),
  remaining: inr('60000.00'),
  targetDate: '2027-01-01',
  monthsRemaining: 3,
  requiredMonthly: inr('20000.00'),
  monthlyContribution: null,
  status: 'ON_TRACK',
  projectedCompletion: '2027-01',
  series: [
    { month: '2026-11', balance: inr('40000.00') },
    { month: '2026-12', balance: inr('60000.00') },
    { month: '2027-01', balance: inr('80000.00') },
  ],
  assumptions: [{ name: 'goalSaved', value: '20000.00 (saved toward this goal)', label: 'USER_PROFILE' }],
}

const simulation = {
  verdict: 'AFFORDABLE_WITH_TRADEOFFS',
  purchaseAmount: inr('60000.00'),
  purchaseMonth: '2026-10',
  currentSavings: inr('80000.00'),
  savingsSetAside: inr('20000.00'),
  availableSavings: inr('60000.00'),
  savingsAfterPurchase: inr('0.00'),
  averageMonthlyIncome: inr('95000.00'),
  averageMonthlyExpenses: inr('65000.00'),
  monthlySurplus: inr('30000.00'),
  emergencyBuffer: inr('195000.00'),
  goalNeed: inr('20000.00'),
  recoveryMonths: 6,
  goalImpacts: [],
  assumptions: [],
  dataQuality: [],
  monthsOfData: 3,
}

describe('GoalsPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows the goal money set aside in the purchase check, with no serious accessibility violations', async () => {
    renderPage(<GoalsPage />, {
      'GET /api/v1/goals': [goal],
      'POST /api/v1/goals/g1/projection': projection,
      'POST /api/v1/simulations/purchase': simulation,
    })
    expect(await screen.findByText('Saved toward this goal:')).toBeInTheDocument()
    await expectNoSeriousA11yViolations()

    await userEvent.type(screen.getByLabelText('Purchase (₹)'), '60000')
    await userEvent.click(screen.getByRole('button', { name: 'Check' }))
    expect(await screen.findByText('Set aside for goals')).toBeInTheDocument()
    expect(screen.getByText('Free to spend')).toBeInTheDocument()
    expect(screen.getAllByText('₹60,000.00').length).toBeGreaterThan(0)
    await expectNoSeriousA11yViolations()
  })
})
