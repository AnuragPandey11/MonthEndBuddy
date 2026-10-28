import { lazy, Suspense, useCallback, useState } from 'react'
import { useReducedMotion } from 'motion/react'

// Loaded only where an aurora is shown, so the shader code stays out of the first paint.
const AuroraCanvas = lazy(() => import('./AuroraCanvas'))

/** WebGL 1 exists at all; the canvas still checks that a context can be created. */
function webglAvailable(): boolean {
  return typeof window !== 'undefined' && typeof window.WebGLRenderingContext !== 'undefined'
}

/**
 * A slowly drifting aurora behind decoration (P4.4, architecture §14): only the hero card's pixel chart and the
 * copilot's robot tile, never behind text. The CSS gradient is always underneath, so it shows while the shader
 * loads and stays when it cannot run: reduced motion, no WebGL, a lost context or a frame rate below 30 fps.
 */
export function AuroraBackground({ className = '' }: { className?: string }) {
  const reduce = useReducedMotion()
  const [failed, setFailed] = useState(false)
  const onFallback = useCallback(() => setFailed(true), [])
  const shader = !reduce && !failed && webglAvailable()
  return (
    <div className={`aurora-fallback pointer-events-none absolute inset-0 ${className}`} aria-hidden>
      {shader && (
        <Suspense fallback={null}>
          <AuroraCanvas onFallback={onFallback} />
        </Suspense>
      )}
    </div>
  )
}
