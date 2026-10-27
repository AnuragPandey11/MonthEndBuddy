import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../auth/tokenStore'
import { api, ApiError, onSessionExpired } from './client'

function respond(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
  })
}

function headerOf(init: RequestInit | undefined, name: string): string | null {
  return new Headers(init?.headers).get(name)
}

describe('api client', () => {
  beforeEach(() => tokenStore.set('old-token'))

  afterEach(() => {
    tokenStore.set(null)
    onSessionExpired(null)
    vi.unstubAllGlobals()
  })

  it('shares one refresh between three concurrent 401s and retries each with the new token', async () => {
    let refreshCalls = 0
    let releaseRefresh!: () => void
    const refreshGate = new Promise<void>((resolve) => (releaseRefresh = resolve))
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input)
        if (url === '/api/v1/auth/refresh') {
          refreshCalls++
          expect(headerOf(init, 'X-MEB-Client')).toBe('web')
          expect(init?.method).toBe('POST')
          await refreshGate
          return respond(200, { accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 900 })
        }
        return headerOf(init, 'Authorization') === 'Bearer new-token'
          ? respond(200, { url })
          : respond(401, { code: 'token_expired' })
      }),
    )

    const calls = [api('/api/v1/a'), api('/api/v1/b'), api('/api/v1/c')]
    await vi.waitFor(() => expect(refreshCalls).toBe(1))
    releaseRefresh()

    await expect(Promise.all(calls)).resolves.toEqual([
      { url: '/api/v1/a' },
      { url: '/api/v1/b' },
      { url: '/api/v1/c' },
    ])
    expect(refreshCalls).toBe(1)
    expect(tokenStore.get()).toBe('new-token')
  })

  it('ends the session when the refresh fails', async () => {
    const expired = vi.fn()
    onSessionExpired(expired)
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        String(input) === '/api/v1/auth/refresh'
          ? respond(401, { code: 'token_reused' })
          : respond(401, { code: 'token_expired', detail: 'Authentication required' }),
      ),
    )

    await expect(api('/api/v1/me')).rejects.toMatchObject({ status: 401, code: 'token_expired' })
    expect(tokenStore.get()).toBeNull()
    expect(expired).toHaveBeenCalledOnce()
  })

  it('turns problem bodies into ApiError with field errors and trace id', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        respond(422, {
          code: 'validation_failed',
          detail: 'Validation failed',
          traceId: 'abc123',
          errors: [{ field: 'amount', message: 'must be positive' }],
        }),
      ),
    )
    const error = await api('/api/v1/transactions', { method: 'POST', body: {} }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 422,
      code: 'validation_failed',
      traceId: 'abc123',
      errors: [{ field: 'amount', message: 'must be positive' }],
    })
  })

  it('does not send the token or refresh for auth endpoints', async () => {
    const fetchMock = vi.fn(async () => respond(401, { code: 'invalid_credentials' }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(
      api('/api/v1/auth/login', { method: 'POST', body: { email: 'a', password: 'b' }, auth: false }),
    ).rejects.toMatchObject({ code: 'invalid_credentials' })
    expect(fetchMock).toHaveBeenCalledOnce()
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(headerOf(init, 'Authorization')).toBeNull()
    expect(tokenStore.get()).toBe('old-token')
  })

  it('returns undefined for 204 and reports network failures as status 0', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(null, { status: 204 })),
    )
    await expect(api('/api/v1/transactions/1', { method: 'DELETE' })).resolves.toBeUndefined()

    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch')
      }),
    )
    await expect(api('/api/v1/me')).rejects.toMatchObject({ status: 0, code: 'network_error' })
  })
})
