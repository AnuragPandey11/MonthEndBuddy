import { Link, NavLink, Outlet, useLocation } from 'react-router'
import { motion } from 'motion/react'
import {
  ArrowLeftRight,
  Bell,
  ChevronDown,
  FileUp,
  FlaskConical,
  LayoutDashboard,
  LogOut,
  Moon,
  PiggyBank,
  Plus,
  Settings,
  Sparkles,
  Sun,
  Target,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { Logo } from '../components/Logo'
import { Popover } from '../components/Popover'
import { AlertList } from '../features/alerts/AlertList'
import { useAuth } from '../features/auth/useAuth'
import { useAlerts } from '../lib/api/finance'
import { duration, ease } from '../lib/motion'
import { useTheme } from '../lib/theme'

interface NavItem {
  to: string
  label: string
  /** The bottom bar's label on phones, where each item has a fifth of the width. */
  short: string
  icon: LucideIcon
}

const NAV: NavItem[] = [
  { to: '/', label: 'Overview', short: 'Overview', icon: LayoutDashboard },
  { to: '/transactions', label: 'Transactions', short: 'Activity', icon: ArrowLeftRight },
  { to: '/budgets', label: 'Budgets', short: 'Budgets', icon: PiggyBank },
  { to: '/goals', label: 'Savings goals', short: 'Goals', icon: Target },
  { to: '/copilot', label: 'AI Copilot', short: 'Copilot', icon: Sparkles },
]

const iconButton =
  'relative inline-flex size-10 items-center justify-center rounded-pill border border-line bg-surface text-ink transition-colors hover:bg-elevated aria-expanded:bg-elevated'

const menuItem =
  'flex w-full items-center gap-2.5 rounded-control px-2.5 py-2 text-left text-sm text-muted hover:bg-elevated hover:text-ink'

function initials(name: string): string {
  const parts = name.trim().split(/\s+/)
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? (parts.at(-1)?.[0] ?? '') : '')).toUpperCase() || '?'
}

/**
 * The top bar (spec §7): the logo, the main navigation as a row of pills whose dark highlight slides to the current
 * page, then theme, quick add, notifications and the account. Below `lg` the pills move to a bottom bar.
 */
export function AppShell() {
  const { me } = useAuth()
  const { pathname } = useLocation()

  return (
    <div className="flex min-h-dvh flex-col pb-16 lg:pb-0">
      <header className="sticky top-0 z-40 border-b border-line/60 bg-canvas/85 backdrop-blur-md">
        <div className="mx-auto flex w-full max-w-[90rem] items-center justify-between gap-3 px-4 py-3 md:px-6">
          <div className="flex min-w-0 items-center gap-3">
            <Link to="/" className="rounded-control" aria-label="MonthEndBuddy, overview">
              <Logo wordmarkClassName="max-sm:sr-only" />
            </Link>
            {me?.demo && (
              <span className="inline-flex shrink-0 items-center gap-1 rounded-pill border border-warning/40 bg-warning/10 px-2 py-0.5 text-xs text-warning">
                <FlaskConical className="size-3" aria-hidden />
                <span className="max-sm:sr-only lg:max-2xl:sr-only">Demo data</span>
              </span>
            )}
          </div>

          <nav
            aria-label="Main"
            className="hidden items-center gap-1 rounded-pill border border-line/70 bg-surface p-1 shadow-card lg:flex"
          >
            {NAV.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                end={to === '/'}
                className={({ isActive }) =>
                  `relative inline-flex items-center gap-2 rounded-pill px-3.5 py-2 text-sm font-medium whitespace-nowrap transition-colors xl:px-4 ${
                    isActive ? 'text-on-night' : 'text-muted hover:text-ink'
                  }`
                }
              >
                {({ isActive }) => (
                  <>
                    {isActive && (
                      <motion.span
                        layoutId="nav-pill"
                        className="absolute inset-0 rounded-pill bg-night"
                        transition={{ duration: duration.base, ease }}
                        aria-hidden
                      />
                    )}
                    <Icon className="relative size-4" aria-hidden />
                    {/* Icons only between lg and xl, where five labels and the actions do not fit. */}
                    <span className="relative max-xl:sr-only">{label}</span>
                  </>
                )}
              </NavLink>
            ))}
          </nav>

          {/* Positioned, so the popovers' panels line up with this edge on phones. */}
          <div className="relative flex items-center gap-2">
            <ThemeToggle />
            <QuickAdd />
            <Notifications />
            <ProfileMenu />
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-[90rem] flex-1 px-4 py-5 md:px-6">
        {/* Each page fades up as it arrives; keyed by path, so a navigation replays it. */}
        <motion.div
          key={pathname}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: duration.base, ease }}
        >
          <Outlet />
        </motion.div>
      </main>

      <nav
        aria-label="Main"
        className="fixed inset-x-0 bottom-0 z-40 grid grid-cols-5 border-t border-line bg-surface/95 backdrop-blur lg:hidden"
      >
        {NAV.map(({ to, short, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              `flex flex-col items-center gap-0.5 py-2 text-caption ${isActive ? 'font-semibold text-accent' : 'text-faint'}`
            }
          >
            <Icon className="size-5" aria-hidden />
            {short}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}

function Avatar({ name }: { name: string }) {
  return (
    <span
      className="inline-flex size-9 shrink-0 items-center justify-center rounded-pill bg-lime text-xs font-semibold text-on-lime"
      aria-hidden
    >
      {initials(name)}
    </span>
  )
}

/** Light or dark, as a two-way switch (the reference's sun and moon). */
function ThemeToggle() {
  const [theme, setTheme] = useTheme()
  const option = (value: 'light' | 'dark', Icon: LucideIcon, label: string) => (
    <button
      type="button"
      onClick={() => setTheme(value)}
      aria-pressed={theme === value}
      aria-label={label}
      className="relative inline-flex size-8 items-center justify-center rounded-pill text-muted transition-colors aria-pressed:text-on-night"
    >
      {theme === value && (
        <motion.span
          layoutId="theme-pill"
          className="absolute inset-0 rounded-pill bg-night"
          transition={{ duration: duration.base, ease }}
          aria-hidden
        />
      )}
      <Icon className="relative size-4" aria-hidden />
    </button>
  )
  return (
    <div
      role="group"
      aria-label="Theme"
      className="hidden items-center gap-0.5 rounded-pill border border-line bg-surface p-0.5 sm:inline-flex"
    >
      {option('light', Sun, 'Light theme')}
      {option('dark', Moon, 'Dark theme')}
    </div>
  )
}

/** The top bar's quick action (spec §7): the three things people come to do. */
function QuickAdd() {
  return (
    <Popover
      label="Quick add"
      trigger={<Plus className="size-[1.125rem]" aria-hidden />}
      triggerClassName={iconButton}
    >
      <ul className="flex flex-col">
        <li>
          <Link to="/transactions?new=1" className={menuItem}>
            <ArrowLeftRight className="size-4" aria-hidden /> Add a transaction
          </Link>
        </li>
        <li>
          <Link to="/import" className={menuItem}>
            <FileUp className="size-4" aria-hidden /> Import a statement
          </Link>
        </li>
        <li>
          <Link to="/copilot" className={menuItem}>
            <Sparkles className="size-4" aria-hidden /> Ask the copilot
          </Link>
        </li>
      </ul>
    </Popover>
  )
}

/** Open anomaly alerts (architecture §9.5). A failed load only hides the count: the shell never shows an error. */
function Notifications() {
  const alerts = useAlerts()
  const open = alerts.data ?? []
  return (
    <Popover
      label={open.length > 0 ? `Notifications, ${open.length} open` : 'Notifications'}
      trigger={
        <>
          <Bell className="size-[1.125rem]" aria-hidden />
          {open.length > 0 && (
            <span
              className="absolute -top-0.5 -right-0.5 inline-flex min-w-4 items-center justify-center rounded-pill bg-negative px-1 text-caption font-semibold text-surface"
              aria-hidden
            >
              {open.length > 9 ? '9+' : open.length}
            </span>
          )}
        </>
      }
      triggerClassName={iconButton}
    >
      <div className="flex flex-col gap-3 p-2">
        <p className="text-sm font-semibold">Unusual spending</p>
        {alerts.isPending ? (
          <p className="text-sm text-muted">Loading…</p>
        ) : alerts.isError ? (
          <p className="text-sm text-muted">We couldn't load your alerts.</p>
        ) : open.length === 0 ? (
          <p className="text-sm text-muted">Nothing unusual right now.</p>
        ) : (
          <div className="max-h-80 overflow-y-auto">
            <AlertList alerts={open} />
          </div>
        )}
      </div>
    </Popover>
  )
}

function ProfileMenu() {
  const { me, logout } = useAuth()
  const [theme, setTheme] = useTheme()
  return (
    <Popover
      label="Your account"
      trigger={
        me ? (
          <>
            <Avatar name={me.displayName} />
            <span className="hidden max-w-36 truncate text-sm font-semibold 2xl:inline">
              {me.displayName}
            </span>
            <ChevronDown className="hidden size-4 text-muted 2xl:inline" aria-hidden />
          </>
        ) : (
          <Settings className="size-5" aria-hidden />
        )
      }
      triggerClassName="inline-flex items-center gap-2 rounded-pill p-0.5 hover:bg-surface aria-expanded:bg-surface 2xl:pr-2.5"
    >
      {me && (
        <div className="border-b border-line px-2.5 pt-1 pb-2.5">
          <p className="truncate text-sm font-medium">{me.displayName}</p>
          <p className="truncate text-xs text-faint">{me.email}</p>
        </div>
      )}
      <ul className="flex flex-col pt-1">
        {/* The top bar's theme switch is hidden on phones, so it lives here instead. */}
        <li className="sm:hidden">
          <button
            type="button"
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
            className={menuItem}
          >
            {theme === 'dark' ? (
              <Sun className="size-4" aria-hidden />
            ) : (
              <Moon className="size-4" aria-hidden />
            )}
            {theme === 'dark' ? 'Use light theme' : 'Use dark theme'}
          </button>
        </li>
        <li>
          <Link to="/settings" className={menuItem}>
            <Settings className="size-4" aria-hidden /> Settings
          </Link>
        </li>
        <li>
          <button type="button" onClick={() => void logout()} className={menuItem}>
            <LogOut className="size-4" aria-hidden /> Sign out
          </button>
        </li>
      </ul>
    </Popover>
  )
}
