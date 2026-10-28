import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { createQueryClient } from '../../lib/queryClient'
import { tokenStore } from '../../lib/auth/tokenStore'
import { AuthProvider } from './AuthProvider'
import { LoginPage } from './LoginPage'
import { expectNoSeriousA11yViolations } from '../../test/axe'

function renderLogin() {
  const router = createMemoryRouter(
    [
      { path: '/login', element: <LoginPage /> },
      { path: '/', element: <p>Signed in home</p> },
    ],
    { initialEntries: ['/login'] },
  )
  render(
    <QueryClientProvider client={createQueryClient()}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

function respond(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('LoginPage', () => {
  afterEach(() => {
    tokenStore.set(null)
    vi.unstubAllGlobals()
  })

  it('has no serious accessibility violations, with its errors showing too', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => respond(401, { code: 'invalid_token' })),
    )
    renderLogin()
    await expectNoSeriousA11yViolations()
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    await screen.findByText('Enter your email')
    await expectNoSeriousA11yViolations()
  })

  it('validates before calling the API', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL) => respond(401, { code: 'invalid_token' }))
    vi.stubGlobal('fetch', fetchMock)
    renderLogin()
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Enter your email')).toBeInTheDocument()
    expect(screen.getByText('Enter your password')).toBeInTheDocument()
    expect(fetchMock.mock.calls.map(([url]) => String(url))).not.toContain('/api/v1/auth/login')
  })

  it('shows a generic message for wrong credentials', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        String(input) === '/api/v1/auth/login'
          ? respond(401, { code: 'invalid_credentials', detail: 'Authentication failed' })
          : respond(401, { code: 'invalid_token' }),
      ),
    )
    renderLogin()
    await userEvent.type(screen.getByLabelText('Email'), 'asha@example.test')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.')
  })

  it('signs in with demo data and navigates home', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input)
        if (url === '/api/v1/auth/demo') return respond(200, { accessToken: 'demo-token', expiresIn: 900 })
        if (url === '/api/v1/me')
          return respond(200, {
            id: '1',
            email: 'demo@monthendbuddy.dev',
            displayName: 'Demo User',
            demo: true,
          })
        return respond(401, { code: 'invalid_token' })
      }),
    )
    renderLogin()
    await userEvent.click(screen.getByRole('button', { name: /Explore with demo data/ }))
    expect(await screen.findByText('Signed in home')).toBeInTheDocument()
    expect(tokenStore.get()).toBe('demo-token')
  })
})
