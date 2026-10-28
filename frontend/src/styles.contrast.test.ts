/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * WCAG contrast of the design tokens (P4.5), computed from styles.css itself so a token change cannot regress it
 * unseen. Vitest runs with `css: false` (even `?raw` CSS imports come back empty), so the file is read from disk,
 * relative to the project root Vitest runs in.
 */
const css = readFileSync(join(process.cwd(), 'src', 'styles.css'), 'utf8')
function tokens(block: string): Record<string, string> {
  const found: Record<string, string> = {}
  for (const [, name, hex] of block.matchAll(/--color-([\w-]+):\s*(#[0-9a-fA-F]{6})\b/g)) found[name!] = hex!
  return found
}

const DARK = ":root[data-theme='dark'] {"
const light = tokens(css.slice(css.indexOf('@theme {'), css.indexOf(DARK)))
const dark = { ...light, ...tokens(css.slice(css.indexOf(DARK), css.indexOf('@layer base'))) }

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r! + 0.7152 * g! + 0.0722 * b!
}

/** `fg` at `alpha` over `bg`, as Tailwind's `bg-x/NN` draws it. */
function mix(fg: string, bg: string, alpha: number): string {
  const channel = (hex: string, i: number) => parseInt(hex.slice(i, i + 2), 16)
  return `#${[1, 3, 5]
    .map((i) =>
      Math.round(channel(fg, i) * alpha + channel(bg, i) * (1 - alpha))
        .toString(16)
        .padStart(2, '0'),
    )
    .join('')}`
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi! + 0.05) / (lo! + 0.05)
}

/** [foreground, background, minimum]: 4.5 for text, 3 for focus rings and data marks. */
const PAIRS: [string, string, number][] = [
  ...['ink', 'muted', 'faint'].flatMap((fg) =>
    ['surface', 'canvas', 'elevated'].map((bg) => [fg, bg, 4.5] as [string, string, number]),
  ),
  ...['positive', 'negative', 'warning'].flatMap((fg) =>
    ['surface', 'canvas', 'elevated'].map((bg) => [fg, bg, 4.5] as [string, string, number]),
  ),
  ['accent', 'surface', 4.5],
  ['on-night', 'night', 4.5],
  ['on-lime', 'lime', 4.5],
  ['focus', 'surface', 3],
  ['focus', 'canvas', 3],
  ['series-income', 'surface', 3],
]

describe.each([
  ['light', light, '#ffffff'],
  ['dark', dark, '#ffffff'],
])('%s theme tokens', (_theme, palette, white) => {
  it.each(PAIRS)('%s on %s reaches %s:1', (fg, bg, min) => {
    expect(palette[fg], `--color-${fg}`).toBeDefined()
    expect(palette[bg], `--color-${bg}`).toBeDefined()
    expect(contrast(palette[fg]!, palette[bg]!)).toBeGreaterThanOrEqual(min)
  })

  // Status pills: the status colour as text on its own tint (bg-positive/12, bg-negative/10, bg-warning/10).
  it.each([
    ['positive', 0.12],
    ['negative', 0.1],
    ['warning', 0.1],
  ] as const)('%s text on its tinted pill reaches 4.5:1 on every surface', (status, alpha) => {
    for (const surface of ['surface', 'elevated']) {
      const pill = mix(palette[status]!, palette[surface]!, alpha)
      expect(contrast(palette[status]!, pill), `${status} on ${surface}`).toBeGreaterThanOrEqual(4.5)
    }
  })

  it('white text on the primary button reaches 4.5:1', () => {
    expect(contrast(white, palette['accent-strong']!)).toBeGreaterThanOrEqual(4.5)
  })
})
