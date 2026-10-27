import { useState } from 'react'
import { Link } from 'react-router'
import { CheckCircle2, FileUp, TriangleAlert } from 'lucide-react'
import { Button } from '../../components/Button'
import { Field, Select } from '../../components/Field'
import { ErrorState, Skeleton } from '../../components/States'
import { PageHeader } from '../../components/PageHeader'
import { ApiError } from '../../lib/api/client'
import type { ImportJob, ImportResult, ImportRow } from '../../lib/api/types'
import { formatDate } from '../../lib/dates'
import { formatSignedMoney } from '../../lib/money'
import { useCommit, useDiscard, useImportRows, useUpload } from './api'
import { Card } from '../../components/Card'

type Tab = 'INVALID' | 'DUPLICATE' | 'VALID'

/**
 * CSV import in two steps (architecture D14): upload and preview every row's outcome, then commit. Nothing is
 * imported until the user commits; duplicates are skipped unless the user picks them.
 */
export function ImportPage() {
  const [file, setFile] = useState<File | null>(null)
  const [dateFormat, setDateFormat] = useState<ImportJob['dateFormat']>('ISO_YYYY_MM_DD')
  const [job, setJob] = useState<ImportJob | null>(null)
  const [result, setResult] = useState<ImportResult | null>(null)
  const upload = useUpload()
  const commit = useCommit()
  const discard = useDiscard()

  const reset = () => {
    setJob(null)
    setResult(null)
    setFile(null)
    upload.reset()
    commit.reset()
  }

  const uploadError =
    upload.error instanceof ApiError ? (upload.error.errors[0]?.message ?? upload.error.message) : null

  return (
    <section className="flex max-w-4xl flex-col gap-5">
      <PageHeader
        title="Import a statement"
        description={
          <>
            Upload a CSV with <code className="text-ink">date</code>,{' '}
            <code className="text-ink">description</code> and <code className="text-ink">amount</code> columns
            (optionally type, merchant, category, currency, notes). You review every row before anything is
            saved.
          </>
        }
      />

      {result ? (
        <Done result={result} onAnother={reset} />
      ) : job ? (
        <Preview
          job={job}
          committing={commit.isPending}
          commitError={commit.error instanceof ApiError ? commit.error.message : null}
          onCommit={(forceRowIds) => commit.mutate({ jobId: job.id, forceRowIds }, { onSuccess: setResult })}
          onDiscard={() => discard.mutate(job.id, { onSuccess: reset })}
        />
      ) : (
        <Card
          as="form"
          padding="roomy"
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            if (file) upload.mutate({ file, dateFormat }, { onSuccess: setJob })
          }}
        >
          <label className="flex cursor-pointer flex-col items-center gap-2 rounded-card border border-dashed border-line-strong px-6 py-10 text-center hover:border-accent">
            <FileUp className="size-7 text-faint" aria-hidden />
            <span className="text-sm font-medium">{file ? file.name : 'Choose a CSV file'}</span>
            <span className="text-xs text-faint">Up to 5 MB and 10,000 rows, UTF-8</span>
            <input
              type="file"
              accept=".csv,text/csv"
              className="sr-only"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
          </label>
          <div className="max-w-xs">
            <Field label="Date format in the file">
              {(a11y) => (
                <Select
                  {...a11y}
                  value={dateFormat}
                  onChange={(e) => setDateFormat(e.target.value as ImportJob['dateFormat'])}
                >
                  <option value="ISO_YYYY_MM_DD">2026-09-30 (year-month-day)</option>
                  <option value="DD_MM_YYYY">30/09/2026 (day/month/year)</option>
                </Select>
              )}
            </Field>
          </div>
          {uploadError && (
            <p role="alert" className="text-sm text-negative">
              {uploadError}
            </p>
          )}
          <div>
            <Button type="submit" disabled={!file} loading={upload.isPending}>
              Check the file
            </Button>
          </div>
        </Card>
      )}
    </section>
  )
}

function Preview({
  job,
  committing,
  commitError,
  onCommit,
  onDiscard,
}: {
  job: ImportJob
  committing: boolean
  commitError: string | null
  onCommit: (forceRowIds: string[]) => void
  onDiscard: () => void
}) {
  const rows = useImportRows(job.id)
  const [tab, setTab] = useState<Tab>(
    job.totals.invalid > 0 ? 'INVALID' : job.totals.duplicate > 0 ? 'DUPLICATE' : 'VALID',
  )
  const [forced, setForced] = useState<Set<string>>(new Set())
  const counts: Record<Tab, number> = {
    INVALID: job.totals.invalid,
    DUPLICATE: job.totals.duplicate,
    VALID: job.totals.valid,
  }
  const willImport = job.totals.valid + forced.size

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {(
          [
            ['Rows in file', job.totals.total],
            ['Ready to import', job.totals.valid],
            ['Already imported', job.totals.duplicate],
            ['With errors', job.totals.invalid],
          ] as const
        ).map(([label, value]) => (
          <Card padding="compact" key={label}>
            <p className="text-xs text-muted">{label}</p>
            <p className="mt-0.5 text-lg font-semibold">{value}</p>
          </Card>
        ))}
      </div>

      <div
        role="tablist"
        aria-label="Rows"
        className="flex gap-1 rounded-control border border-line bg-surface p-1"
      >
        {(['INVALID', 'DUPLICATE', 'VALID'] as Tab[]).map((t) => (
          <button
            key={t}
            role="tab"
            type="button"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className="flex-1 rounded-md px-3 py-1.5 text-sm text-muted aria-selected:bg-elevated aria-selected:text-ink"
          >
            {{ INVALID: 'Errors', DUPLICATE: 'Duplicates', VALID: 'Ready' }[t]} ({counts[t]})
          </button>
        ))}
      </div>

      <Card padding="compact" role="tabpanel">
        {rows.isPending ? (
          <Skeleton className="h-32" />
        ) : rows.isError ? (
          <ErrorState message="We couldn't load the rows." onRetry={() => void rows.refetch()} />
        ) : (
          <RowTable
            rows={rows.data.filter((r) => r.status === tab)}
            tab={tab}
            forced={forced}
            onToggle={(id) =>
              setForced((current) => {
                const next = new Set(current)
                if (next.has(id)) next.delete(id)
                else next.add(id)
                return next
              })
            }
          />
        )}
      </Card>

      {commitError && (
        <p role="alert" className="text-sm text-negative">
          {commitError}
        </p>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <Button onClick={() => onCommit([...forced])} loading={committing} disabled={willImport === 0}>
          Import {willImport} transaction{willImport === 1 ? '' : 's'}
        </Button>
        <Button variant="ghost" onClick={onDiscard}>
          Discard
        </Button>
        {job.totals.invalid > 0 && (
          <p className="text-xs text-faint">
            Rows with errors are skipped; fix them in the file and upload again.
          </p>
        )}
      </div>
    </div>
  )
}

function RowTable({
  rows,
  tab,
  forced,
  onToggle,
}: {
  rows: ImportRow[]
  tab: Tab
  forced: Set<string>
  onToggle: (id: string) => void
}) {
  if (rows.length === 0) return <p className="px-1 py-4 text-sm text-muted">No rows here.</p>
  return (
    <div className="max-h-96 overflow-auto">
      <table className="w-full text-sm">
        <thead className="sticky top-0 bg-surface">
          <tr className="border-b border-line text-left text-xs text-faint">
            {tab === 'DUPLICATE' && (
              <th scope="col" className="py-1.5 pr-2 font-medium">
                Import anyway
              </th>
            )}
            <th scope="col" className="py-1.5 pr-3 font-medium">
              Row
            </th>
            <th scope="col" className="py-1.5 pr-3 font-medium">
              Date
            </th>
            <th scope="col" className="py-1.5 pr-3 font-medium">
              Description
            </th>
            <th scope="col" className="py-1.5 pr-3 text-right font-medium">
              Amount
            </th>
            {tab !== 'DUPLICATE' && (
              <th scope="col" className="py-1.5 font-medium">
                {tab === 'INVALID' ? 'Problems' : 'Notes'}
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id} className="border-b border-line/50 align-top">
              {tab === 'DUPLICATE' && (
                <td className="py-1.5 pr-2">
                  <input
                    type="checkbox"
                    checked={forced.has(r.id)}
                    onChange={() => onToggle(r.id)}
                    aria-label={`Import row ${r.rowNumber} anyway`}
                  />
                </td>
              )}
              <td className="figure py-1.5 pr-3 text-faint">{r.rowNumber}</td>
              <td className="py-1.5 pr-3 whitespace-nowrap text-muted">
                {r.transactionDate ? formatDate(r.transactionDate) : '—'}
              </td>
              <td className="py-1.5 pr-3">{r.description ?? '—'}</td>
              <td className="figure py-1.5 pr-3 text-right whitespace-nowrap">
                {r.amount && r.type ? formatSignedMoney(r.amount, r.type) : '—'}
              </td>
              {tab === 'INVALID' && (
                <td className="py-1.5 text-xs text-negative">
                  {r.errors.map((e) => (
                    <p key={e.field + e.message}>
                      <span className="font-medium">{e.field}</span> {e.message}
                    </p>
                  ))}
                </td>
              )}
              {tab === 'VALID' && (
                <td className="py-1.5 text-xs text-warning">
                  {r.warnings.map((w) => (
                    <p key={w.message} className="inline-flex gap-1">
                      <TriangleAlert className="mt-0.5 size-3 shrink-0" aria-hidden /> {w.message}
                    </p>
                  ))}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function Done({ result, onAnother }: { result: ImportResult; onAnother: () => void }) {
  return (
    <div className="flex flex-col items-start gap-3 rounded-card border border-positive/30 bg-positive/5 p-5">
      <p className="inline-flex items-center gap-2 font-medium">
        <CheckCircle2 className="size-5 text-positive" aria-hidden />
        Imported {result.imported} transaction{result.imported === 1 ? '' : 's'}
      </p>
      <p className="text-sm text-muted">
        Skipped {result.skippedDuplicates} already imported and {result.skippedInvalid} with errors.
      </p>
      <div className="flex gap-3">
        <Link to="/transactions" className="text-sm font-medium text-accent hover:underline">
          See transactions
        </Link>
        <button type="button" onClick={onAnother} className="text-sm text-muted hover:text-ink">
          Import another file
        </button>
      </div>
    </div>
  )
}
