/**
 * Decides when the aurora is too slow to keep (architecture §14): after a warm-up that absorbs shader compilation,
 * the frame rate is measured over consecutive windows, and one window below the floor means fall back for good.
 * Pure, so it is tested with synthetic timestamps.
 */
export interface FpsWatchdog {
  /** Call once per frame with the rAF timestamp (ms). Returns true once the shader should be dropped. */
  tick(now: number): boolean
  /** Call when frames resume after a pause (hidden tab, off-screen), so the gap is not counted as slowness. */
  resume(now: number): void
}

export function createFpsWatchdog({
  minFps = 30,
  windowMs = 2000,
  warmupMs = 500,
}: { minFps?: number; windowMs?: number; warmupMs?: number } = {}): FpsWatchdog {
  let start: number | null = null
  let windowStart: number | null = null
  let frames = 0
  let tooSlow = false

  return {
    tick(now) {
      if (tooSlow) return true
      if (start === null) start = now
      if (now - start < warmupMs) return false
      if (windowStart === null) {
        windowStart = now
        frames = 0
        return false
      }
      frames++
      const elapsed = now - windowStart
      if (elapsed >= windowMs) {
        if ((frames * 1000) / elapsed < minFps) tooSlow = true
        windowStart = now
        frames = 0
      }
      return tooSlow
    },
    resume(now) {
      if (tooSlow) return
      windowStart = now
      frames = 0
    },
  }
}
