import type { Assumption } from '../../lib/api/types'

/*
 * The copilot's response contract (architecture §10.6), written out by hand like the Phase 2 types: tool results are
 * typed per tool by the evidence cards, so here they stay `unknown` until a card narrows them.
 */

export type AgentStatus =
  'COMPLETE' | 'NEEDS_CLARIFICATION' | 'PARTIAL' | 'PROVIDER_UNAVAILABLE' | 'LIMIT_REACHED'

export interface Provenance {
  source: string
  period: string | null
  currency: string | null
  asOf: string
  transactionCount: number | null
}

export interface Evidence {
  toolCallId: string
  tool: string
  args: Record<string, unknown> | null
  result: unknown
  provenance: Provenance | null
  truncated: boolean
}

export interface Clarification {
  question: string
  options: string[]
}

export type ActionType = 'CREATE_BUDGET' | 'UPDATE_BUDGET' | 'CREATE_GOAL' | 'UPDATE_GOAL_CONTRIBUTION'

export interface ProposedAction {
  type: ActionType
  payload: Record<string, unknown>
  label: string
}

export interface AgentResponse {
  conversationId: string
  messageId: string
  status: AgentStatus
  answer: string
  evidence: Evidence[]
  assumptions: Assumption[]
  clarification: Clarification | null
  proposedActions: ProposedAction[]
  unverifiedAmounts: string[]
  model: string | null
  promptVersion: string
}

export interface ConversationSummary {
  id: string
  title: string
  createdAt: string
  updatedAt: string
}

/** What an assistant message stored, so a reopened conversation renders the same cards (architecture §10.9). */
export interface StoredTurn {
  evidence: Evidence[]
  assumptions: Assumption[]
  clarification: Clarification | null
  proposedActions: ProposedAction[]
  unverifiedAmounts: string[]
}

export interface MessageView {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  status: AgentStatus | null
  metadata: StoredTurn | null
  model: string | null
  promptVersion: string | null
  createdAt: string
}

export interface ConversationView {
  id: string
  title: string
  createdAt: string
  updatedAt: string
  messages: MessageView[]
}
