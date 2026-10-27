import { useId, useState, type ReactNode } from 'react'
import { Table2 } from 'lucide-react'
import { Card, CardHeader } from './Card'

interface ChartCardProps {
  title: string
  subtitle?: string
  icon?: ReactNode
  className?: string
  action?: ReactNode
  /** The chart's table-view twin; every value the chart shows is readable here without hovering. */
  table?: ReactNode
  children: ReactNode
}

export function ChartCard({
  title,
  subtitle,
  icon,
  className = '',
  action,
  table,
  children,
}: ChartCardProps) {
  const [showTable, setShowTable] = useState(false)
  const regionId = useId()
  return (
    <Card as="section" aria-labelledby={`${regionId}-t`} className={className}>
      <CardHeader
        title={title}
        subtitle={subtitle}
        icon={icon}
        titleId={`${regionId}-t`}
        action={
          (action || table) && (
            <>
              {action}
              {table && (
                <button
                  type="button"
                  onClick={() => setShowTable((v) => !v)}
                  aria-pressed={showTable}
                  className="inline-flex items-center gap-1 rounded-pill border border-line px-2.5 py-1 text-xs font-medium text-muted hover:bg-elevated hover:text-ink aria-pressed:border-night aria-pressed:bg-night aria-pressed:text-on-night"
                >
                  <Table2 className="size-3.5" aria-hidden />
                  Table
                </button>
              )}
            </>
          )
        }
      />
      {showTable ? table : children}
    </Card>
  )
}

/** A compact, aligned table for chart twins. */
export function DataTable({ headers, rows }: { headers: string[]; rows: string[][] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-line text-left text-xs text-faint">
            {headers.map((h, i) => (
              <th key={h} scope="col" className={`py-1.5 pr-3 font-medium ${i > 0 ? 'text-right' : ''}`}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row[0]} className="border-b border-line/50">
              {row.map((cell, i) => (
                <td key={i} className={`py-1.5 pr-3 ${i > 0 ? 'figure text-right' : ''}`}>
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
