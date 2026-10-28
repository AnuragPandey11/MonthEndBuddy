import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expectNoSeriousA11yViolations } from '../test/axe'
import { Dialog } from './Dialog'

function Example() {
  return (
    <>
      <button type="button">Behind the dialog</button>
      <Dialog title="Edit goal" open onClose={() => {}}>
        <input aria-label="Name" />
        <button type="button">Save</button>
      </Dialog>
    </>
  )
}

describe('Dialog', () => {
  it('keeps Tab inside the dialog in both directions', async () => {
    render(<Example />)
    const name = screen.getByLabelText('Name')
    expect(name).toHaveFocus()
    await userEvent.tab()
    expect(screen.getByRole('button', { name: 'Save' })).toHaveFocus()
    await userEvent.tab()
    expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus()
    await userEvent.tab()
    expect(name).toHaveFocus()
    await userEvent.tab({ shift: true })
    expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus()
    expect(screen.getByRole('button', { name: 'Behind the dialog', hidden: true })).not.toHaveFocus()
  })

  it('has no serious accessibility violations', async () => {
    render(<Example />)
    await expectNoSeriousA11yViolations()
  })
})
