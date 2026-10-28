import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { FileUp, Plus } from 'lucide-react'
import { Button } from '../../components/Button'
import { EmptyState, ErrorState, Skeleton } from '../../components/States'
import { PageHeader } from '../../components/PageHeader'
import type { Transaction } from '../../lib/api/types'
import { useTransactions, type TransactionFilters } from './api'
import { TransactionFilterBar } from './TransactionFilterBar'
import { TransactionFormDialog } from './TransactionFormDialog'
import { TransactionList } from './TransactionList'

export function TransactionsPage() {
  const [filters, setFilters] = useState<TransactionFilters>({})
  const [dialog, setDialog] = useState<{ open: boolean; transaction: Transaction | null }>({
    open: false,
    transaction: null,
  })
  const [params, setParams] = useSearchParams()
  const query = useTransactions(filters)

  // The top bar's "Add a transaction" links here with ?new=1, also when this page is already open. The flag is
  // removed once used, so going back or reloading does not reopen the form.
  const wantsNew = params.get('new') === '1'
  useEffect(() => {
    if (!wantsNew) return
    // eslint-disable-next-line react-hooks/set-state-in-effect -- opening the form is the effect of the link
    setDialog({ open: true, transaction: null })
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        next.delete('new')
        return next
      },
      { replace: true },
    )
  }, [wantsNew, setParams])
  const transactions = query.data?.pages.flatMap((page) => page.items) ?? []
  const filtered = Boolean(filters.q || filters.type || filters.categoryId || filters.from || filters.to)
  const close = useCallback(() => setDialog({ open: false, transaction: null }), [])

  return (
    <section className="flex flex-col gap-section">
      <PageHeader
        title="Transactions"
        description="Everything you've spent and earned, newest first."
        actions={
          <>
            <Link
              to="/import"
              className="inline-flex items-center gap-2 rounded-control border border-line-strong bg-elevated px-3.5 py-2 text-sm font-medium hover:border-faint"
            >
              <FileUp className="size-4" aria-hidden />
              <span className="max-sm:sr-only">Import CSV</span>
            </Link>
            <Button onClick={() => setDialog({ open: true, transaction: null })}>
              <Plus className="size-4" aria-hidden />
              <span className="max-sm:sr-only">Add transaction</span>
              <span className="sm:hidden">Add</span>
            </Button>
          </>
        }
      />

      <TransactionFilterBar value={filters} onChange={setFilters} />

      {query.isPending ? (
        <div className="flex flex-col gap-2" aria-label="Loading transactions">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-12" />
          ))}
        </div>
      ) : query.isError ? (
        <ErrorState message="We couldn't load your transactions." onRetry={() => void query.refetch()} />
      ) : transactions.length === 0 ? (
        filtered ? (
          <EmptyState
            title="No transactions match these filters"
            body="Try a different search or clear the filters."
          />
        ) : (
          <EmptyState
            title="No transactions yet"
            body="Add your first one, or import a CSV statement."
            action={
              <Button onClick={() => setDialog({ open: true, transaction: null })}>Add transaction</Button>
            }
          />
        )
      ) : (
        <>
          <TransactionList
            transactions={transactions}
            onSelect={(t) => setDialog({ open: true, transaction: t })}
          />
          {query.hasNextPage && (
            <div className="flex justify-center">
              <Button
                variant="secondary"
                onClick={() => void query.fetchNextPage()}
                loading={query.isFetchingNextPage}
              >
                Load more
              </Button>
            </div>
          )}
        </>
      )}

      <TransactionFormDialog open={dialog.open} transaction={dialog.transaction} onClose={close} />
    </section>
  )
}
