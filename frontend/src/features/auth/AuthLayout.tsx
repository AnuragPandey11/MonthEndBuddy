import type { ReactNode } from 'react'
import { Logo } from '../../components/Logo'
import { Card } from '../../components/Card'

export function AuthLayout({
  title,
  subtitle,
  children,
}: {
  title: string
  subtitle: string
  children: ReactNode
}) {
  return (
    <main className="stripes flex min-h-dvh items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm">
        <Logo className="mb-8" />
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        <p className="mt-1 text-sm text-muted">{subtitle}</p>
        <Card padding="roomy" className="mt-6">
          {children}
        </Card>
      </div>
    </main>
  )
}
