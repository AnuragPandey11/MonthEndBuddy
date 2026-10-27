import { createBrowserRouter } from 'react-router'
import { RedirectIfAuthenticated, RequireAuth } from '../features/auth/RouteGuards'
import { NotFoundPage } from '../pages/NotFoundPage'
import { AppShell } from './AppShell'

// Pages load on demand, so the first paint ships only the shell and the current page.
export const router = createBrowserRouter([
  {
    element: <RedirectIfAuthenticated />,
    children: [
      {
        path: '/login',
        lazy: async () => ({ Component: (await import('../features/auth/LoginPage')).LoginPage }),
      },
      {
        path: '/register',
        lazy: async () => ({ Component: (await import('../features/auth/RegisterPage')).RegisterPage }),
      },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [
          {
            index: true,
            lazy: async () => ({
              Component: (await import('../features/dashboard/DashboardPage')).DashboardPage,
            }),
          },
          {
            path: 'transactions',
            lazy: async () => ({
              Component: (await import('../features/transactions/TransactionsPage')).TransactionsPage,
            }),
          },
          {
            path: 'import',
            lazy: async () => ({ Component: (await import('../features/imports/ImportPage')).ImportPage }),
          },
          {
            path: 'budgets',
            lazy: async () => ({ Component: (await import('../features/budgets/BudgetsPage')).BudgetsPage }),
          },
          {
            path: 'goals',
            lazy: async () => ({ Component: (await import('../features/goals/GoalsPage')).GoalsPage }),
          },
          {
            path: 'settings',
            lazy: async () => ({
              Component: (await import('../features/settings/SettingsPage')).SettingsPage,
            }),
          },
          {
            path: 'copilot',
            lazy: async () => ({
              Component: (await import('../features/copilot/CopilotPage')).CopilotPage,
            }),
          },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
])
