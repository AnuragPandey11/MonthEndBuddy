import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-3 px-4 text-center">
      <p className="text-sm text-faint">404</p>
      <h1 className="text-title">Page not found</h1>
      <Link to="/" className="text-sm text-accent hover:underline">
        Back to overview
      </Link>
    </main>
  )
}
