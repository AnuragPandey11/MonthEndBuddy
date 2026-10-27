/** Calendar months as "YYYY-MM" strings, the API's month format (architecture §13.1). */

export function currentMonth(today: string): string {
  return today.slice(0, 7)
}

export function addMonths(month: string, delta: number): string {
  const [year, m] = month.split('-').map(Number)
  const index = (year ?? 1970) * 12 + ((m ?? 1) - 1) + delta
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}`
}

const longFormat = new Intl.DateTimeFormat('en-IN', { month: 'long', year: 'numeric', timeZone: 'UTC' })
const shortFormat = new Intl.DateTimeFormat('en-IN', { month: 'short', timeZone: 'UTC' })

function toDate(month: string): Date {
  const [year, m] = month.split('-').map(Number)
  return new Date(Date.UTC(year ?? 1970, (m ?? 1) - 1, 1))
}

/** "2026-09" → "September 2026" */
export function formatMonth(month: string): string {
  return longFormat.format(toDate(month))
}

/** "2026-09" → "Sept" (chart axis) */
export function formatMonthShort(month: string): string {
  return shortFormat.format(toDate(month))
}
