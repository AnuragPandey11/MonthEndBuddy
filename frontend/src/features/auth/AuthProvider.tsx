import { useCallback, useEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, onSessionExpired, refreshSession } from '../../lib/api/client'
import type { Me, TokenResponse } from '../../lib/api/types'
import { tokenStore } from '../../lib/auth/tokenStore'
import { AuthContext, type AuthContextValue } from './authContext'

/**
 * Session state (architecture §14). On load it tries a silent refresh, so a reload keeps the user signed in via
 * the httpOnly cookie without the access token ever touching storage.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const token = useSyncExternalStore(tokenStore.subscribe, tokenStore.get)
  const [bootstrapped, setBootstrapped] = useState(false)

  useEffect(() => {
    let active = true
    void refreshSession().finally(() => {
      if (active) setBootstrapped(true)
    })
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    onSessionExpired(() => queryClient.clear())
    return () => onSessionExpired(null)
  }, [queryClient])

  const me = useQuery({
    queryKey: ['me'],
    queryFn: () => api<Me>('/api/v1/me'),
    enabled: token !== null,
    staleTime: 5 * 60_000,
  })

  const signIn = useCallback(
    async (path: string, body?: unknown) => {
      const tokens = await api<TokenResponse>(path, { method: 'POST', body, auth: false })
      queryClient.clear()
      tokenStore.set(tokens.accessToken)
    },
    [queryClient],
  )

  const value = useMemo<AuthContextValue>(
    () => ({
      status: !bootstrapped && token === null ? 'loading' : token ? 'authenticated' : 'anonymous',
      me: me.data,
      login: (email, password) => signIn('/api/v1/auth/login', { email, password }),
      register: (email, password, displayName) =>
        signIn('/api/v1/auth/register', { email, password, displayName }),
      loginAsDemo: () => signIn('/api/v1/auth/demo'),
      logout: async () => {
        try {
          await api<void>('/api/v1/auth/logout', { method: 'POST', auth: false })
        } finally {
          tokenStore.set(null)
          queryClient.clear()
        }
      },
    }),
    [bootstrapped, token, me.data, signIn, queryClient],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
