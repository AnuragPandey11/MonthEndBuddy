import type { components } from './schema'

type Schemas = components['schemas']

/** Makes the listed keys required. The generated response types mark every property optional. */
type Present<T, K extends keyof T> = Omit<T, K> & { [P in K]-?: NonNullable<T[P]> }

export type MoneyDto = Present<Schemas['MoneyDto'], 'amount' | 'currency'>
export type TransactionType = Schemas['TransactionRequest']['type']
export type CategoryKind = Schemas['CreateCategoryRequest']['kind']

export type Transaction = Present<
  Omit<Schemas['TransactionView'], 'amount'>,
  'id' | 'type' | 'transactionDate' | 'description' | 'categorySource' | 'source' | 'version'
> & { amount: MoneyDto }

export interface TransactionPage {
  items: Transaction[]
  nextCursor: string | null
}

export type Category = Present<Schemas['CategoryView'], 'id' | 'name' | 'kind' | 'system'>
export type TransactionRequest = Schemas['TransactionRequest']
export type UpdateTransactionRequest = Schemas['UpdateTransactionRequest']
export type Me = Present<Schemas['MeResponse'], 'id' | 'email' | 'displayName' | 'demo'>
export type TokenResponse = Present<Schemas['TokenResponse'], 'accessToken' | 'expiresIn'>

/*
 * Phase 2 responses, written out by hand because springdoc marks every property optional and cannot say which are
 * nullable. Field names and shapes mirror the backend records (analytics, budgets, goals, imports, alerts).
 */

export interface DataCoverage {
  transactionCount: number
  firstDate: string | null
  lastDate: string | null
  monthsWithData: string[]
}

export interface MonthlySummary {
  month: string
  income: MoneyDto
  expenses: MoneyDto
  net: MoneyDto
  savingsRate: number | null
  savingsRateReason: string | null
  coverage: DataCoverage
}

export interface CategoryShare {
  categoryId: string | null
  name: string
  amount: MoneyDto
  pct: number
  count: number
}

export interface CategoryBreakdown {
  from: string
  to: string
  total: MoneyDto
  categories: CategoryShare[]
  coverage: DataCoverage
}

export interface TrendPoint {
  month: string
  income: MoneyDto
  expenses: MoneyDto
  net: MoneyDto
  hasData: boolean
}

export interface Change {
  current: MoneyDto
  previous: MoneyDto
  delta: MoneyDto
  deltaPct: number | null
  isNew: boolean
}

export interface PeriodComparison {
  current: string
  previous: string
  totals: Change
  categories: (Change & { categoryId: string | null; name: string })[]
}

export interface BudgetStatus {
  budgetId: string
  categoryId: string | null
  categoryName: string
  limit: MoneyDto
  spent: MoneyDto
  remaining: MoneyDto
  pctUsed: number
  overspent: boolean
  alertThresholds: number[]
  thresholdsCrossed: number[]
  topTransactions: Transaction[]
}

export interface BudgetStatusReport {
  month: string
  budgets: BudgetStatus[]
  coverage: DataCoverage
}

export interface Budget {
  id: string
  categoryId: string | null
  categoryName: string
  limit: MoneyDto
  alertThresholds: number[]
  version: number
}

export type GoalStatus = 'ACTIVE' | 'ACHIEVED' | 'ARCHIVED'

export interface Goal {
  id: string
  name: string
  target: MoneyDto
  current: MoneyDto
  remaining: MoneyDto
  progressPct: number
  targetDate: string
  status: GoalStatus
  priority: number
  version: number
}

export type AssumptionLabel = 'DERIVED' | 'USER_STATED' | 'USER_PROFILE' | 'DEFAULT_ASSUMPTION'

export interface Assumption {
  name: string
  value: string
  label: AssumptionLabel
}

export type ProjectionStatus = 'ON_TRACK' | 'BEHIND' | 'ACHIEVED' | 'DEADLINE_PASSED' | 'NEVER'

export interface SavingsProjection {
  target: MoneyDto
  current: MoneyDto
  remaining: MoneyDto
  targetDate: string
  monthsRemaining: number
  requiredMonthly: MoneyDto | null
  monthlyContribution: MoneyDto | null
  status: ProjectionStatus
  projectedCompletion: string | null
  series: { month: string; balance: MoneyDto }[]
  assumptions: Assumption[]
}

export type AffordabilityVerdict =
  'AFFORDABLE' | 'AFFORDABLE_WITH_TRADEOFFS' | 'NOT_AFFORDABLE' | 'INSUFFICIENT_DATA'

export interface AffordabilityResult {
  verdict: AffordabilityVerdict
  purchaseAmount: MoneyDto
  purchaseMonth: string
  /** The total, goal money included. */
  currentSavings: MoneyDto | null
  /** What active goals already hold: part of `currentSavings`, not free to spend. */
  savingsSetAside: MoneyDto
  /** `currentSavings` minus `savingsSetAside`; negative when goals hold more than the savings. */
  availableSavings: MoneyDto | null
  /** What is left of `availableSavings` after the purchase. */
  savingsAfterPurchase: MoneyDto | null
  averageMonthlyIncome: MoneyDto | null
  averageMonthlyExpenses: MoneyDto | null
  monthlySurplus: MoneyDto | null
  emergencyBuffer: MoneyDto | null
  goalNeed: MoneyDto
  recoveryMonths: number | null
  goalImpacts: {
    goalId: string
    name: string
    requiredMonthly: MoneyDto | null
    delayMonths: number | null
  }[]
  assumptions: Assumption[]
  dataQuality: { code: string; message: string }[]
  monthsOfData: number
}

export interface Alert {
  id: string
  ruleId: 'LARGE_VS_CATEGORY' | 'CATEGORY_SPIKE' | 'ABSOLUTE_THRESHOLD'
  subjectType: 'TRANSACTION' | 'CATEGORY'
  subjectId: string
  subjectLabel: string
  periodStart: string
  severity: 'INFO' | 'WARNING'
  status: 'OPEN' | 'DISMISSED' | 'EXPECTED'
  message: string
  createdAt: string
}

/** An in-app notification (P5.5), created by the backend from a budget threshold, an anomaly or an import. */
export interface Notification {
  id: string
  type: 'BUDGET_THRESHOLD' | 'ANOMALY' | 'IMPORT_COMPLETED'
  title: string
  body: string
  /** An app path, e.g. `/budgets`. */
  link: string
  createdAt: string
  readAt: string | null
}

export interface FieldViolationView {
  field: string
  message: string
}

export interface ImportJob {
  id: string
  filename: string
  status: 'VALIDATED' | 'COMMITTED' | 'DISCARDED' | 'FAILED'
  dateFormat: 'ISO_YYYY_MM_DD' | 'DD_MM_YYYY'
  totals: { total: number; valid: number; invalid: number; duplicate: number }
  sampleErrors: { rowNumber: number; field: string; message: string }[]
  result: ImportResult | null
  failureReason: string | null
}

export interface ImportResult {
  jobId: string
  imported: number
  skippedDuplicates: number
  skippedInvalid: number
}

export interface ImportRow {
  id: string
  rowNumber: number
  status: 'VALID' | 'INVALID' | 'DUPLICATE'
  transactionDate: string | null
  type: TransactionType | null
  amount: MoneyDto | null
  description: string | null
  merchant: string | null
  categoryName: string | null
  errors: FieldViolationView[]
  warnings: FieldViolationView[]
}

export interface Settings {
  baseCurrency: string
  timezone: string
  currentSavings: MoneyDto | null
  currentSavingsUpdatedAt: string | null
  emergencyBufferMonths: number
  largeTxnThreshold: MoneyDto
  anomalyMultiplier: number
  categorySpikePct: number
}

export interface CategoryRule {
  id: string
  matchType: 'CONTAINS' | 'STARTS_WITH' | 'EQUALS'
  pattern: string
  category: { id: string; name: string | null }
  priority: number
  version: number
}

export type MonthReviewStatus = 'DUE' | 'NOTHING_KEPT' | 'DONE' | 'SKIPPED'

/** Last month's review (architecture §9.6). `kept` is the month's net cash flow, computed by the API. */
export interface MonthReview {
  month: string
  kept: MoneyDto
  transactionCount: number
  status: MonthReviewStatus
  /** Savings today, or null when unknown: then nothing can be added to it. */
  currentSavings: MoneyDto | null
  currentSavingsUpdatedAt: string | null
  /** Savings were changed after the month ended, so they may already include what it kept. */
  savingsUpdatedSinceMonthEnd: boolean
  addedToSavings: MoneyDto | null
  setAsideForGoals: MoneyDto | null
}
