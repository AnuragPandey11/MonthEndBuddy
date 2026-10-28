import { useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { motion } from 'motion/react'
import { AlertTriangle, Bot, Clock, Plus, Send, Trash2 } from 'lucide-react'
import { AssumptionChips } from '../../components/AssumptionChips'
import { AuroraBackground } from '../../components/aurora/AuroraBackground'
import { Button } from '../../components/Button'
import { TextArea } from '../../components/Field'
import { ErrorState, Skeleton } from '../../components/States'
import { enter } from '../../lib/motion'
import { useConversation, useConversations, useDeleteConversation } from './api'
import { EvidenceCard } from './EvidenceCards'
import { Markdown } from './Markdown'
import { ProposedActions } from './ProposedActions'
import { QuickInsights } from './QuickInsights'
import { ToolStatus } from './ToolStatus'
import { chatErrorMessage, useAgentChat } from './useAgentChat'
import type { AgentResponse, ConversationView } from './types'

const SUGGESTIONS = [
  'Where did most of my salary go this month?',
  'How does this month compare with last month?',
  'Can I afford a ₹60,000 phone this month?',
  'Am I on track for my goals?',
  'Anything unusual in my spending this month?',
]

const MAX_MESSAGE = 2000

/** One question and its answer. {@code asOf} is set for turns re-rendered from a stored conversation. */
interface Turn {
  key: string
  question: string
  response: AgentResponse
  asOf?: string
}

/**
 * The AI copilot (architecture §10.6, §14). The answer is explanation; the figures are the evidence cards, drawn
 * from the tool results the backend returned. Every change it proposes opens a normal form for the user to save.
 */
export function CopilotPage() {
  const queryClient = useQueryClient()
  const conversations = useConversations()
  const chat = useAgentChat()
  const remove = useDeleteConversation()
  const [opened, setOpened] = useState<string | null>(null)
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [live, setLive] = useState<Turn[]>([])
  const [draft, setDraft] = useState('')
  const [pendingQuestion, setPendingQuestion] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  /** Bumped whenever the view changes, so a turn the user moved away from cannot write into the new view. */
  const view = useRef(0)
  const stored = useConversation(opened)

  const storedTurns = useMemo(() => (stored.data ? toTurns(stored.data) : []), [stored.data])
  const turns = [...(opened ? storedTurns : []), ...live]

  function leave() {
    view.current++
    chat.detach()
    setPendingQuestion(null)
  }

  function startNew() {
    leave()
    setOpened(null)
    setConversationId(null)
    setLive([])
    setError(null)
  }

  function open(id: string) {
    leave()
    // A snapshot is never refetched in the background, but a turn may have finished since it was loaded (one that was
    // interrupted on this page, say), so opening a conversation always reloads it.
    void queryClient.invalidateQueries({ queryKey: ['agent', 'conversation', id] })
    setOpened(id)
    setConversationId(id)
    setLive([])
    setError(null)
  }

  function ask(question: string) {
    const message = question.trim()
    if (!message || chat.pending) return
    const turn = view.current
    setError(null)
    setPendingQuestion(message)
    setDraft('')
    chat
      .send({ conversationId, message })
      .then(
        (response) => {
          if (view.current !== turn) return
          setConversationId(response.conversationId)
          setLive((turns) => [...turns, { key: response.messageId, question: message, response }])
        },
        (e: unknown) => {
          if (view.current !== turn) return
          setDraft(message)
          setError(chatErrorMessage(e))
        },
      )
      .finally(() => {
        if (view.current === turn) setPendingQuestion(null)
      })
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    ask(draft)
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      ask(draft)
    }
  }

  const lastKey = turns.at(-1)?.key

  return (
    <section className="grid grid-cols-1 gap-5 lg:grid-cols-[15rem_minmax(0,1fr)]">
      <aside className="order-2 flex flex-col gap-2 lg:order-1" aria-label="Conversations">
        <Button variant="secondary" onClick={startNew}>
          <Plus className="size-4" aria-hidden /> New conversation
        </Button>
        {conversations.isPending ? (
          <Skeleton className="h-24" />
        ) : (
          <ul className="flex flex-col gap-1">
            {(conversations.data ?? []).map((c) => (
              <li key={c.id} className="group flex items-center gap-1">
                <button
                  type="button"
                  onClick={() => open(c.id)}
                  aria-current={c.id === conversationId || undefined}
                  className="min-w-0 flex-1 truncate rounded-md px-2 py-1.5 text-left text-sm text-muted hover:bg-elevated hover:text-ink aria-[current=true]:bg-elevated aria-[current=true]:text-ink"
                >
                  {c.title}
                </button>
                <button
                  type="button"
                  aria-label={`Delete conversation ${c.title}`}
                  onClick={() => {
                    if (c.id === conversationId) startNew()
                    remove.mutate(c.id)
                  }}
                  className="rounded p-1 text-faint opacity-60 hover:text-negative group-hover:opacity-100"
                >
                  <Trash2 className="size-3.5" aria-hidden />
                </button>
              </li>
            ))}
          </ul>
        )}
      </aside>

      <div className="order-1 flex min-w-0 flex-col gap-4 lg:order-2">
        <header className="stripes flex items-center gap-4 rounded-card border border-line/70 bg-surface p-5 shadow-card">
          {/* The aurora glows behind the robot tile only; the heading and text sit on the plain card. */}
          <span className="relative inline-flex size-16 shrink-0 items-center justify-center overflow-hidden rounded-2xl">
            <AuroraBackground />
            <img src="/logo-mark.png" alt="" width={52} height={52} className="relative size-13" />
          </span>
          <div className="min-w-0">
            <h1 className="flex items-center gap-2 text-title">
              <Bot className="size-5" aria-hidden /> Copilot
            </h1>
            <p className="text-sm text-muted">
              Ask about your spending, budgets and goals. Every figure comes from your own data.
            </p>
          </div>
        </header>

        {opened && stored.isPending && <Skeleton className="h-32" />}
        {opened && stored.isError && (
          <ErrorState message="We couldn't load this conversation." onRetry={() => void stored.refetch()} />
        )}

        {turns.length === 0 && !pendingQuestion && !(opened && stored.isPending) && (
          <div className="flex flex-col gap-2">
            <p className="text-sm text-muted">Try asking:</p>
            <div className="flex flex-wrap gap-2">
              {SUGGESTIONS.map((s) => (
                <Button key={s} variant="secondary" className="bg-surface text-xs" onClick={() => ask(s)}>
                  {s}
                </Button>
              ))}
            </div>
          </div>
        )}

        <ol className="flex flex-col gap-6" aria-label="Conversation">
          {/* Each exchange slides in as it arrives. */}
          {turns.map((turn) => (
            <motion.li
              key={turn.key}
              className="flex flex-col gap-3"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={enter}
            >
              <Question text={turn.question} />
              <Answer
                response={turn.response}
                asOf={turn.asOf}
                onReply={turn.key === lastKey ? ask : undefined}
              />
            </motion.li>
          ))}
          {pendingQuestion && (
            <motion.li
              className="flex flex-col gap-3"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={enter}
            >
              <Question text={pendingQuestion} />
              <ToolStatus phase={chat.phase} steps={chat.steps} />
            </motion.li>
          )}
        </ol>

        {error && (
          <p role="alert" className="text-sm text-negative">
            {error}
          </p>
        )}

        <form onSubmit={onSubmit} className="flex flex-col gap-2">
          <label htmlFor="copilot-message" className="sr-only">
            Ask the copilot
          </label>
          <div className="flex items-end gap-2">
            <TextArea
              id="copilot-message"
              rows={2}
              maxLength={MAX_MESSAGE}
              placeholder="Ask about your money…"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={onKeyDown}
              className="resize-none"
            />
            <Button type="submit" loading={chat.pending} disabled={!draft.trim()} aria-label="Send">
              <Send className="size-4" aria-hidden />
            </Button>
          </div>
          <p className="text-xs text-faint">
            The copilot explains your own data. It is not financial advice, and it never changes anything
            without you.
          </p>
        </form>
      </div>
    </section>
  )
}

function Question({ text }: { text: string }) {
  return (
    <p className="max-w-[85%] self-end rounded-[1.25rem] rounded-br-md bg-accent-strong px-4 py-2.5 text-sm whitespace-pre-wrap text-white">
      {text}
    </p>
  )
}

const NOTICES: Partial<Record<AgentResponse['status'], string>> = {
  PARTIAL: 'This answer is incomplete: it took too long.',
  LIMIT_REACHED: 'The copilot stopped at its step limit before finishing.',
}

function Answer({
  response: r,
  asOf,
  onReply,
}: {
  response: AgentResponse
  asOf?: string
  onReply?: (text: string) => void
}) {
  if (r.status === 'PROVIDER_UNAVAILABLE') {
    return (
      <div className="flex flex-col gap-3 rounded-card border border-line p-4">
        <p className="flex items-center gap-2 text-sm font-medium">
          <AlertTriangle className="size-4 text-warning" aria-hidden /> The copilot is unavailable right now
        </p>
        <p className="text-sm text-muted">{r.answer}</p>
        <QuickInsights />
      </div>
    )
  }
  const notice = NOTICES[r.status]
  return (
    <div className="flex flex-col gap-3">
      {asOf && (
        <p className="inline-flex items-center gap-1 text-caption text-faint">
          <Clock className="size-3" aria-hidden /> Saved answer, figures as of{' '}
          {new Date(asOf).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}
        </p>
      )}
      {notice && (
        <p className="flex items-center gap-2 text-sm text-warning">
          <AlertTriangle className="size-4" aria-hidden /> {notice}
        </p>
      )}
      <Markdown text={r.answer} unverified={r.unverifiedAmounts} />
      {r.unverifiedAmounts.length > 0 && (
        <p className="text-xs text-warning">
          Highlighted amounts are estimates, not from your data. The cards below show your real figures.
        </p>
      )}
      {r.status === 'NEEDS_CLARIFICATION' && r.clarification && r.clarification.options.length > 0 && (
        <div className="flex flex-wrap gap-2" role="group" aria-label="Quick replies">
          {r.clarification.options.map((option) => (
            <Button
              key={option}
              variant="secondary"
              className="px-3 py-1 text-xs"
              disabled={!onReply}
              onClick={() => onReply?.(option)}
            >
              {option}
            </Button>
          ))}
        </div>
      )}
      {r.evidence.length > 0 && (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {r.evidence.map((e) => (
            <EvidenceCard key={e.toolCallId} evidence={e} asOf={asOf} />
          ))}
        </div>
      )}
      {r.assumptions.length > 0 && <AssumptionChips assumptions={r.assumptions} />}
      <ProposedActions actions={r.proposedActions} />
    </div>
  )
}

/** Pairs each stored question with the answer that followed it; an unanswered question is left out. */
function toTurns(view: ConversationView): Turn[] {
  const turns: Turn[] = []
  view.messages.forEach((m, i) => {
    const next = view.messages[i + 1]
    if (m.role !== 'USER' || next?.role !== 'ASSISTANT') return
    const meta = next.metadata
    turns.push({
      key: next.id,
      question: m.content,
      asOf: next.createdAt,
      response: {
        conversationId: view.id,
        messageId: next.id,
        status: next.status ?? 'COMPLETE',
        answer: next.content,
        evidence: meta?.evidence ?? [],
        assumptions: meta?.assumptions ?? [],
        clarification: meta?.clarification ?? null,
        proposedActions: meta?.proposedActions ?? [],
        unverifiedAmounts: meta?.unverifiedAmounts ?? [],
        model: next.model,
        promptVersion: next.promptVersion ?? '',
      },
    })
  })
  return turns
}
