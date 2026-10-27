import { describe, expect, it } from 'vitest'
import { readSse, type SseEvent } from './sse'

/** A body that delivers exactly these chunks, as a network might split them. */
function body(...chunks: (string | Uint8Array)[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder()
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks)
        controller.enqueue(typeof chunk === 'string' ? encoder.encode(chunk) : chunk)
      controller.close()
    },
  })
}

async function collect(stream: ReadableStream<Uint8Array>): Promise<SseEvent[]> {
  const events: SseEvent[] = []
  for await (const event of readSse(stream)) events.push(event)
  return events
}

describe('readSse', () => {
  it('reads the events Spring writes, with no space after the colon', async () => {
    const events = await collect(
      body(
        'event:tool_started\ndata:{"tool":"getMonthlySummary"}\n\nevent:final\ndata:{"status":"COMPLETE"}\n\n',
      ),
    )
    expect(events).toEqual([
      { event: 'tool_started', data: '{"tool":"getMonthlySummary"}' },
      { event: 'final', data: '{"status":"COMPLETE"}' },
    ])
  })

  it('joins an event split across chunks, including mid-field and between the blank-line newlines', async () => {
    const events = await collect(
      body('eve', 'nt:fin', 'al\nda', 'ta:{"a":', '1}\n', '\nevent:error\ndata:x\n', '\n'),
    )
    expect(events).toEqual([
      { event: 'final', data: '{"a":1}' },
      { event: 'error', data: 'x' },
    ])
  })

  it('decodes a multi-byte character split between chunks', async () => {
    const bytes = new TextEncoder().encode('data:₹1,250.50\n\n')
    const rupee = bytes.indexOf(0xe2)
    const events = await collect(
      body(bytes.slice(0, rupee + 1), bytes.slice(rupee + 1, rupee + 2), bytes.slice(rupee + 2)),
    )
    expect(events).toEqual([{ event: 'message', data: '₹1,250.50' }])
  })

  it('handles CRLF line endings, even when a CR and its LF arrive in different chunks', async () => {
    const events = await collect(body('event: final\r', '\ndata: {}\r\n\r', '\n'))
    expect(events).toEqual([{ event: 'final', data: '{}' }])
  })

  it('joins multi-line data with newlines and strips only one leading space', async () => {
    const events = await collect(body('data: line one\ndata:  indented\n\n'))
    expect(events).toEqual([{ event: 'message', data: 'line one\n indented' }])
  })

  it('ignores comments and events without data, and drops a final event that never ended', async () => {
    const events = await collect(
      body(': keep-alive\n\nevent:ping\n\nevent:final\ndata:{}\n\nevent:final\ndata:{"cut'),
    )
    expect(events).toEqual([{ event: 'final', data: '{}' }])
  })
})
