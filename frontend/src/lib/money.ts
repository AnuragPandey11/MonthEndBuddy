import type { MoneyDto } from './api/types'

/**
 * Formatting only: no arithmetic on money happens in the browser (architecture §14). The decimal string goes to
 * Intl.NumberFormat as a string, which formats it exactly instead of converting through a float.
 */
const formatters = new Map<string, Intl.NumberFormat>()

function formatterFor(currency: string): Intl.NumberFormat {
  let formatter = formatters.get(currency)
  if (!formatter) {
    formatter = new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })
    formatters.set(currency, formatter)
  }
  return formatter
}

/** "12450.00" INR → "₹12,450.00"; lakh grouping: "100000" → "₹1,00,000.00". */
export function formatMoney(money: MoneyDto): string {
  return formatterFor(money.currency).format(money.amount as Intl.StringNumericLiteral)
}

/** The size of an amount without its sign, e.g. for "₹2,400.00 over". Formatting only: the minus is dropped. */
export function formatMoneyMagnitude(money: MoneyDto): string {
  return formatMoney({ ...money, amount: money.amount.replace(/^-/, '') })
}

/** Signed for display by transaction type; the stored amount is always positive (architecture §6.1). */
export function formatSignedMoney(money: MoneyDto, type: 'INCOME' | 'EXPENSE'): string {
  return `${type === 'INCOME' ? '+' : '−'}${formatMoney(money)}`
}

const compact = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  notation: 'compact',
  maximumFractionDigits: 1,
})

/** Axis ticks only: "₹25K", "₹2.5L". Values themselves are always shown in full from the API string. */
export function formatAxisRupees(value: number): string {
  return compact.format(value)
}

/** Charts plot numbers; this is the one place an API amount becomes a Number, and only for drawing. */
export function plotValue(money: MoneyDto): number {
  return Number(money.amount)
}
