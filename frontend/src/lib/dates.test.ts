import { describe, expect, it } from 'vitest'
import { formatDate, formatLongToday, greeting, todayIso } from './dates'

describe('dates', () => {
  it('formats a calendar date without timezone drift', () => {
    expect(formatDate('2026-09-30')).toMatch(/^30 Sept? 2026$/)
    expect(formatDate('2026-01-01')).toMatch(/^1 Jan 2026$/)
  })

  it('computes today in the user timezone', () => {
    // 20:00 UTC on 31 Dec is already 1 Jan in India (UTC+5:30).
    const instant = new Date('2026-12-31T20:00:00Z')
    expect(todayIso('Asia/Kolkata', instant)).toBe('2027-01-01')
    expect(todayIso('UTC', instant)).toBe('2026-12-31')
  })

  it('greets and dates the top bar in the user timezone', () => {
    // 06:00 UTC is 11:30 in India: still morning there, and the same calendar day.
    const instant = new Date('2026-10-27T06:00:00Z')
    expect(greeting('Asia/Kolkata', instant)).toBe('Good morning')
    expect(greeting('Asia/Kolkata', new Date('2026-10-27T09:00:00Z'))).toBe('Good afternoon')
    expect(greeting('Asia/Kolkata', new Date('2026-10-27T13:00:00Z'))).toBe('Good evening')
    expect(formatLongToday('Asia/Kolkata', instant)).toBe('Tuesday, 27 October')
  })
})
