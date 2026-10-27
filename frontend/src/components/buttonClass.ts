export type Variant = 'primary' | 'dark' | 'secondary' | 'ghost' | 'danger'

const variants: Record<Variant, string> = {
  primary: 'bg-accent-strong text-white hover:bg-accent disabled:bg-accent-strong/50',
  dark: 'bg-night text-on-night hover:bg-night/85 disabled:opacity-50',
  secondary: 'border border-line-strong bg-elevated text-ink hover:border-faint disabled:opacity-50',
  ghost: 'text-muted hover:bg-elevated hover:text-ink disabled:opacity-50',
  danger: 'border border-negative/40 text-negative hover:bg-negative/10 disabled:opacity-50',
}

/** The pill shape and a variant's colours, for links that look like buttons. */
export function buttonClass(variant: Variant = 'primary', className = '') {
  return `inline-flex items-center justify-center gap-2 rounded-pill px-4 py-2 text-sm font-medium transition-[background-color,border-color,transform] duration-150 active:scale-[0.98] disabled:cursor-not-allowed motion-reduce:active:scale-100 ${variants[variant]} ${className}`
}
