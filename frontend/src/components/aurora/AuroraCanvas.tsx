import { useEffect, useRef } from 'react'
import { createAuroraRenderer, type AuroraColors } from './auroraRenderer'
import { createFpsWatchdog } from './fpsWatchdog'

/** The theme's colours for the shader, read from the design tokens so dark mode gets its own steps. */
function themeColors(): AuroraColors {
  const css = getComputedStyle(document.documentElement)
  const token = (name: string) => css.getPropertyValue(name).trim()
  return {
    base: token('--color-elevated'),
    a: token('--color-accent'),
    b: token('--color-lime'),
    c: token('--color-series-income'),
  }
}

/**
 * The WebGL half of `AuroraBackground`, loaded on demand. It creates its own canvas inside the effect (so a
 * StrictMode re-mount never reuses a lost context), draws only while the page is visible and the canvas on screen,
 * re-reads colours when the theme changes, and calls `onFallback` for good when WebGL is missing, the context is
 * lost or the frame rate stays below 30 fps.
 */
export default function AuroraCanvas({ onFallback }: { onFallback: () => void }) {
  const host = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const container = host.current
    if (!container) return
    const canvas = document.createElement('canvas')
    canvas.setAttribute('aria-hidden', 'true')
    canvas.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;display:block'
    container.appendChild(canvas)

    const renderer = createAuroraRenderer(canvas, themeColors())
    if (!renderer) {
      canvas.remove()
      onFallback()
      return
    }

    const watchdog = createFpsWatchdog()
    let frame = 0
    let visible = !document.hidden
    let onScreen = true
    let running = false
    let stopped = false
    const started = performance.now()

    const stop = () => {
      running = false
      cancelAnimationFrame(frame)
    }
    const fail = () => {
      if (stopped) return
      stopped = true
      stop()
      onFallback()
    }
    const loop = (now: number) => {
      if (watchdog.tick(now)) {
        fail()
        return
      }
      renderer.render((now - started) / 1000)
      frame = requestAnimationFrame(loop)
    }
    const update = () => {
      const shouldRun = visible && onScreen && !stopped
      if (shouldRun && !running) {
        running = true
        watchdog.resume(performance.now())
        frame = requestAnimationFrame(loop)
      } else if (!shouldRun && running) {
        stop()
      }
    }

    const size = () => renderer.resize(container.clientWidth, container.clientHeight)
    size()
    const resizeObserver = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(size)
    resizeObserver?.observe(container)

    const onVisibility = () => {
      visible = !document.hidden
      update()
    }
    document.addEventListener('visibilitychange', onVisibility)
    const intersection =
      typeof IntersectionObserver === 'undefined'
        ? null
        : new IntersectionObserver(([entry]) => {
            onScreen = entry?.isIntersecting ?? true
            update()
          })
    intersection?.observe(container)

    // The theme toggle rewrites data-theme on <html>; the shader follows it.
    const themeObserver = new MutationObserver(() => renderer.setColors(themeColors()))
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })

    canvas.addEventListener('webglcontextlost', fail)
    update()

    return () => {
      stopped = true
      stop()
      document.removeEventListener('visibilitychange', onVisibility)
      intersection?.disconnect()
      resizeObserver?.disconnect()
      themeObserver.disconnect()
      canvas.removeEventListener('webglcontextlost', fail)
      renderer.dispose()
      canvas.remove()
    }
  }, [onFallback])

  return <div ref={host} className="absolute inset-0" aria-hidden />
}
