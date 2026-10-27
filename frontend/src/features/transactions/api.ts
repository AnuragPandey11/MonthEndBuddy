import { useInfiniteQuery, useMutation, useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api/client'
import { useInvalidateFinance } from '../../lib/api/finance'
import type {
  Category,
  Transaction,
  TransactionPage,
  TransactionRequest,
  TransactionType,
  UpdateTransactionRequest,
} from '../../lib/api/types'

export interface TransactionFilters {
  q?: string
  type?: TransactionType
  categoryId?: string
  from?: string
  to?: string
}

export const PAGE_SIZE = 50

function toQuery(filters: TransactionFilters, cursor?: string): string {
  const params = new URLSearchParams({ limit: String(PAGE_SIZE) })
  for (const [key, value] of Object.entries(filters)) {
    if (value) params.set(key, value)
  }
  if (cursor) params.set('cursor', cursor)
  return params.toString()
}

export function useTransactions(filters: TransactionFilters) {
  return useInfiniteQuery({
    queryKey: ['transactions', filters],
    queryFn: ({ pageParam, signal }) =>
      api<TransactionPage>(`/api/v1/transactions?${toQuery(filters, pageParam)}`, { signal }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })
}

export function useCategories() {
  return useQuery({
    queryKey: ['categories'],
    queryFn: ({ signal }) => api<Category[]>('/api/v1/categories', { signal }),
    staleTime: 10 * 60_000,
  })
}

/**
 * After any transaction write, everything derived from transactions is stale (architecture §14): the frontend
 * counterpart of the backend's cache-generation bump.
 */
function useInvalidateTransactionData() {
  return useInvalidateFinance()
}

export function useCreateTransaction() {
  const invalidate = useInvalidateTransactionData()
  return useMutation({
    mutationFn: (body: TransactionRequest) =>
      api<Transaction>('/api/v1/transactions', { method: 'POST', body }),
    onSuccess: invalidate,
  })
}

export function useUpdateTransaction() {
  const invalidate = useInvalidateTransactionData()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateTransactionRequest }) =>
      api<Transaction>(`/api/v1/transactions/${id}`, { method: 'PUT', body }),
    onSuccess: invalidate,
  })
}

export function useDeleteTransaction() {
  const invalidate = useInvalidateTransactionData()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/v1/transactions/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  })
}
