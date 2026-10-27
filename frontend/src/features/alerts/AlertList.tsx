import { Info, TriangleAlert } from 'lucide-react'
import type { Alert } from '../../lib/api/types'
import { formatMonth } from '../../lib/months'
import { useResolveAlert } from '../../lib/api/finance'

/** Alerts show the backend's sentence, which is rendered from the numbers that triggered the rule (architecture §9.5). */
export function AlertList({ alerts }: { alerts: Alert[] }) {
  const resolve = useResolveAlert()
  return (
    <ul className="flex flex-col gap-3">
      {alerts.map((alert) => (
        <li key={alert.id} className="flex gap-3">
          {alert.severity === 'WARNING' ? (
            <TriangleAlert className="mt-0.5 size-4 shrink-0 text-warning" aria-label="Warning" />
          ) : (
            <Info className="mt-0.5 size-4 shrink-0 text-accent" aria-label="Notice" />
          )}
          <div className="min-w-0 flex-1">
            <p className="text-sm">{alert.message}</p>
            <div className="mt-1 flex flex-wrap items-center gap-3 text-xs text-faint">
              <span>{formatMonth(alert.periodStart.slice(0, 7))}</span>
              <button
                type="button"
                className="hover:text-ink"
                disabled={resolve.isPending}
                onClick={() => resolve.mutate({ id: alert.id, status: 'EXPECTED' })}
              >
                This was expected
              </button>
              <button
                type="button"
                className="hover:text-ink"
                disabled={resolve.isPending}
                onClick={() => resolve.mutate({ id: alert.id, status: 'DISMISSED' })}
              >
                Dismiss
              </button>
            </div>
          </div>
        </li>
      ))}
    </ul>
  )
}
