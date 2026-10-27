import { describe, expect, it } from 'vitest'
import { chatReducer, initialChatState, type ChatAction, type ChatState } from './useAgentChat'

function run(...actions: ChatAction[]): ChatState[] {
  const states: ChatState[] = []
  actions.reduce((state, action) => {
    const next = chatReducer(state, action)
    states.push(next)
    return next
  }, initialChatState)
  return states
}

const started = (callId: string, label = 'Comparing months'): ChatAction => ({
  type: 'tool_started',
  callId,
  tool: 'compareMonthlySpending',
  label,
})

describe('chatReducer', () => {
  it('runs idle → thinking → tool_running → answering → tool_running → answering → done for two tools', () => {
    const states = run(
      { type: 'start' },
      started('a', 'Checking your month'),
      { type: 'tool_finished', callId: 'a', ok: true },
      started('b', 'Comparing months'),
      { type: 'tool_finished', callId: 'b', ok: false },
      { type: 'final' },
    )

    expect(states.map((s) => s.phase.kind)).toEqual([
      'thinking',
      'tool_running',
      'answering',
      'tool_running',
      'answering',
      'done',
    ])
    expect(states[3]?.phase).toEqual({
      kind: 'tool_running',
      tool: 'compareMonthlySpending',
      label: 'Comparing months',
    })
    expect(states.at(-1)?.steps).toEqual([
      { callId: 'a', tool: 'compareMonthlySpending', label: 'Checking your month', ok: true },
      { callId: 'b', tool: 'compareMonthlySpending', label: 'Comparing months', ok: false },
    ])
  })

  it('goes straight from thinking to done when the model answers without tools', () => {
    expect(run({ type: 'start' }, { type: 'final' }).map((s) => s.phase.kind)).toEqual(['thinking', 'done'])
  })

  it('ends in error from any active phase, keeping the steps so far', () => {
    const states = run({ type: 'start' }, started('a'), { type: 'error', message: 'Dropped' })
    expect(states.at(-1)).toEqual({
      phase: { kind: 'error', message: 'Dropped' },
      steps: [{ callId: 'a', tool: 'compareMonthlySpending', label: 'Comparing months', ok: null }],
    })
  })

  it('ignores stream events that arrive when no turn is running', () => {
    expect(run(started('a'), { type: 'final' }, { type: 'error', message: 'x' }).at(-1)).toBe(
      initialChatState,
    )
    const done = run({ type: 'start' }, { type: 'final' }).at(-1)!
    expect(chatReducer(done, started('late'))).toBe(done)
    expect(chatReducer(done, { type: 'tool_finished', callId: 'late', ok: true })).toBe(done)
  })

  it('a new turn starts clean from done or error', () => {
    const failed = run({ type: 'start' }, started('a'), { type: 'error', message: 'x' }).at(-1)!
    expect(chatReducer(failed, { type: 'start' })).toEqual({ phase: { kind: 'thinking' }, steps: [] })
  })
})
