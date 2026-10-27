/** The robot mark and the wordmark. On a phone's top bar the wordmark can be visually hidden; it stays the logo's name. */
export function Logo({
  className = '',
  wordmarkClassName = '',
}: {
  className?: string
  wordmarkClassName?: string
}) {
  return (
    <span className={`inline-flex items-center gap-2.5 font-semibold tracking-tight ${className}`}>
      <img src="/logo-mark.png" alt="" width={36} height={36} className="size-9 shrink-0" />
      <span className={`text-lg ${wordmarkClassName}`}>MonthEndBuddy</span>
    </span>
  )
}
