import { z } from 'zod'
import type { Transaction, TransactionRequest } from '../../lib/api/types'

/** Same rule as the backend's @PositiveMoney: plain decimal, at most 2 decimal places, greater than zero. */
const AMOUNT = /^\d{1,15}(\.\d{1,2})?$/
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

/** Form validation mirrors the backend (architecture §8.1); the server stays the authority. */
export function transactionFormSchema(today: string) {
  return z.object({
    type: z.enum(['EXPENSE', 'INCOME']),
    amount: z
      .string()
      .trim()
      .regex(AMOUNT, 'Enter an amount like 1250 or 1250.50')
      // Validation, not arithmetic: a positive amount has a non-zero digit.
      .refine((value) => /[1-9]/.test(value), 'Amount must be greater than zero'),
    transactionDate: z
      .string()
      .regex(ISO_DATE, 'Pick a date')
      // ISO dates compare correctly as strings.
      .refine((value) => value <= today, 'Date cannot be in the future'),
    description: z.string().trim().min(1, 'Enter a description').max(255, 'Use at most 255 characters'),
    merchant: z.string().trim().max(120, 'Use at most 120 characters'),
    categoryId: z.string(),
    notes: z.string().max(1000, 'Use at most 1000 characters'),
  })
}

export type TransactionFormValues = z.infer<ReturnType<typeof transactionFormSchema>>

export function emptyForm(today: string): TransactionFormValues {
  return {
    type: 'EXPENSE',
    amount: '',
    transactionDate: today,
    description: '',
    merchant: '',
    categoryId: '',
    notes: '',
  }
}

export function formFromTransaction(t: Transaction): TransactionFormValues {
  return {
    type: t.type,
    amount: t.amount.amount,
    transactionDate: t.transactionDate,
    description: t.description,
    merchant: t.merchant ?? '',
    categoryId: t.category?.id ?? '',
    notes: t.notes ?? '',
  }
}

export function toRequest(values: TransactionFormValues): TransactionRequest {
  return {
    type: values.type,
    amount: { amount: values.amount.trim(), currency: 'INR' },
    transactionDate: values.transactionDate,
    description: values.description.trim(),
    merchant: values.merchant.trim() || undefined,
    categoryId: values.categoryId || undefined,
    notes: values.notes.trim() || undefined,
  }
}

/** Maps an API field path (e.g. "amount.currency") onto a form field. */
export function formFieldFor(apiField: string): keyof TransactionFormValues | null {
  const head = apiField.split('.')[0] ?? ''
  const fields: (keyof TransactionFormValues)[] = [
    'type',
    'amount',
    'transactionDate',
    'description',
    'merchant',
    'categoryId',
    'notes',
  ]
  return fields.includes(head as keyof TransactionFormValues) ? (head as keyof TransactionFormValues) : null
}
