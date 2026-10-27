import type { FormHTMLAttributes, ReactNode } from 'react'

type Padding = 'compact' | 'normal' | 'roomy'

const paddings: Record<Padding, string> = { compact: 'p-3', normal: 'p-card', roomy: 'p-6' }

/** Form attributes are a superset of the common ones, so `as="form"` takes `onSubmit` and `noValidate`. */
interface CardProps extends FormHTMLAttributes<HTMLElement> {
  as?: 'div' | 'section' | 'article' | 'form' | 'li'
  padding?: Padding
}

/** The panel every page is built from: a solid surface on the grey canvas, the large card radius (spec §7). */
export function Card({ as: Tag = 'div', padding = 'normal', className = '', ...rest }: CardProps) {
  return (
    <Tag
      className={`rounded-card border border-line/70 bg-surface shadow-card ${paddings[padding]} ${className}`}
      {...rest}
    />
  )
}

interface CardHeaderProps {
  title: ReactNode
  subtitle?: ReactNode
  /** A small icon in a round chip before the title. */
  icon?: ReactNode
  /** Links or buttons at the right of the header. */
  action?: ReactNode
  /** Lets the card label itself with `aria-labelledby`. */
  titleId?: string
}

export function CardHeader({ title, subtitle, icon, action, titleId }: CardHeaderProps) {
  return (
    <header className="mb-4 flex items-center justify-between gap-3">
      <div className="flex min-w-0 items-center gap-3">
        {icon && <IconChip>{icon}</IconChip>}
        <div className="min-w-0">
          <h2 id={titleId} className="truncate text-heading">
            {title}
          </h2>
          {subtitle && <p className="truncate text-xs text-faint">{subtitle}</p>}
        </div>
      </div>
      {action && <div className="flex shrink-0 items-center gap-1.5">{action}</div>}
    </header>
  )
}

/** The round icon chip the reference cards open with. */
export function IconChip({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={`inline-flex size-10 shrink-0 items-center justify-center rounded-pill bg-elevated text-ink [&>svg]:size-[1.125rem] ${className}`}
      aria-hidden
    >
      {children}
    </span>
  )
}

/** A small rounded label: the month a card covers, a currency, a badge. */
export function Chip({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={`inline-flex shrink-0 items-center gap-1 rounded-pill border border-line-strong px-2.5 py-1 text-caption font-medium tracking-wide uppercase text-muted ${className}`}
    >
      {children}
    </span>
  )
}
