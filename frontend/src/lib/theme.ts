import { useCallback, useSyncExternalStore } from 'react'

export type Theme = 'light' | 'dark'

/** The same key /theme.js reads before first paint. */
const STORAGE_KEY = 'meb-theme'

function current(): Theme {
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'
}

const listeners = new Set<() => void>()

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** The page's theme, which the person picks in the top bar; the choice is remembered in this browser only. */
export function useTheme(): [Theme, (theme: Theme) => void] {
  const theme = useSyncExternalStore(subscribe, current, () => 'light' as Theme)
  const setTheme = useCallback((next: Theme) => {
    document.documentElement.dataset.theme = next
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Blocked storage only means the choice lasts for this visit.
    }
    listeners.forEach((listener) => listener())
  }, [])
  return [theme, setTheme]
}
