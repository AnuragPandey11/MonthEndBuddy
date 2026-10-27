import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Meter } from './Meter'

describe('Meter', () => {
  it('states severity in words, not only colour', () => {
    const { rerender } = render(<Meter pct={50} kind="budget" label="Dining" />)
    expect(screen.getByRole('meter', { name: 'Dining' })).toHaveAttribute('aria-valuenow', '50')
    expect(screen.queryByText(/over budget|nearly/i)).not.toBeInTheDocument()

    rerender(<Meter pct={85} kind="budget" label="Dining" />)
    expect(screen.getByText('Nearly used up')).toBeInTheDocument()

    rerender(<Meter pct={140} kind="budget" label="Dining" />)
    expect(screen.getByText('Over budget')).toBeInTheDocument()
    expect(screen.getByRole('meter')).toHaveAttribute('aria-valuetext', '140%')

    rerender(<Meter pct={100} kind="goal" label="Laptop" />)
    expect(screen.getByText('Achieved')).toBeInTheDocument()
  })

  it('uses the first alert threshold of the budget as the warning point', () => {
    render(<Meter pct={55} kind="budget" warnAt={50} label="Dining" />)
    expect(screen.getByText('Nearly used up')).toBeInTheDocument()
  })
})
