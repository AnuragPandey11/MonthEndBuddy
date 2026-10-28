# ADR 0002 — Aurora shader in raw WebGL

| | |
|---|---|
| **Status** | **Accepted** (2026-10-28) |
| **Work item** | P4.4 (implementation plan) |
| **Evidence** | The built bundle (`npm run build`), the component tests and a browser review under the production nginx CSP (P4.5) |

## Context

Spec §7 and architecture §14 ask for an animated "aurora" behind the hero card and the copilot header. It has to be lazy-loaded, cap the device pixel ratio at 1.5, pause when the page is hidden or the effect is off-screen, release its GPU resources on unmount, and fall back to a CSS gradient under reduced motion, without WebGL, or below 30 fps for 2 s. Text never sits on it.

The plan framed the choice as a maintained shader library versus a small Three.js implementation.

## Decision

Neither. The aurora is **about 200 lines of raw WebGL 1**: one full-screen triangle and one fragment shader (four octaves of value noise, mixed into the theme's colour tokens).

- `components/aurora/auroraRenderer.ts`: the framework-free renderer (`render`, `resize`, `setColors`, `dispose`).
- `components/aurora/fpsWatchdog.ts`: the 30 fps rule. A 500 ms warm-up absorbs shader compilation; after that, one 2-second window below 30 fps drops the shader for good. Pure and unit-tested.
- `components/aurora/AuroraCanvas.tsx`: the frame loop, loaded through `React.lazy`. It creates and removes its own canvas inside the effect, so a StrictMode re-mount never reuses a lost context. It pauses on `visibilitychange` and `IntersectionObserver`, follows `data-theme` with a `MutationObserver`, and treats `webglcontextlost` as a fallback.
- `components/aurora/AuroraBackground.tsx`: the public component. The `.aurora-fallback` CSS gradient is always underneath it, and the canvas is only attempted when motion is welcome and `WebGLRenderingContext` exists.

### Why not a library or Three.js

- **Size.** The effect is one shader. Three.js is about 600 KB minified (about 150 KB gzipped) for a scene graph we would not use, and shader-background packages bundle their own renderers. The shader chunk is 4.7 KB (2.4 KB gzipped), and nothing enters the main bundle.
- **CSP.** nginx serves `script-src 'self'; style-src 'self'`. Raw WebGL needs neither `eval` nor inline styles. The canvas is styled through the CSSOM, which `style-src` allows.
- **Nothing to pin or patch.** No new dependency means no version to verify, no Dependabot churn and no supply-chain surface for decoration.
- **Control.** The required behaviour (pause, DPR cap, disposal, fps fallback, theme colours) is wrapper code that a library would not have done for us anyway.

## Consequences

- The shader is ours to maintain. It is small, and the CSS fallback is the guaranteed baseline: a broken shader degrades to the gradient, not to an empty box.
- The aurora sits only behind the hero's pixel chart and the copilot's robot tile, so "text never on the shader" holds by construction.
- Tests cover the watchdog with synthetic timestamps and the fallback paths (no WebGL, reduced motion, no context). Real rendering is checked in a browser, not in jsdom.
