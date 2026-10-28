import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { emptyCoverage, inr, renderPage } from '../../test/render'
import { currentMonth } from '../../lib/months'
import { todayIso } from '../../lib/dates'
import { formatMoney } from '../../lib/money'
import { CopilotPage } from './CopilotPage'
import { expectNoSeriousA11yViolations } from '../../test/axe'
import type { AgentResponse, AgentStatus, Evidence } from './types'

const month = currentMonth(todayIso())

const summaryEvidence: Evidence = {
  toolCallId: 'call_1',
  tool: 'getMonthlySummary',
  args: { month },
  result: {
    month,
    income: inr('95000.00'),
    expenses: inr('71250.50'),
    net: inr('23749.50'),
    savingsRate: 25,
    savingsRateReason: null,
    coverage: { transactionCount: 87, firstDate: null, lastDate: null, monthsWithData: [month] },
  },
  provenance: {
    source: 'transactions',
    period: month,
    currency: 'INR',
    asOf: '2026-10-27T10:00:00Z',
    transactionCount: 87,
  },
  truncated: false,
}

function response(overrides: Partial<AgentResponse> = {}): AgentResponse {
  return {
    conversationId: 'c1',
    messageId: 'm1',
    status: 'COMPLETE',
    answer: 'You kept **₹23,749.50** this month.',
    evidence: [summaryEvidence],
    assumptions: [],
    clarification: null,
    proposedActions: [],
    unverifiedAmounts: [],
    model: 'openai/gpt-oss-120b',
    promptVersion: 'system-v1',
    ...overrides,
  }
}

const analytics = {
  'GET /api/v1/analytics/summary': summaryEvidence.result,
  'GET /api/v1/budgets/status': { month, coverage: emptyCoverage, budgets: [] },
  'GET /api/v1/alerts': [],
  'GET /api/v1/budgets': [],
}

function renderCopilot(reply: AgentResponse | ((body: { message: string }) => AgentResponse), extra = {}) {
  return renderPage(<CopilotPage />, {
    'GET /api/v1/agent/conversations': [],
    'POST /api/v1/agent/chat': (_: URL, init: RequestInit | undefined) => {
      const body = JSON.parse(String(init?.body))
      return typeof reply === 'function' ? reply(body) : reply
    },
    ...analytics,
    ...extra,
  })
}

async function askSomething(text = 'How did this month go?') {
  await userEvent.type(screen.getByLabelText('Ask the copilot'), text)
  await userEvent.click(screen.getByRole('button', { name: 'Send' }))
}

/** An event stream the test feeds one event at a time, as the backend would while a turn runs. */
function sseResponse() {
  const encoder = new TextEncoder()
  let controller!: ReadableStreamDefaultController<Uint8Array>
  const stream = new ReadableStream<Uint8Array>({ start: (c) => (controller = c) })
  return {
    response: new Response(stream, { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
    emit: (event: string, data: unknown) =>
      controller.enqueue(encoder.encode(`event:${event}\ndata:${JSON.stringify(data)}\n\n`)),
    end: () => controller.close(),
  }
}

describe('CopilotPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows the answer with an evidence card built from the tool result', async () => {
    renderCopilot(response())
    await askSomething()

    const card = await screen.findByRole('article', { name: 'Monthly summary' })
    expect(within(card).getByText('₹95,000.00')).toBeInTheDocument()
    expect(within(card).getByText('25%')).toBeInTheDocument()
    expect(within(card).getByText(/87 transactions/)).toBeInTheDocument()
    expect(screen.getByText('₹23,749.50', { selector: 'strong' })).toBeInTheDocument()
    expect(screen.getByText(/not financial advice/)).toBeInTheDocument()
  })

  it('has no serious accessibility violations, empty and with an answer', async () => {
    renderCopilot(response())
    await screen.findByRole('button', { name: /new conversation/i })
    await expectNoSeriousA11yViolations()
    await askSomething()
    await screen.findByRole('article', { name: 'Monthly summary' })
    await expectNoSeriousA11yViolations()
  })

  it('evidence cards show only values present in the result', async () => {
    renderCopilot(response())
    await askSomething()

    const card = await screen.findByRole('article', { name: 'Monthly summary' })
    const shown = card.textContent?.match(/₹[\d,]+\.\d{2}/g) ?? []
    const r = summaryEvidence.result as Record<
      'income' | 'expenses' | 'net',
      { amount: string; currency: string }
    >
    const inResult = [r.income, r.expenses, r.net].map(formatMoney)
    expect(shown.length).toBeGreaterThan(0)
    shown.forEach((amount) => expect(inResult).toContain(amount))
  })

  it('uses the generic card for a tool it does not know', async () => {
    renderCopilot(
      response({
        evidence: [
          {
            ...summaryEvidence,
            tool: 'futureTool',
            result: { headline: 'Rent is due', total: inr('25000.00') },
          },
        ],
      }),
    )
    await askSomething()

    const card = await screen.findByRole('article', { name: 'Data used' })
    expect(within(card).getByText('Rent is due')).toBeInTheDocument()
    expect(within(card).getByText('₹25,000.00')).toBeInTheDocument()
  })

  it.each<[AgentStatus, Partial<AgentResponse>, RegExp]>([
    ['PARTIAL', { answer: 'This took too long to answer completely.' }, /incomplete: it took too long/],
    ['LIMIT_REACHED', { answer: 'I couldn’t finish.' }, /stopped at its step limit/],
  ])('renders %s with its own notice and the evidence gathered', async (status, overrides, notice) => {
    renderCopilot(response({ status, ...overrides }))
    await askSomething()

    expect(await screen.findByText(notice)).toBeInTheDocument()
    expect(screen.getByRole('article', { name: 'Monthly summary' })).toBeInTheDocument()
  })

  it('offers clarification options as quick replies that send the next message', async () => {
    const sent: string[] = []
    renderCopilot((body) => {
      sent.push(body.message)
      return sent.length === 1
        ? response({
            status: 'NEEDS_CLARIFICATION',
            answer: 'Which month do you mean?',
            evidence: [],
            clarification: { question: 'Which month do you mean?', options: ['September', 'October'] },
          })
        : response({ messageId: 'm2' })
    })
    await askSomething('How was that month?')

    const replies = await screen.findByRole('group', { name: 'Quick replies' })
    await userEvent.click(within(replies).getByRole('button', { name: 'October' }))

    expect(await screen.findByRole('article', { name: 'Monthly summary' })).toBeInTheDocument()
    expect(sent).toEqual(['How was that month?', 'October'])
  })

  it('falls back to quick insights from the dashboard data when the provider is unavailable', async () => {
    renderCopilot(
      response({
        status: 'PROVIDER_UNAVAILABLE',
        answer: 'The copilot can’t answer right now.',
        evidence: [],
      }),
    )
    await askSomething()

    expect(await screen.findByText('The copilot is unavailable right now')).toBeInTheDocument()
    expect(await screen.findByText(/₹95,000.00 in, ₹71,250.50 out/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open the dashboard' })).toBeInTheDocument()
  })

  it('marks amounts the grounding check could not find in the data', async () => {
    renderCopilot(response({ answer: 'Rent was ₹99,999.', unverifiedAmounts: ['₹99,999'] }))
    await askSomething()

    const mark = await screen.findByText('₹99,999', { selector: 'mark', exact: false })
    expect(mark).toHaveAttribute('title', expect.stringMatching(/not in your data/))
    expect(screen.getByText(/Highlighted amounts are estimates/)).toBeInTheDocument()
  })

  it('never renders markup or links from the answer', async () => {
    renderCopilot(
      response({
        answer:
          'Hi <img src=x onerror="alert(1)"> see [your bank](https://evil.example) and <script>x</script>',
      }),
    )
    await askSomething()

    expect(await screen.findByText(/<img src=x onerror="alert\(1\)">/)).toBeInTheDocument()
    // Scoped to the conversation: the page header carries the logo image.
    expect(screen.getByRole('list', { name: 'Conversation' }).querySelector('img, script')).toBeNull()
    expect(screen.queryByRole('link', { name: 'your bank' })).toBeNull()
    expect(screen.getByText('your bank')).toBeInTheDocument()
  })

  it('reopens a stored conversation from its snapshot, marked as of when it was answered', async () => {
    const fetchMock = renderPage(<CopilotPage />, {
      'GET /api/v1/agent/conversations': [
        {
          id: 'c9',
          title: 'Where did my money go?',
          createdAt: '2026-10-20T09:00:00Z',
          updatedAt: '2026-10-20T09:00:00Z',
        },
      ],
      'GET /api/v1/agent/conversations/c9': {
        id: 'c9',
        title: 'Where did my money go?',
        createdAt: '2026-10-20T09:00:00Z',
        updatedAt: '2026-10-20T09:00:00Z',
        messages: [
          {
            id: 'u1',
            role: 'USER',
            content: 'Where did my money go?',
            status: null,
            metadata: null,
            model: null,
            promptVersion: null,
            createdAt: '2026-10-20T09:00:00Z',
          },
          {
            id: 'a1',
            role: 'ASSISTANT',
            content: 'Mostly rent.',
            status: 'COMPLETE',
            metadata: {
              evidence: [summaryEvidence],
              assumptions: [],
              clarification: null,
              proposedActions: [],
              unverifiedAmounts: [],
            },
            model: 'openai/gpt-oss-120b',
            promptVersion: 'system-v1',
            createdAt: '2026-10-20T09:00:05Z',
          },
        ],
      },
    })
    await userEvent.click(await screen.findByRole('button', { name: 'Where did my money go?' }))

    expect(await screen.findByText('Mostly rent.')).toBeInTheDocument()
    expect(screen.getByText(/Saved answer, figures as of/)).toBeInTheDocument()
    expect(
      within(screen.getByRole('article', { name: 'Monthly summary' })).getByText(/as of/),
    ).toBeInTheDocument()
    // Re-rendered from the snapshot: no chat request, so no tool runs again.
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/agent/chat'))).toBe(false)
  })

  it('opens a proposed budget as a pre-filled form', async () => {
    renderCopilot(
      response({
        proposedActions: [
          {
            type: 'CREATE_BUDGET',
            payload: { categoryId: 'c-dining', categoryName: 'Dining', limit: inr('8500.00') },
            label: 'Set a monthly budget for Dining',
          },
        ],
      }),
      {
        'GET /api/v1/categories': [
          { id: 'c-dining', name: 'Dining', kind: 'EXPENSE', system: true, archived: false, icon: null },
        ],
      },
    )
    await askSomething()
    await userEvent.click(await screen.findByRole('button', { name: /Set a monthly budget for Dining/ }))

    const dialog = await screen.findByRole('dialog', { name: 'Add budget' })
    expect(within(dialog).getByLabelText('Monthly limit (₹)')).toHaveValue('8500.00')
  })
  it('streams tool status while the turn runs, then shows the answer', async () => {
    const sse = sseResponse()
    const fetchMock = renderPage(<CopilotPage />, {
      'GET /api/v1/agent/conversations': [],
      'POST /api/v1/agent/chat': () => sse.response,
      ...analytics,
    })
    await askSomething()

    const status = await screen.findByRole('status')
    expect(within(status).getByText('Reading your question…')).toBeInTheDocument()
    sse.emit('tool_started', { callId: 'call_1', tool: 'getMonthlySummary', label: 'Checking your month' })
    expect(await within(status).findByText('Checking your month…')).toBeInTheDocument()
    sse.emit('tool_finished', { callId: 'call_1', tool: 'getMonthlySummary', ok: true, millis: 12 })
    expect(await within(status).findByText('Working out the answer…')).toBeInTheDocument()
    expect(within(status).getByRole('list', { name: 'Data checked' })).toHaveTextContent(
      'Checking your month',
    )

    sse.emit('final', response())
    sse.end()
    expect(await screen.findByRole('article', { name: 'Monthly summary' })).toBeInTheDocument()
    expect(screen.queryByRole('status')).toBeNull()
    const chat = fetchMock.mock.calls.find(([url]) => String(url) === '/api/v1/agent/chat')
    expect(new Headers(chat?.[1]?.headers).get('Accept')).toContain('text/event-stream')
  })

  it('shows a streamed error and gives the question back to edit', async () => {
    const sse = sseResponse()
    renderPage(<CopilotPage />, {
      'GET /api/v1/agent/conversations': [],
      'POST /api/v1/agent/chat': () => sse.response,
      ...analytics,
    })
    await askSomething('Will this fail?')
    sse.emit('error', { code: 'internal_error', message: 'Something went wrong', traceId: 't1' })
    sse.end()

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not send your question. Try again.')
    expect(screen.getByLabelText('Ask the copilot')).toHaveValue('Will this fail?')
  })

  it('says the answer was interrupted when the stream ends without one', async () => {
    const sse = sseResponse()
    renderPage(<CopilotPage />, {
      'GET /api/v1/agent/conversations': [],
      'POST /api/v1/agent/chat': () => sse.response,
      ...analytics,
    })
    await askSomething()
    sse.emit('tool_started', { callId: 'call_1', tool: 'getMonthlySummary', label: 'Checking your month' })
    sse.end()

    expect(await screen.findByRole('alert')).toHaveTextContent(/Reopen the conversation in a moment/)
  })
  it('reopening a conversation after an interrupted turn loads the answer the server stored', async () => {
    const summary = {
      id: 'c9',
      title: 'First question',
      createdAt: '2026-10-20T09:00:00Z',
      updatedAt: '2026-10-20T09:00:00Z',
    }
    const message = (id: string, role: 'USER' | 'ASSISTANT', content: string) => ({
      id,
      role,
      content,
      status: role === 'ASSISTANT' ? 'COMPLETE' : null,
      metadata: null,
      model: null,
      promptVersion: null,
      createdAt: '2026-10-20T09:00:05Z',
    })
    let stored = [message('u1', 'USER', 'First question'), message('a1', 'ASSISTANT', 'First answer.')]
    const sse = sseResponse()
    const fetchMock = renderPage(<CopilotPage />, {
      'GET /api/v1/agent/conversations': [summary],
      'GET /api/v1/agent/conversations/c9': () => ({ ...summary, messages: stored }),
      'POST /api/v1/agent/chat': () => sse.response,
      ...analytics,
    })
    await userEvent.click(await screen.findByRole('button', { name: 'First question' }))
    expect(await screen.findByText('First answer.')).toBeInTheDocument()

    await askSomething('Second question')
    sse.end()
    expect(await screen.findByRole('alert')).toHaveTextContent(/Reopen the conversation/)
    // Meanwhile the turn finished on the server and was stored.
    stored = [
      ...stored,
      message('u2', 'USER', 'Second question'),
      message('a2', 'ASSISTANT', 'Second answer.'),
    ]
    await userEvent.click(screen.getByRole('button', { name: 'First question' }))

    expect(await screen.findByText('Second answer.')).toBeInTheDocument()
    const loads = fetchMock.mock.calls.filter(([url]) => String(url) === '/api/v1/agent/conversations/c9')
    expect(loads.length).toBeGreaterThanOrEqual(2)
  })
})
