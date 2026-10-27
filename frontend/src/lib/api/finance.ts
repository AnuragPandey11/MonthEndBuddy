import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type {
  AffordabilityResult,
  Alert,
  Budget,
  BudgetStatusReport,
  CategoryBreakdown,
  CategoryRule,
  Goal,
  MonthlySummary,
  MonthReview,
  MoneyDto,
  PeriodComparison,
  SavingsProjection,
  Settings,
  TrendPoint,
} from './types'

/*
 * Query keys follow architecture §14: one key per endpoint, so TanStack Query caches each separately and the
 * dashboard calls them in parallel (there is no composite dashboard endpoint).
 */

export function useSummary(month: string) {
  return useQuery({
    queryKey: ['analytics', 'summary', month],
    queryFn: ({ signal }) => api<MonthlySummary>(`/api/v1/analytics/summary?month=${month}`, { signal }),
  })
}

export function useCategoryBreakdown(month: string, from: string, to: string) {
  return useQuery({
    queryKey: ['analytics', 'categories', month],
    queryFn: ({ signal }) =>
      api<CategoryBreakdown>(`/api/v1/analytics/categories?from=${from}&to=${to}`, { signal }),
  })
}

export function useTrends(months: number) {
  return useQuery({
    queryKey: ['analytics', 'trends', months],
    queryFn: ({ signal }) =>
      api<{ points: TrendPoint[] }>(`/api/v1/analytics/trends?months=${months}`, { signal }),
  })
}

export function useComparison(current: string, previous: string) {
  return useQuery({
    queryKey: ['analytics', 'compare', current, previous],
    queryFn: ({ signal }) =>
      api<PeriodComparison>(`/api/v1/analytics/compare?current=${current}&previous=${previous}`, { signal }),
  })
}

export function useBudgetStatus(month: string) {
  return useQuery({
    queryKey: ['budgets', 'status', month],
    queryFn: ({ signal }) => api<BudgetStatusReport>(`/api/v1/budgets/status?month=${month}`, { signal }),
  })
}

export function useBudgets() {
  return useQuery({
    queryKey: ['budgets', 'list'],
    queryFn: ({ signal }) => api<Budget[]>('/api/v1/budgets', { signal }),
  })
}

export function useGoals() {
  return useQuery({ queryKey: ['goals'], queryFn: ({ signal }) => api<Goal[]>('/api/v1/goals', { signal }) })
}

export function useAlerts(month?: string) {
  return useQuery({
    queryKey: ['alerts', month ?? 'all'],
    queryFn: ({ signal }) => api<Alert[]>(`/api/v1/alerts${month ? `?month=${month}` : ''}`, { signal }),
  })
}

export function useSettings() {
  return useQuery({
    queryKey: ['settings'],
    queryFn: ({ signal }) => api<Settings>('/api/v1/me/settings', { signal }),
  })
}

export function useCategoryRules() {
  return useQuery({
    queryKey: ['category-rules'],
    queryFn: ({ signal }) => api<CategoryRule[]>('/api/v1/category-rules', { signal }),
  })
}

/** Everything derived from transactions, budgets, goals or settings (the frontend's cache-generation bump). */
export function useInvalidateFinance() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all(
      [
        ['transactions'],
        ['analytics'],
        ['budgets'],
        ['goals'],
        ['alerts'],
        ['settings'],
        ['month-review'],
      ].map((queryKey) => queryClient.invalidateQueries({ queryKey })),
    )
}

export function useResolveAlert() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: 'DISMISSED' | 'EXPECTED' }) =>
      api<Alert>(`/api/v1/alerts/${id}`, { method: 'PATCH', body: { status } }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['alerts'] }),
  })
}

/**
 * A what-if projection. It persists nothing (architecture §9.3), so it is a query, cached per goal and amount, even
 * though the endpoint is a POST. The previous result stays visible while a new amount is projected.
 */
export function useGoalProjection(goalId: string, monthlyContribution: string) {
  return useQuery({
    queryKey: ['goals', goalId, 'projection', monthlyContribution],
    queryFn: ({ signal }) =>
      api<SavingsProjection>(`/api/v1/goals/${goalId}/projection`, {
        method: 'POST',
        body: monthlyContribution
          ? { monthlyContribution: { amount: monthlyContribution, currency: 'INR' } }
          : {},
        signal,
      }),
    placeholderData: (previous) => previous,
  })
}

export function usePurchaseSimulation() {
  return useMutation({
    mutationFn: (body: { purchaseAmount: MoneyDto; currentSavingsOverride?: MoneyDto }) =>
      api<AffordabilityResult>('/api/v1/simulations/purchase', { method: 'POST', body }),
  })
}

/** Last month's review (architecture §9.6): whether it is due, and what the month kept. */
export function useMonthReview() {
  return useQuery({
    queryKey: ['month-review'],
    queryFn: ({ signal }) => api<MonthReview>('/api/v1/month-review', { signal }),
  })
}

export interface MonthReviewRequest {
  month: string
  addToSavings?: MoneyDto
  goalContributions?: { goalId: string; amount: MoneyDto }[]
}

/** Records last month's review once; the API adds to savings and goals in the same transaction. */
export function useCompleteMonthReview() {
  const queryClient = useQueryClient()
  const invalidate = useInvalidateFinance()
  return useMutation({
    mutationFn: (body: MonthReviewRequest) =>
      api<MonthReview>('/api/v1/month-review', { method: 'POST', body }),
    onSuccess: (review) => {
      queryClient.setQueryData(['month-review'], review)
      void invalidate()
    },
  })
}
