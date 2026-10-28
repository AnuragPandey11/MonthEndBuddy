import { describe, expect, it } from 'vitest'
import { createFpsWatchdog } from './fpsWatchdog'

/** Ticks at a steady frame rate from `from` for `ms` milliseconds; returns the last verdict and time. */
function run(watchdog: ReturnType<typeof createFpsWatchdog>, fps: number, from: number, ms: number) {
  let slow = false
  let t = from
  for (; t <= from + ms; t += 1000 / fps) slow = watchdog.tick(t)
  return { slow, t }
}

describe('fps watchdog', () => {
  it('keeps a shader that runs at 60 fps', () => {
    expect(run(createFpsWatchdog(), 60, 0, 10_000).slow).toBe(false)
  })

  it('drops a shader that stays below 30 fps for two seconds', () => {
    const watchdog = createFpsWatchdog()
    expect(run(watchdog, 20, 0, 1_500).slow).toBe(false)
    expect(run(watchdog, 20, 1_500, 2_100).slow).toBe(true)
    expect(watchdog.tick(99_999)).toBe(true)
  })

  it('ignores the warm-up, where the shader compiles', () => {
    const watchdog = createFpsWatchdog()
    watchdog.tick(0)
    watchdog.tick(450) // one 450 ms frame while compiling
    expect(run(watchdog, 60, 500, 5_000).slow).toBe(false)
  })

  it('does not count a pause as slowness', () => {
    const watchdog = createFpsWatchdog()
    const { t } = run(watchdog, 60, 0, 3_000)
    // The tab was hidden for a minute; frames resume where it left off.
    watchdog.resume(t + 60_000)
    expect(run(watchdog, 60, t + 60_000, 3_000).slow).toBe(false)
  })
})
