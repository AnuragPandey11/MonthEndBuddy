/** Default user timezone (architecture §6.2); "today" for validation follows the user's settings. */
export const DEFAULT_TIMEZONE = 'Asia/Kolkata'

const displayFormat = new Intl.DateTimeFormat('en-IN', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
})

/** "2026-09-30" → "30 Sept 2026". Parsed as a UTC calendar date so no timezone can shift the day. */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return displayFormat.format(new Date(Date.UTC(year ?? 1970, (month ?? 1) - 1, day ?? 1)))
}

/** Today's calendar date in a timezone, as YYYY-MM-DD. */
export function todayIso(timeZone: string = DEFAULT_TIMEZONE, now: Date = new Date()): string {
  // en-CA formats dates as YYYY-MM-DD.
  return new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now)
}

/** "Tuesday, 27 October" for the top bar, in the given timezone. */
export function formatLongToday(timeZone: string = DEFAULT_TIMEZONE, now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-IN', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    timeZone,
  }).format(now)
}

/** "Good morning" before noon, "Good afternoon" before 5 pm, else "Good evening", in the given timezone. */
export function greeting(timeZone: string = DEFAULT_TIMEZONE, now: Date = new Date()): string {
  const hour = Number(
    new Intl.DateTimeFormat('en-GB', { hour: 'numeric', hourCycle: 'h23', timeZone }).format(now),
  )
  return hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening'
}
