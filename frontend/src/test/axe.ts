import axe from 'axe-core'
import { expect } from 'vitest'

/**
 * Fails on serious or critical axe violations (P4.5). `color-contrast` is off because jsdom cannot compute styles
 * (Vitest runs with `css: false`); token contrast is checked in `styles.contrast.test.ts` and in a real browser.
 * `landmark-unique` is off because the shell renders two "Main" navigations, one hidden by CSS per breakpoint,
 * which jsdom cannot see.
 */
export async function expectNoSeriousA11yViolations(root: Element = document.body) {
  const result = await axe.run(root, {
    rules: { 'color-contrast': { enabled: false }, 'landmark-unique': { enabled: false } },
    resultTypes: ['violations'],
  })
  const serious = result.violations
    .filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => `${v.id} (${v.impact}): ${v.help} — ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)
  expect(serious, serious.join('\n')).toEqual([])
}
