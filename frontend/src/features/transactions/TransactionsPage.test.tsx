import { afterEach, describe, expect, it, vi } from 'vitest'
import { useLocation } from 'react-router'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { inr, renderPage } from '../../test/render'
import type { Transaction } from '../../lib/api/types'
import { TransactionsPage } from './TransactionsPage'
import { expectNoSeriousA11yViolations } from '../../test/axe'

const rent = {
  id: 't1',
  type: 'EXPENSE',
  amount: inr('25000.00'),
  transactionDate: '2026-10-01',
  description: 'Rent',
  merchant: null,
  category: { id: 'c1', name: 'Housing' },
  categorySource: 'USER',
  source: 'MANUAL',
  version: 0,
} as unknown as Transaction

/** Shows the URL's query string, so a test can see the page change it. */
function Search() {
  return <output aria-label="Query string">{useLocation().search}</output>
}

function renderTransactions(path: string) {
  return renderPage(
    <>
      <TransactionsPage />
      <Search />
    </>,
    {
      'GET /api/v1/transactions': { items: [rent], nextCursor: null },
      'GET /api/v1/categories': [],
    },
    path,
  )
}

describe('TransactionsPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('has no serious accessibility violations, list and add form', async () => {
    renderTransactions('/transactions')
    await screen.findAllByRole('button', { name: /^Edit Rent/ })
    await expectNoSeriousA11yViolations()
    await userEvent.click(screen.getByRole('button', { name: /add transaction/i }))
    await screen.findByRole('dialog', { name: 'Add transaction' })
    await expectNoSeriousA11yViolations()
  })

  it('opens the add form from the top bar link', async () => {
    renderTransactions('/transactions?new=1')

    expect(await screen.findByRole('dialog', { name: 'Add transaction' })).toBeInTheDocument()
    // The flag is removed once used, so going back or reloading does not reopen the form.
    expect(screen.getByRole('status', { name: 'Query string' })).toHaveTextContent(/^$/)
    await userEvent.click(screen.getByRole('button', { name: 'Close' }))
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('lists transactions as a table and as cards, both opening the same form', async () => {
    renderTransactions('/transactions')

    const [inTable, inCard] = await screen.findAllByRole('button', { name: /^Edit Rent/ })
    expect(screen.getByRole('columnheader', { name: 'Amount' })).toBeInTheDocument()
    expect(inCard).toHaveTextContent('Housing')
    await userEvent.click(inTable!)
    expect(screen.getByRole('dialog', { name: 'Edit transaction' })).toBeInTheDocument()
  })
})
