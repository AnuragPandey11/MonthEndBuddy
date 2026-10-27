import { useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { Field, Input, Select, TextArea } from '../../components/Field'
import { ApiError } from '../../lib/api/client'
import type { Transaction } from '../../lib/api/types'
import { todayIso } from '../../lib/dates'
import { useCategories, useCreateTransaction, useDeleteTransaction, useUpdateTransaction } from './api'
import {
  emptyForm,
  formFieldFor,
  formFromTransaction,
  toRequest,
  transactionFormSchema,
  type TransactionFormValues,
} from './schema'

interface Props {
  open: boolean
  /** The transaction being edited, or null to create one. */
  transaction: Transaction | null
  onClose: () => void
}

/** The form remounts each time the dialog opens (keyed by transaction), so it always starts from fresh state. */
export function TransactionFormDialog({ open, transaction, onClose }: Props) {
  return (
    <Dialog title={transaction ? 'Edit transaction' : 'Add transaction'} open={open} onClose={onClose}>
      <TransactionForm key={transaction?.id ?? 'new'} transaction={transaction} onClose={onClose} />
    </Dialog>
  )
}

function TransactionForm({ transaction, onClose }: { transaction: Transaction | null; onClose: () => void }) {
  const today = todayIso()
  const schema = useMemo(() => transactionFormSchema(today), [today])
  const categories = useCategories()
  const create = useCreateTransaction()
  const update = useUpdateTransaction()
  const remove = useDeleteTransaction()
  const [formError, setFormError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const {
    register,
    handleSubmit,
    setError,
    setValue,
    control,
    formState: { errors, isSubmitting },
  } = useForm<TransactionFormValues>({
    resolver: zodResolver(schema),
    defaultValues: transaction ? formFromTransaction(transaction) : emptyForm(today),
  })

  const type = useWatch({ control, name: 'type' })
  const categoryId = useWatch({ control, name: 'categoryId' })
  const usable = (categories.data ?? []).filter((c) => c.kind === type)
  // Keep a category that is no longer listed (e.g. archived) selectable while editing.
  const keepCurrent =
    transaction?.category &&
    transaction.type === type &&
    !usable.some((c) => c.id === transaction.category?.id)
      ? transaction.category
      : null

  useEffect(() => {
    if (
      categoryId &&
      categories.data &&
      !usable.some((c) => c.id === categoryId) &&
      keepCurrent?.id !== categoryId
    ) {
      setValue('categoryId', '')
    }
  }, [type, categoryId, categories.data, usable, keepCurrent, setValue])

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      if (transaction) {
        await update.mutateAsync({
          id: transaction.id,
          body: { ...toRequest(values), version: transaction.version },
        })
      } else {
        await create.mutateAsync(toRequest(values))
      }
      onClose()
    } catch (error) {
      if (!(error instanceof ApiError)) {
        setFormError('Something went wrong. Try again.')
      } else if (error.code === 'version_conflict') {
        setFormError('This transaction was changed elsewhere. Close this dialog to load the latest version.')
      } else if (error.errors.length > 0) {
        for (const violation of error.errors) {
          const field = formFieldFor(violation.field)
          if (field) setError(field, { message: violation.message })
          else setFormError(violation.message)
        }
      } else {
        setFormError(error.message)
      }
    }
  })

  const onDelete = async () => {
    if (!transaction) return
    if (!confirmDelete) {
      setConfirmDelete(true)
      return
    }
    try {
      await remove.mutateAsync(transaction.id)
      onClose()
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Could not delete. Try again.')
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <fieldset className="grid grid-cols-2 gap-1 rounded-control border border-line bg-surface p-1">
        <legend className="sr-only">Type</legend>
        {(['EXPENSE', 'INCOME'] as const).map((value) => (
          <label
            key={value}
            className="cursor-pointer rounded-md px-3 py-1.5 text-center text-sm text-muted has-checked:bg-elevated has-checked:text-ink has-focus-visible:outline-2 has-focus-visible:outline-accent"
          >
            <input type="radio" value={value} className="sr-only" {...register('type')} />
            {value === 'EXPENSE' ? 'Expense' : 'Income'}
          </label>
        ))}
      </fieldset>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Amount (₹)" error={errors.amount?.message}>
          {(a11y) => (
            <Input
              {...a11y}
              inputMode="decimal"
              placeholder="1250.00"
              className="figure"
              {...register('amount')}
            />
          )}
        </Field>
        <Field label="Date" error={errors.transactionDate?.message}>
          {(a11y) => <Input {...a11y} type="date" max={today} {...register('transactionDate')} />}
        </Field>
      </div>
      <Field label="Description" error={errors.description?.message}>
        {(a11y) => <Input {...a11y} placeholder="Dinner with friends" {...register('description')} />}
      </Field>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Merchant (optional)" error={errors.merchant?.message}>
          {(a11y) => <Input {...a11y} placeholder="Swiggy" {...register('merchant')} />}
        </Field>
        <Field label="Category" error={errors.categoryId?.message}>
          {(a11y) => (
            <Select {...a11y} {...register('categoryId')}>
              <option value="">Uncategorised</option>
              {keepCurrent && <option value={keepCurrent.id}>{keepCurrent.name} (archived)</option>}
              {usable.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </Select>
          )}
        </Field>
      </div>
      <Field label="Notes (optional)" error={errors.notes?.message}>
        {(a11y) => <TextArea {...a11y} rows={2} {...register('notes')} />}
      </Field>

      {formError && (
        <p
          role="alert"
          className="rounded-control border border-negative/30 bg-negative/10 px-3 py-2 text-sm text-negative"
        >
          {formError}
        </p>
      )}

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:items-center sm:justify-between">
        {transaction ? (
          <Button variant="danger" onClick={onDelete} loading={remove.isPending}>
            {confirmDelete ? 'Click again to delete' : 'Delete'}
          </Button>
        ) : (
          <span />
        )}
        <div className="flex gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={isSubmitting}>
            {transaction ? 'Save changes' : 'Add transaction'}
          </Button>
        </div>
      </div>
    </form>
  )
}
