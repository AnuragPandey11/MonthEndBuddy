/**
 * The access token lives only in memory (architecture D8): never in localStorage, so injected scripts cannot
 * read a long-lived copy. The refresh token is an httpOnly cookie the SPA never sees.
 */
type Listener = () => void

let accessToken: string | null = null
const listeners = new Set<Listener>()

export const tokenStore = {
  get(): string | null {
    return accessToken
  },
  set(token: string | null): void {
    if (token === accessToken) return
    accessToken = token
    listeners.forEach((listener) => listener())
  },
  subscribe(listener: Listener): () => void {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
}
