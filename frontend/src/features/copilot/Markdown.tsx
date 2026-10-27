import type { ReactNode } from 'react'

/**
 * Renders the model's answer (architecture §15: a sanitising renderer, no raw HTML). It understands only the small
 * subset the system prompt asks for: paragraphs, bullet and numbered lists, **bold**, *italic* and `code`. Output is
 * built from React elements, never from an HTML string, so markup in the answer shows up as text. Links show their
 * text only: a URL the model wrote is never made clickable. Amounts the grounding check could not find in the data
 * are highlighted with an explanation.
 */
export function Markdown({ text, unverified = [] }: { text: string; unverified?: string[] }) {
  return <div className="flex flex-col gap-2 text-sm leading-relaxed">{blocks(text, unverified)}</div>
}

type Block = { kind: 'p'; lines: string[] } | { kind: 'ul' | 'ol'; items: string[] }

const BULLET = /^\s*[-*•]\s+(.*)$/
const NUMBERED = /^\s*\d+[.)]\s+(.*)$/
const HEADING = /^\s*#{1,6}\s+(.*)$/

function blocks(text: string, unverified: string[]): ReactNode[] {
  const parsed: Block[] = []
  for (const raw of text.replace(/\r\n/g, '\n').split('\n')) {
    const line = raw.replace(HEADING, '**$1**')
    const bullet = BULLET.exec(line)
    const numbered = bullet ? null : NUMBERED.exec(line)
    const last = parsed.at(-1)
    const match = bullet ?? numbered
    if (match) {
      const kind = bullet ? 'ul' : 'ol'
      const item = match[1] ?? ''
      if (last?.kind === kind) last.items.push(item)
      else parsed.push({ kind, items: [item] })
    } else if (line.trim() === '') {
      parsed.push({ kind: 'p', lines: [] })
    } else if (last?.kind === 'p') {
      last.lines.push(line)
    } else {
      parsed.push({ kind: 'p', lines: [line] })
    }
  }
  return parsed
    .filter((b) => (b.kind === 'p' ? b.lines.length > 0 : true))
    .map((b, i) => {
      if (b.kind === 'p') {
        return (
          <p key={i}>
            {b.lines.map((l, j) => (
              <span key={j}>
                {j > 0 && <br />}
                {inline(l, unverified)}
              </span>
            ))}
          </p>
        )
      }
      const items = b.items.map((item, j) => <li key={j}>{inline(item, unverified)}</li>)
      return b.kind === 'ul' ? (
        <ul key={i} className="ml-5 list-disc space-y-1">
          {items}
        </ul>
      ) : (
        <ol key={i} className="ml-5 list-decimal space-y-1">
          {items}
        </ol>
      )
    })
}

const INLINE = /(\*\*[^*]+\*\*|__[^_]+__|`[^`]+`|\*[^*\s][^*]*\*|\[[^\]]+\]\([^)]*\))/g

function inline(text: string, unverified: string[]): ReactNode[] {
  return text.split(INLINE).map((part, i) => {
    if (/^(\*\*|__).+(\*\*|__)$/.test(part)) {
      return <strong key={i}>{highlight(part.slice(2, -2), unverified)}</strong>
    }
    if (/^`.+`$/.test(part)) {
      return (
        <code key={i} className="rounded bg-elevated px-1 text-[0.85em]">
          {part.slice(1, -1)}
        </code>
      )
    }
    if (/^\*.+\*$/.test(part)) {
      return <em key={i}>{highlight(part.slice(1, -1), unverified)}</em>
    }
    const link = /^\[([^\]]+)\]\([^)]*\)$/.exec(part)
    if (link) {
      return <span key={i}>{highlight(link[1] ?? '', unverified)}</span>
    }
    return <span key={i}>{highlight(part, unverified)}</span>
  })
}

/** Wraps each unverified amount so it is marked, in text as well as colour. */
function highlight(text: string, unverified: string[]): ReactNode {
  const amounts = unverified.filter((a) => a && text.includes(a))
  if (amounts.length === 0) return text
  const pattern = new RegExp(`(${amounts.map(escapeRegExp).join('|')})`, 'g')
  return text.split(pattern).map((part, i) =>
    amounts.includes(part) ? (
      <mark
        key={i}
        title="Estimate: this amount is not in your data"
        className="rounded bg-warning/15 px-0.5 text-ink underline decoration-warning decoration-dotted"
      >
        {part}
        <span className="sr-only"> (estimate, not from your data)</span>
      </mark>
    ) : (
      part
    ),
  )
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
