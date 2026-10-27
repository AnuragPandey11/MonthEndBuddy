import { describe, expect, it } from 'vitest'
import type { Transaction } from '../../lib/api/types'
import { emptyForm, formFieldFor, formFromTransaction, toRequest, transactionFormSchema } from './schema'

const today = '2026-10-26'
const schema = transactionFormSchema(today)
const valid = { ...emptyForm(today), amount: '1250.50', description: 'Dinner' }

function errorsFor(values: Record<string, unknown>): Record<string, string> {
  const result = schema.safeParse(values)
  if (result.success) return {}
  return Object.fromEntries(result.error.issues.map((issue) => [String(issue.path[0]), issue.message]))
}

describe('transaction form schema', () => {
  it('accepts a valid expense', () => {
    expect(schema.safeParse(valid).success).toBe(true)
  })

  it.each([
    ['0', 'Amount must be greater than zero'],
    ['0.00', 'Amount must be greater than zero'],
    ['-5', 'Enter an amount like 1250 or 1250.50'],
    ['12.345', 'Enter an amount like 1250 or 1250.50'],
    ['1e5', 'Enter an amount like 1250 or 1250.50'],
    ['1,000', 'Enter an amount like 1250 or 1250.50'],
  ])('rejects amount %s', (amount, message) => {
    expect(errorsFor({ ...valid, amount }).amount).toBe(message)
  })

  it('rejects future dates and blank descriptions', () => {
    expect(errorsFor({ ...valid, transactionDate: '2026-10-27' }).transactionDate).toBe(
      'Date cannot be in the future',
    )
    expect(errorsFor({ ...valid, description: '   ' }).description).toBe('Enter a description')
  })

  it('builds the API request with a string amount and omits empty optionals', () => {
    expect(toRequest({ ...valid, amount: ' 1250.50 ', merchant: '  ' })).toEqual({
      type: 'EXPENSE',
      amount: { amount: '1250.50', currency: 'INR' },
      transactionDate: today,
      description: 'Dinner',
      merchant: undefined,
      categoryId: undefined,
      notes: undefined,
    })
  })

  it('round-trips an existing transaction into the form', () => {
    const t = {
      id: 'x',
      type: 'INCOME',
      amount: { amount: '95000.00', currency: 'INR' },
      transactionDate: '2026-10-01',
      description: 'Salary',
      merchant: 'Acme',
      category: { id: 'c1', name: 'Salary' },
      categorySource: 'USER',
      source: 'MANUAL',
      version: 3,
    } as Transaction
    expect(formFromTransaction(t)).toMatchObject({
      type: 'INCOME',
      amount: '95000.00',
      categoryId: 'c1',
      notes: '',
    })
  })

  it('maps API error fields onto form fields', () => {
    expect(formFieldFor('amount.currency')).toBe('amount')
    expect(formFieldFor('transactionDate')).toBe('transactionDate')
    expect(formFieldFor('cursor')).toBeNull()
  })
})
