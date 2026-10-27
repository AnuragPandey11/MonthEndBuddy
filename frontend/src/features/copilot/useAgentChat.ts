import { useCallback, useEffect, useReducer, useRef } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { ApiError, apiFetch, toApiError } from '../../lib/api/client'
import { readSse } from '../../lib/sse'
import type { AgentResponse } from './types'

/*
 * One copilot turn over the streaming transport (architecture §10.7, §14). The backend reports each tool as it runs,
 * then sends the same response the JSON endpoint would. The turn keeps running on the server if the page goes away,
 * and its answer is stored, so reopening the conversation shows it.
 */

export type ChatPhase =
  | { kind: 'idle' }
  /** Waiting for the model, before any tool has run. */
  | { kind: 'thinking' }
  | { kind: 'tool_running'; tool: string; label: string }
  /** A tool finished; the model is reading the result and may call another or write the answer. */
  | { kind: 'answering' }
  | { kind: 'done' }
  | { kind: 'error'; message: string }

export interface ToolStep {
  callId: string
  tool: string
  label: string
  /** Null while the tool is still running. */
  ok: boolean | null
}

export interface ChatState {
  phase: ChatPhase
  steps: ToolStep[]
}

export type ChatAction =
  | { type: 'start' }
  | { type: 'tool_started'; callId: string; tool: string; label: string }
  | { type: 'tool_finished'; callId: string; ok: boolean }
  | { type: 'final' }
  | { type: 'error'; message: string }
  | { type: 'reset' }

export const initialChatState: ChatState = { phase: { kind: 'idle' }, steps: [] }

export function isActive(phase: ChatPhase): boolean {
  return phase.kind === 'thinking' || phase.kind === 'tool_running' || phase.kind === 'answering'
}

/**
 * `idle → thinking → (tool_running → answering)* → done | error`; `start` begins a new turn from any phase. Other events
 * that do not fit the phase are ignored.
 */
export function chatReducer(state: ChatState, action: ChatAction): ChatState {
  const active = isActive(state.phase)
  switch (action.type) {
    case 'start':
      return { phase: { kind: 'thinking' }, steps: [] }
    case 'tool_started':
      if (!active) return state
      return {
        phase: { kind: 'tool_running', tool: action.tool, label: action.label },
        steps: [...state.steps, { callId: action.callId, tool: action.tool, label: action.label, ok: null }],
      }
    case 'tool_finished':
      if (state.phase.kind !== 'tool_running') return state
      return {
        phase: { kind: 'answering' },
        steps: state.steps.map((s) => (s.callId === action.callId ? { ...s, ok: action.ok } : s)),
      }
    case 'final':
      return active ? { ...state, phase: { kind: 'done' } } : state
    case 'error':
      return active ? { ...state, phase: { kind: 'error', message: action.message } } : state
    case 'reset':
      return initialChatState
  }
}

export interface ChatRequest {
  conversationId: string | null
  message: string
}

/** Tool events and the answer for one turn. `send` resolves with the final response, or rejects with an ApiError. */
export function useAgentChat() {
  const queryClient = useQueryClient()
  const [state, dispatch] = useReducer(chatReducer, initialChatState)
  const inFlight = useRef<AbortController | null>(null)

  // Leaving the page stops reading; the turn itself finishes and is stored on the server.
  useEffect(() => () => inFlight.current?.abort(), [])

  const send = useCallback(
    async (body: ChatRequest): Promise<AgentResponse> => {
      inFlight.current?.abort()
      const controller = new AbortController()
      inFlight.current = controller
      dispatch({ type: 'start' })
      try {
        const response = await streamTurn(body, controller.signal, dispatch)
        dispatch({ type: 'final' })
        return response
      } catch (error) {
        // A turn the user moved away from (detach, or a newer send) no longer owns the state.
        if (!controller.signal.aborted) dispatch({ type: 'error', message: chatErrorMessage(error) })
        throw error
      } finally {
        if (inFlight.current === controller) inFlight.current = null
        // Also after an interrupted stream: the server stores the turn, and the list and snapshot should show it.
        void queryClient.invalidateQueries({ queryKey: ['agent', 'conversations'] })
        if (body.conversationId) {
          void queryClient.invalidateQueries({ queryKey: ['agent', 'conversation', body.conversationId] })
        }
      }
    },
    [queryClient],
  )

  /** Stops following the current turn, e.g. when the user opens another conversation. */
  const detach = useCallback(() => {
    inFlight.current?.abort()
    inFlight.current = null
    dispatch({ type: 'reset' })
  }, [])

  return { ...state, pending: isActive(state.phase), send, detach }
}

interface ToolStartedEvent {
  callId: string
  tool: string
  label: string
}

interface ToolFinishedEvent {
  callId: string
  tool: string
  ok: boolean
  millis: number
}

interface StreamErrorEvent {
  code: string
  message: string
  traceId: string | null
}

const STREAM_ACCEPT = 'text/event-stream, application/problem+json'

async function streamTurn(
  body: ChatRequest,
  signal: AbortSignal,
  dispatch: (action: ChatAction) => void,
): Promise<AgentResponse> {
  const response = await apiFetch('/api/v1/agent/chat', {
    method: 'POST',
    body,
    signal,
    accept: STREAM_ACCEPT,
  })
  if (!response.ok) throw await toApiError(response)
  // A server or proxy that answered with the whole JSON response instead of a stream.
  if (response.headers.get('Content-Type')?.includes('application/json')) {
    return (await response.json()) as AgentResponse
  }
  if (!response.body) throw interrupted()
  for await (const event of readSse(response.body)) {
    switch (event.event) {
      case 'tool_started': {
        const data = JSON.parse(event.data) as ToolStartedEvent
        dispatch({ type: 'tool_started', callId: data.callId, tool: data.tool, label: data.label })
        break
      }
      case 'tool_finished': {
        const data = JSON.parse(event.data) as ToolFinishedEvent
        dispatch({ type: 'tool_finished', callId: data.callId, ok: data.ok })
        break
      }
      case 'final':
        return JSON.parse(event.data) as AgentResponse
      case 'error': {
        const data = JSON.parse(event.data) as StreamErrorEvent
        throw new ApiError(500, data.code, data.message, [], data.traceId ?? undefined)
      }
    }
  }
  throw interrupted()
}

function interrupted(): ApiError {
  return new ApiError(
    0,
    'stream_interrupted',
    'The connection dropped before the answer arrived. Reopen the conversation in a moment to see it.',
  )
}

export function chatErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'turn_in_progress') return 'Still answering your previous question.'
    if (error.status === 429) return 'You have asked a lot of questions. Try again in a minute.'
    if (error.status === 422) return 'Questions can be up to 2,000 characters.'
    if (error.code === 'stream_interrupted' || error.code === 'turn_timeout') return error.message
    if (error.status === 503) return 'The copilot is busy. Try again in a minute.'
  }
  return 'Could not send your question. Try again.'
}
