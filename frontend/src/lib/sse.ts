/**
 * Server-Sent Events over `fetch` (architecture §10.7). `EventSource` cannot send a POST with a body and headers, so
 * the copilot reads the response stream itself. Network chunks split anywhere, including inside a multi-byte
 * character or between `\r` and `\n`, so the parser buffers until an event's closing blank line.
 */

export interface SseEvent {
  /** `message` when the event names no type, as in the EventSource spec. */
  event: string
  data: string
}

export async function* readSse(body: ReadableStream<Uint8Array>): AsyncGenerator<SseEvent> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // Hold back a trailing `\r`: the `\n` of its `\r\n` may be in the next chunk.
      const carry = buffer.endsWith('\r') ? '\r' : ''
      let text = (carry ? buffer.slice(0, -1) : buffer).replace(/\r\n?/g, '\n')
      let end: number
      while ((end = text.indexOf('\n\n')) >= 0) {
        const event = parseBlock(text.slice(0, end))
        text = text.slice(end + 2)
        if (event) yield event
      }
      buffer = text + carry
    }
  } finally {
    reader.releaseLock()
  }
  // An event with no closing blank line was cut off, so it is dropped (as EventSource does).
}

/** One event's lines. Comments (`:`) and unknown fields are ignored; an event without data is not dispatched. */
function parseBlock(block: string): SseEvent | null {
  let event = 'message'
  const data: string[] = []
  for (const line of block.split('\n')) {
    if (line === '' || line.startsWith(':')) continue
    const colon = line.indexOf(':')
    const field = colon < 0 ? line : line.slice(0, colon)
    let value = colon < 0 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') event = value
    else if (field === 'data') data.push(value)
  }
  return data.length > 0 ? { event, data: data.join('\n') } : null
}
