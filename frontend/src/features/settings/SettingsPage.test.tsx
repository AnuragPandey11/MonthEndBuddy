import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { inr, renderPage } from '../../test/render'
import { expectNoSeriousA11yViolations } from '../../test/axe'
import { SettingsPage } from './SettingsPage'

describe('SettingsPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('says Savings today includes goal money, with no serious accessibility violations', async () => {
    renderPage(<SettingsPage />, {
      'GET /api/v1/me/settings': {
        baseCurrency: 'INR',
        timezone: 'Asia/Kolkata',
        currentSavings: inr('80000.00'),
        currentSavingsUpdatedAt: '2026-10-27T10:00:00Z',
        emergencyBufferMonths: 3,
        largeTxnThreshold: inr('25000.00'),
        anomalyMultiplier: 3,
        categorySpikePct: 40,
      },
      'GET /api/v1/category-rules': [],
      'GET /api/v1/categories': [],
    })
    expect(await screen.findByText(/including money set aside for goals/)).toBeInTheDocument()
    expect(screen.getByText(/If one of your goals is your emergency fund, set this to 0/)).toBeInTheDocument()
    await expectNoSeriousA11yViolations()
  })
})
