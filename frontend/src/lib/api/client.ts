import { tokenStore } from '../auth/tokenStore'
import type { TokenResponse } from './types'

export interface FieldViolation {
  field: string
  message: string
}

/** An RFC 9457 problem from the API (architecture §13.1), or a network failure (status 0). */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly errors: FieldViolation[]
  readonly traceId?: string

  constructor(
    status: number,
    code: string,
    message: string,
    errors: FieldViolation[] = [],
    traceId?: string,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.errors = errors
    this.traceId = traceId
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
  /** Send the access token and refresh it on 401. Off for the auth endpoints themselves. */
  auth?: boolean
  /** Defaults to JSON. The copilot's stream asks for `text/event-stream`. */
  accept?: string
}

/** Sent on every call; the cookie-authenticated refresh and logout endpoints require it (CSRF defence). */
const CLIENT_HEADER = { 'X-MEB-Client': 'web' }

let refreshInFlight: Promise<boolean> | null = null
let sessionExpiredHandler: (() => void) | null = null

/** Called when a refresh fails, i.e. the user must sign in again. */
export function onSessionExpired(handler: (() => void) | null): void {
  sessionExpiredHandler = handler
}

/**
 * Exchanges the refresh cookie for a new access token. Concurrent callers share one request: the backend rotates
 * refresh tokens and treats reuse as theft (architecture §5.2), so two parallel refreshes would end the session.
 */
export function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const response = await fetch('/api/v1/auth/refresh', {
          method: 'POST',
          headers: CLIENT_HEADER,
          credentials: 'same-origin',
        })
        if (!response.ok) {
          tokenStore.set(null)
          return false
        }
        const body = (await response.json()) as TokenResponse
        tokenStore.set(body.accessToken)
        return true
      } catch {
        tokenStore.set(null)
        return false
      } finally {
        refreshInFlight = null
      }
    })()
  }
  return refreshInFlight
}

/** JSON request to the API. On 401 it refreshes once (shared with concurrent calls) and retries. */
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return parse<T>(await apiFetch(path, options))
}

/**
 * The raw response, after the same refresh-on-401 as {@link api}. For bodies that are not one JSON document, such as
 * the copilot's event stream; a failed response should go through {@link toApiError}.
 */
export async function apiFetch(path: string, options: RequestOptions = {}): Promise<Response> {
  const auth = options.auth ?? true
  const tokenUsed = auth ? tokenStore.get() : null
  let response = await send(path, options, tokenUsed)

  if (response.status === 401 && auth) {
    const current = tokenStore.get()
    // Another request may already have refreshed while this one was in flight.
    const refreshed = current !== null && current !== tokenUsed ? true : await refreshSession()
    if (refreshed) {
      response = await send(path, options, tokenStore.get())
    }
    if (response.status === 401) {
      tokenStore.set(null)
      sessionExpiredHandler?.()
    }
  }
  return response
}

async function send(path: string, options: RequestOptions, token: string | null): Promise<Response> {
  const headers: Record<string, string> = { Accept: options.accept ?? 'application/json', ...CLIENT_HEADER }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`
  try {
    return await fetch(path, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
      credentials: 'same-origin',
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, 'network_error', 'Could not reach the server. Check your connection and try again.')
  }
}

async function parse<T>(response: Response): Promise<T> {
  if (response.ok) {
    if (response.status === 204) return undefined as T
    const text = await response.text()
    return (text ? JSON.parse(text) : undefined) as T
  }
  throw await toApiError(response)
}

/** Reads a failed response's problem body into an {@link ApiError}. */
export async function toApiError(response: Response): Promise<ApiError> {
  let problem: Record<string, unknown> = {}
  try {
    problem = (await response.json()) as Record<string, unknown>
  } catch {
    // Not a problem body (e.g. a proxy error page); fall back to the status line.
  }
  return new ApiError(
    response.status,
    typeof problem.code === 'string' ? problem.code : 'error',
    typeof problem.detail === 'string' ? problem.detail : response.statusText || 'Request failed',
    Array.isArray(problem.errors) ? (problem.errors as FieldViolation[]) : [],
    typeof problem.traceId === 'string' ? problem.traceId : undefined,
  )
}
