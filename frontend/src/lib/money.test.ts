import { describe, expect, it } from 'vitest'
import { formatMoney, formatSignedMoney } from './money'

describe('formatMoney', () => {
  it('uses Indian lakh grouping and two decimals', () => {
    expect(formatMoney({ amount: '100000', currency: 'INR' })).toBe('₹1,00,000.00')
    expect(formatMoney({ amount: '12450.5', currency: 'INR' })).toBe('₹12,450.50')
    expect(formatMoney({ amount: '9876543.21', currency: 'INR' })).toBe('₹98,76,543.21')
  })

  it('formats the decimal string exactly, without float rounding', () => {
    // 0.1 + 0.2 style errors would show up here if the string went through Number.
    expect(formatMoney({ amount: '999999999999999.99', currency: 'INR' })).toBe('₹99,99,99,99,99,99,999.99')
  })

  it('handles negative values', () => {
    expect(formatMoney({ amount: '-75.50', currency: 'INR' })).toBe('-₹75.50')
  })

  it('signs by transaction type', () => {
    expect(formatSignedMoney({ amount: '95000.00', currency: 'INR' }, 'INCOME')).toBe('+₹95,000.00')
    expect(formatSignedMoney({ amount: '1250.50', currency: 'INR' }, 'EXPENSE')).toBe('−₹1,250.50')
  })
})
