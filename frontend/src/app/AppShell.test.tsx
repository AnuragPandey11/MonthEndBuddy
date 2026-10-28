import { afterEach, describe, expect, it, vi } from 'vitest'
import { Route, Routes } from 'react-router'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderPage } from '../test/render'
import type { Notification } from '../lib/api/types'
import { AppShell } from './AppShell'
import { expectNoSeriousA11yViolations } from '../test/axe'

function notification(id: string, title: string, readAt: string | null = null): Notification {
  return {
    id,
    type: 'BUDGET_THRESHOLD',
    title,
    body: "You've spent ₹8,400.00 of a ₹10,000.00 budget in October 2026 (84%).",
    link: '/settings',
    createdAt: '2026-10-20T10:00:00Z',
    readAt,
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
      'GET /api/v1/notifications': [
        notification('n1', 'Budget 80% used'),
        notification('n2', 'Unusual spending'),
        notification('n3', 'Import complete', '2026-10-21T09:00:00Z'),
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

  it('has no serious accessibility violations, with a panel open', async () => {
    renderShell()
    await screen.findByRole('button', { name: 'Notifications, 2 unread' })
    await expectNoSeriousA11yViolations()
    await userEvent.click(screen.getByRole('button', { name: 'Asha, your account' }))
    await expectNoSeriousA11yViolations()
  })

  it('counts unread notifications on the bell and lists them all in its panel', async () => {
    renderShell()
    const bell = await screen.findByRole('button', { name: 'Notifications, 2 unread' })

    await userEvent.click(bell)

    const panel = screen.getByRole('dialog', { name: 'Notifications, 2 unread' })
    expect(within(panel).getAllByRole('link')).toHaveLength(3)
    expect(within(panel).getByRole('link', { name: /Budget 80% used \(unread\)/ })).toBeInTheDocument()
    expect(within(panel).getByRole('link', { name: /^Import complete/ })).not.toHaveTextContent('(unread)')
    expect(bell).toHaveAttribute('aria-expanded', 'true')
  })

  it('opening a notification marks it read and goes to its page', async () => {
    const read = vi.fn(() => notification('n1', 'Budget 80% used', '2026-10-28T08:00:00Z'))
    renderShell({ 'POST /api/v1/notifications/n1/read': read })
    await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 2 unread' }))

    await userEvent.click(screen.getByRole('link', { name: /Budget 80% used/ }))

    expect(await screen.findByText('Settings page')).toBeInTheDocument()
    await waitFor(() => expect(read).toHaveBeenCalledOnce())
  })

  it('hides the count when notifications cannot be loaded, without an error in the shell', async () => {
    renderShell({ 'GET /api/v1/notifications': () => new Response('{}', { status: 500 }) })
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
    await userEvent.click(await screen.findByRole('button', { name: 'Asha, your account' }))
    await userEvent.click(
      within(screen.getByRole('dialog', { name: 'Asha, your account' })).getByRole('link', {
        name: 'Settings',
      }),
    )

    expect(await screen.findByText('Settings page')).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Asha, your account' })).toBeNull()
  })

  it('closes a panel on a click outside it', async () => {
    renderShell()
    await userEvent.click(screen.getByRole('button', { name: 'Quick add' }))
    await userEvent.click(screen.getByText('Overview page'))
    expect(screen.queryByRole('dialog', { name: 'Quick add' })).toBeNull()
  })

  it('signs out from the account menu', async () => {
    const fetchMock = renderShell()
    await userEvent.click(await screen.findByRole('button', { name: 'Asha, your account' }))
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([url]) => String(url) === '/api/v1/auth/logout')).toBe(true),
    )
  })
})
