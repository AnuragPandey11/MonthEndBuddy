import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, waitFor } from '@testing-library/react'
import { AuroraBackground } from './AuroraBackground'
import { hexToRgb } from './auroraRenderer'

describe('AuroraBackground', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows the CSS gradient and no canvas where WebGL does not exist', async () => {
    // jsdom has no WebGL, like an old or locked-down browser.
    const { container } = render(<AuroraBackground />)
    expect(container.querySelector('.aurora-fallback')).toBeInTheDocument()
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(container.querySelector('canvas')).toBeNull()
  })

  it('never starts the shader when the user asks for reduced motion', async () => {
    vi.stubGlobal('WebGLRenderingContext', function WebGLRenderingContext() {})
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query.includes('prefers-reduced-motion'),
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }))
    const getContext = vi.spyOn(HTMLCanvasElement.prototype, 'getContext')
    const { container } = render(<AuroraBackground />)
    expect(container.querySelector('.aurora-fallback')).toBeInTheDocument()
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(getContext).not.toHaveBeenCalled()
  })

  it('falls back when a WebGL context cannot be created', async () => {
    vi.stubGlobal('WebGLRenderingContext', function WebGLRenderingContext() {})
    const getContext = vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
    const { container } = render(<AuroraBackground />)
    await waitFor(() => expect(getContext).toHaveBeenCalled())
    await waitFor(() => expect(container.querySelector('canvas')).toBeNull())
    expect(container.querySelector('.aurora-fallback')).toBeInTheDocument()
  })
})

describe('hexToRgb', () => {
  it('reads the token formats styles.css uses', () => {
    expect(hexToRgb('#2f5bf5')).toEqual([0x2f / 255, 0x5b / 255, 0xf5 / 255])
    expect(hexToRgb(' #fff ')).toEqual([1, 1, 1])
    expect(hexToRgb('rgb(1 2 3)')).toEqual([0, 0, 0])
  })
})
