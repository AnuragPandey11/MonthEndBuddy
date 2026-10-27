import { ChevronLeft, ChevronRight } from 'lucide-react'
import { addMonths, formatMonth } from '../lib/months'

interface MonthPickerProps {
  value: string
  max: string
  onChange: (month: string) => void
}

/** The page's one filter (dataviz: one row, above everything it scopes). Future months are not selectable. */
export function MonthPicker({ value, max, onChange }: MonthPickerProps) {
  return (
    <div className="inline-flex items-center gap-1 rounded-pill border border-line bg-surface p-1 shadow-card">
      <button
        type="button"
        onClick={() => onChange(addMonths(value, -1))}
        className="rounded-pill p-1.5 text-muted hover:bg-elevated hover:text-ink"
        aria-label="Previous month"
      >
        <ChevronLeft className="size-4" aria-hidden />
      </button>
      <label className="relative">
        <span className="sr-only">Month</span>
        <input
          type="month"
          value={value}
          max={max}
          onChange={(event) =>
            event.target.value && onChange(event.target.value <= max ? event.target.value : max)
          }
          className="w-40 rounded-pill bg-transparent px-2 py-1 text-center text-sm text-ink focus:outline-none"
          aria-description={formatMonth(value)}
        />
      </label>
      <button
        type="button"
        onClick={() => onChange(addMonths(value, 1))}
        disabled={value >= max}
        className="rounded-pill p-1.5 text-muted hover:bg-elevated hover:text-ink disabled:opacity-30"
        aria-label="Next month"
      >
        <ChevronRight className="size-4" aria-hidden />
      </button>
    </div>
  )
}
