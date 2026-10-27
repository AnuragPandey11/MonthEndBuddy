import { afterEach, describe, expect, it, vi } from 'vitest'
import { Route, Routes } from 'react-router'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderPage } from '../test/render'
import type { Alert } from '../lib/api/types'
import { AppShell } from './AppShell'

function alert(id: string, message: string): Alert {
  return {
    id,
    ruleId: 'CATEGORY_SPIKE',
    subjectType: 'CATEGORY',
    subjectId: 'c1',
    subjectLabel: 'Dining',
    periodStart: '2026-10-01',
    severity: 'WARNING',
    status: 'OPEN',
    message,
    createdAt: '2026-10-20T10:00:00Z',
  }
}

function renderShell(routes: Record<string, unknown> = {}) {
  return renderPage(
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<p>Overview page</p>} />
        <Route path="settings" element={<p>Settings page</p>} />
        <Route path="import" element={<p>Import page</p>} />
      </Route>
    </Routes>,
    {
      'GET /api/v1/alerts': [
        alert('a1', 'Dining is up 80% on last month.'),
        alert('a2', 'A large purchase.'),
      ],
      'POST /api/v1/auth/logout': undefined,
      ...routes,
    },
  )
}

describe('AppShell', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('has the spec §7 navigation, with the copilot available', async () => {
    renderShell()
    const [sidebar] = screen.getAllByRole('navigation', { name: 'Main' })
    for (const name of ['Overview', 'Transactions', 'Budgets', 'Savings goals', 'AI Copilot']) {
      expect(within(sidebar!).getByRole('link', { name })).toBeInTheDocument()
    }
    expect(within(sidebar!).queryByText(/soon/i)).toBeNull()
    // The greeting moved to the overview's header; the account button shows the name.
    expect(await screen.findByText('Asha')).toBeInTheDocument()
  })

  it('switches between the light and dark themes', async () => {
    renderShell()
    await userEvent.click(screen.getByRole('button', { name: 'Dark theme' }))
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(screen.getByRole('button', { name: 'Dark theme' })).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(screen.getByRole('button', { name: 'Light theme' }))
    expect(document.documentElement.dataset.theme).toBe('light')
  })

  it('counts open alerts on the bell and lists them in its panel', async () => {
    renderShell()
    const bell = await screen.findByRole('button', { name: 'Notifications, 2 open' })

    await userEvent.click(bell)

    const panel = screen.getByRole('dialog', { name: 'Notifications, 2 open' })
    expect(within(panel).getByText('Dining is up 80% on last month.')).toBeInTheDocument()
    expect(bell).toHaveAttribute('aria-expanded', 'true')
  })

  it('hides the count when alerts cannot be loaded, without an error in the shell', async () => {
    renderShell({ 'GET /api/v1/alerts': () => new Response('{}', { status: 500 }) })
    await userEvent.click(await screen.findByRole('button', { name: 'Notifications' }))
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('closes a panel on Escape and gives focus back to its button', async () => {
    renderShell()
    const quickAdd = screen.getByRole('button', { name: 'Quick add' })
    await userEvent.click(quickAdd)
    expect(screen.getByRole('dialog', { name: 'Quick add' })).toBeInTheDocument()

    await userEvent.keyboard('{Escape}')

    expect(screen.queryByRole('dialog', { name: 'Quick add' })).toBeNull()
    expect(quickAdd).toHaveFocus()
  })

  it('closes a panel when one of its links navigates', async () => {
    renderShell()
    await userEvent.click(screen.getByRole('button', { name: 'Your account' }))
    await userEvent.click(
      within(screen.getByRole('dialog', { name: 'Your account' })).getByRole('link', { name: 'Settings' }),
    )

    expect(await screen.findByText('Settings page')).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Your account' })).toBeNull()
  })

  it('closes a panel on a click outside it', async () => {
    renderShell()
    await userEvent.click(screen.getByRole('button', { name: 'Quick add' }))
    await userEvent.click(screen.getByText('Overview page'))
    expect(screen.queryByRole('dialog', { name: 'Quick add' })).toBeNull()
  })

  it('signs out from the account menu', async () => {
    const fetchMock = renderShell()
    await userEvent.click(await screen.findByRole('button', { name: 'Your account' }))
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([url]) => String(url) === '/api/v1/auth/logout')).toBe(true),
    )
  })
})
