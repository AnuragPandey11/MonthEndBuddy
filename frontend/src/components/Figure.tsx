/**
 * A formatted amount with its paise set smaller ("₹4,990.09"). The split is on the formatted string, so no
 * arithmetic happens; screen readers and text search get the whole string from one visually hidden node.
 */
export function Figure({ text, className = '' }: { text: string; className?: string }) {
  const dot = text.lastIndexOf('.')
  const whole = dot >= 0 ? text.slice(0, dot) : text
  const fraction = dot >= 0 ? text.slice(dot) : ''
  return (
    <span className={className}>
      <span aria-hidden>
        {whole}
        <span className="text-[0.55em] tracking-normal opacity-70">{fraction}</span>
      </span>
      <span className="sr-only">{text}</span>
    </span>
  )
}
