import type { ReactNode } from 'react'

export interface Column<T> {
  key: string
  header: string
  cell: (row: T) => ReactNode
  /** What the column shows in a row's card, if it differs from the table cell. */
  cardCell?: (row: T) => ReactNode
  /** Where it goes in the card a row becomes on narrow screens. */
  card: 'title' | 'meta' | 'trailing' | 'none'
  /** False for a column that only appears in the card (in the table it is folded into another column). */
  table?: boolean
  align?: 'start' | 'end'
  /** Extra classes for the table cell, e.g. `whitespace-nowrap`. */
  className?: string
}

interface DataListProps<T> {
  rows: T[]
  columns: Column<T>[]
  rowKey: (row: T) => string
  /** Makes each row open something; the title cell (table) and the whole card (narrow) become buttons. */
  onSelect?: (row: T) => void
  /** The accessible name of a row's button, e.g. "Edit Groceries". */
  selectLabel?: (row: T) => string
}

const cardClass =
  'flex w-full items-start justify-between gap-3 rounded-card border border-line bg-surface p-3 text-left'

/**
 * A table on wide screens and a list of cards on narrow ones (spec §7), from one column definition. Both are in the
 * DOM and CSS shows one, so the content is identical at every width.
 */
export function DataList<T>({ rows, columns, rowKey, onSelect, selectLabel }: DataListProps<T>) {
  const tableColumns = columns.filter((c) => c.table !== false)
  const inCard = (slot: Column<T>['card']) => columns.filter((c) => c.card === slot)
  const cardContent = (c: Column<T>, row: T) => (c.cardCell ?? c.cell)(row)

  return (
    <>
      <table className="hidden w-full text-sm md:table">
        <thead>
          <tr className="border-b border-line text-left text-xs text-faint">
            {tableColumns.map((c) => (
              <th
                key={c.key}
                scope="col"
                className={`py-2 pr-4 font-medium last:pr-0 ${c.align === 'end' ? 'text-right' : ''}`}
              >
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)} className="border-b border-line/60 hover:bg-elevated/60">
              {tableColumns.map((c) => (
                <td
                  key={c.key}
                  className={`py-3 pr-4 last:pr-0 ${c.align === 'end' ? 'text-right' : ''} ${c.className ?? ''}`}
                >
                  {onSelect && c.card === 'title' ? (
                    <button
                      type="button"
                      onClick={() => onSelect(row)}
                      className="text-left hover:text-accent"
                      aria-label={selectLabel?.(row)}
                    >
                      {c.cell(row)}
                    </button>
                  ) : (
                    c.cell(row)
                  )}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>

      <ul className="flex flex-col gap-2 md:hidden">
        {rows.map((row) => {
          const content = (
            <>
              <span className="min-w-0">
                {inCard('title').map((c) => (
                  <span key={c.key} className="block truncate font-medium">
                    {cardContent(c, row)}
                  </span>
                ))}
                <span className="mt-1 flex flex-wrap items-center gap-2 text-xs text-faint">
                  {inCard('meta').map((c) => {
                    const value = cardContent(c, row)
                    return value === null || value === undefined || value === false || value === '' ? null : (
                      <span key={c.key}>{value}</span>
                    )
                  })}
                </span>
              </span>
              {inCard('trailing').map((c) => (
                <span key={c.key} className="shrink-0">
                  {cardContent(c, row)}
                </span>
              ))}
            </>
          )
          return (
            <li key={rowKey(row)}>
              {onSelect ? (
                <button
                  type="button"
                  onClick={() => onSelect(row)}
                  className={cardClass}
                  aria-label={selectLabel?.(row)}
                >
                  {content}
                </button>
              ) : (
                <div className={cardClass}>{content}</div>
              )}
            </li>
          )
        })}
      </ul>
    </>
  )
}
