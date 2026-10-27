import { useEffect, useState } from 'react'
import { Search, X } from 'lucide-react'
import { Button } from '../../components/Button'
import { Input, Select } from '../../components/Field'
import type { TransactionType } from '../../lib/api/types'
import type { TransactionFilters } from './api'
import { useCategories } from './api'

interface Props {
  value: TransactionFilters
  onChange: (filters: TransactionFilters) => void
}

export function TransactionFilterBar({ value, onChange }: Props) {
  const categories = useCategories()
  const [search, setSearch] = useState(value.q ?? '')

  // Debounce typing so each keystroke doesn't start a query.
  useEffect(() => {
    const handle = setTimeout(() => {
      if ((value.q ?? '') !== search.trim()) onChange({ ...value, q: search.trim() || undefined })
    }, 300)
    return () => clearTimeout(handle)
  }, [search, value, onChange])

  const active = Boolean(value.q || value.type || value.categoryId || value.from || value.to)

  return (
    <div className="flex flex-col gap-2 lg:flex-row lg:items-center">
      <label className="relative flex-1">
        <span className="sr-only">Search description or merchant</span>
        <Search
          className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-faint"
          aria-hidden
        />
        <Input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Search description or merchant"
          maxLength={100}
          className="pl-9"
        />
      </label>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:flex">
        <Select
          aria-label="Type"
          value={value.type ?? ''}
          onChange={(event) =>
            onChange({ ...value, type: (event.target.value || undefined) as TransactionType | undefined })
          }
        >
          <option value="">All types</option>
          <option value="EXPENSE">Expenses</option>
          <option value="INCOME">Income</option>
        </Select>
        <Select
          aria-label="Category"
          value={value.categoryId ?? ''}
          onChange={(event) => onChange({ ...value, categoryId: event.target.value || undefined })}
        >
          <option value="">All categories</option>
          {(categories.data ?? []).map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </Select>
        <Input
          type="date"
          aria-label="From date"
          value={value.from ?? ''}
          onChange={(event) => onChange({ ...value, from: event.target.value || undefined })}
        />
        <Input
          type="date"
          aria-label="To date"
          value={value.to ?? ''}
          onChange={(event) => onChange({ ...value, to: event.target.value || undefined })}
        />
      </div>
      {active && (
        <Button
          variant="ghost"
          onClick={() => {
            setSearch('')
            onChange({})
          }}
        >
          <X className="size-4" aria-hidden />
          Clear
        </Button>
      )}
    </div>
  )
}
