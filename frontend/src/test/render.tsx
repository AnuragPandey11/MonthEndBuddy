import type { ReactNode } from 'react'
import { render } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { vi } from 'vitest'
import { AuthProvider } from '../features/auth/AuthProvider'
import { createQueryClient } from '../lib/queryClient'
import { tokenStore } from '../lib/auth/tokenStore'

export type Handler = (url: URL, init: RequestInit | undefined) => unknown

/**
 * Renders a page signed in, with a fake fetch routed by "METHOD /path" (query string ignored). Unknown routes are 404
 * problems, so a test fails loudly when the page calls something unexpected. A handler may return a `Response` of its
 * own, such as an event stream. Returns the fetch mock for assertions.
 */
export function renderPage(ui: ReactNode, routes: Record<string, unknown | Handler>, initialPath = '/') {
  tokenStore.set('test-token')
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), 'http://localhost')
    const key = `${init?.method ?? 'GET'} ${url.pathname}`
    if (key === 'POST /api/v1/auth/refresh') return json(200, { accessToken: 'test-token', expiresIn: 900 })
    if (key === 'GET /api/v1/me')
      return json(200, { id: 'u1', email: 'a@b.c', displayName: 'Asha', demo: false })
    if (!(key in routes)) return json(404, { code: 'not_found', detail: `no fake for ${key}` })
    const route = routes[key]
    const body = typeof route === 'function' ? (route as Handler)(url, init) : route
    if (body instanceof Response) return body
    return body === undefined ? new Response(null, { status: 204 }) : json(200, body)
  })
  vi.stubGlobal('fetch', fetchMock)
  const router = createMemoryRouter([{ path: '*', element: ui }], { initialEntries: [initialPath] })
  render(
    <QueryClientProvider client={createQueryClient()}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  )
  return fetchMock
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

export const inr = (amount: string) => ({ amount, currency: 'INR' })

export const emptyCoverage = { transactionCount: 0, firstDate: null, lastDate: null, monthsWithData: [] }
