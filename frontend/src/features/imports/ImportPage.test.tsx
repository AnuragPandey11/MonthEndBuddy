import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { inr, renderPage } from '../../test/render'
import { ImportPage } from './ImportPage'

const job = {
  id: 'j1',
  filename: 'statement.csv',
  status: 'VALIDATED',
  dateFormat: 'ISO_YYYY_MM_DD',
  totals: { total: 3, valid: 1, invalid: 1, duplicate: 1 },
  sampleErrors: [{ rowNumber: 3, field: 'date', message: 'must be a date like 2026-09-30' }],
  result: null,
  failureReason: null,
}

const row = (id: string, status: string, extra: object = {}) => ({
  id,
  rowNumber: Number(id.slice(1)) + 1,
  status,
  transactionDate: '2026-09-03',
  type: 'EXPENSE',
  amount: inr('120.00'),
  description: `Row ${id}`,
  merchant: null,
  categoryName: null,
  errors: [],
  warnings: [],
  ...extra,
})

describe('ImportPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uploads, previews errors and duplicates, and commits the chosen duplicates', async () => {
    const fetchMock = renderPage(<ImportPage />, {
      'POST /api/v1/imports': job,
      'GET /api/v1/imports/j1/rows': [
        row('r1', 'VALID'),
        row('r2', 'INVALID', {
          amount: null,
          errors: [{ field: 'date', message: 'must be a date like 2026-09-30' }],
        }),
        row('r3', 'DUPLICATE'),
      ],
      'POST /api/v1/imports/j1/commit': { jobId: 'j1', imported: 2, skippedDuplicates: 0, skippedInvalid: 1 },
    })

    const input = document.querySelector('input[type=file]') as HTMLInputElement
    await userEvent.upload(
      input,
      new File(['date,description,amount\n'], 'statement.csv', { type: 'text/csv' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Check the file' }))

    // Errors tab opens first, with the row-level problem.
    expect(await screen.findByText('must be a date like 2026-09-30')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Import 1 transaction' })).toBeEnabled()

    await userEvent.click(screen.getByRole('tab', { name: /Duplicates/ }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Import row 4 anyway' }))
    await userEvent.click(screen.getByRole('button', { name: 'Import 2 transactions' }))

    expect(await screen.findByText('Imported 2 transactions')).toBeInTheDocument()
    const commit = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/commit'))
    expect(JSON.parse(String(commit?.[1]?.body))).toEqual({ forceRowIds: ['r3'] })
  })

  it('shows a file-level problem from the API', async () => {
    renderPage(<ImportPage />, {
      'POST /api/v1/imports': () => {
        throw new Error('unreachable')
      },
    })
    vi.stubGlobal(
      'fetch',
      vi.fn(
        async () =>
          new Response(
            JSON.stringify({
              code: 'validation_failed',
              errors: [{ field: 'file', message: 'is missing required columns: amount' }],
            }),
            { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
          ),
      ),
    )
    const input = document.querySelector('input[type=file]') as HTMLInputElement
    await userEvent.upload(input, new File(['x'], 'bad.csv', { type: 'text/csv' }))
    await userEvent.click(screen.getByRole('button', { name: 'Check the file' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('is missing required columns: amount')
  })
})
