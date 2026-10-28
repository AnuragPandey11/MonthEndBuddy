import { DataList, type Column } from '../../components/DataList'
import type { Transaction } from '../../lib/api/types'
import { formatDate } from '../../lib/dates'
import { formatSignedMoney } from '../../lib/money'

interface Props {
  transactions: Transaction[]
  /** Omit for a read-only list (rows are not buttons). */
  onSelect?: (transaction: Transaction) => void
}

function Amount({ t }: { t: Transaction }) {
  return (
    <span className={`figure font-medium ${t.type === 'INCOME' ? 'text-positive' : 'text-ink'}`}>
      {formatSignedMoney(t.amount, t.type)}
    </span>
  )
}

function CategoryLabel({ t }: { t: Transaction }) {
  return t.category?.name ? (
    <span className="rounded-pill border border-line px-2 py-0.5 text-xs text-muted">{t.category.name}</span>
  ) : (
    <span className="text-xs text-faint">Uncategorised</span>
  )
}

const columns: Column<Transaction>[] = [
  {
    key: 'date',
    header: 'Date',
    cell: (t) => formatDate(t.transactionDate),
    card: 'meta',
    className: 'figure whitespace-nowrap text-muted',
  },
  {
    key: 'description',
    header: 'Description',
    cell: (t) => (
      <>
        <span className="block font-medium">{t.description}</span>
        {t.merchant && <span className="block text-xs text-faint">{t.merchant}</span>}
      </>
    ),
    cardCell: (t) => t.description,
    card: 'title',
  },
  // In the table the merchant sits under the description.
  {
    key: 'merchant',
    header: 'Merchant',
    cell: (t) => t.merchant && `· ${t.merchant}`,
    card: 'meta',
    table: false,
  },
  { key: 'category', header: 'Category', cell: (t) => <CategoryLabel t={t} />, card: 'meta' },
  {
    key: 'amount',
    header: 'Amount',
    cell: (t) => <Amount t={t} />,
    card: 'trailing',
    align: 'end',
    className: 'whitespace-nowrap',
  },
]

/** A table on wide screens, cards on narrow ones (spec §7). Amounts come from the API as strings. */
export function TransactionList({ transactions, onSelect }: Props) {
  return (
    <DataList
      rows={transactions}
      columns={columns}
      rowKey={(t) => t.id}
      onSelect={onSelect}
      selectVerb="Edit"
    />
  )
}
