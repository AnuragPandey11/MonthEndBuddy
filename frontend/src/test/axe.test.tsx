import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'
import { expectNoSeriousA11yViolations } from './axe'

/** The helper must be able to fail, or every page test that uses it proves nothing. */
describe('expectNoSeriousA11yViolations', () => {
  it('fails on a button without a name and an input without a label', async () => {
    render(
      <main>
        <button type="button">
          <svg aria-hidden />
        </button>
        <input />
      </main>,
    )
    await expect(expectNoSeriousA11yViolations()).rejects.toThrow(/button-name|label/)
  })
})
