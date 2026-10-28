/**
 * The aurora shader (P4.4, ADR 0002): raw WebGL 1, one full-screen triangle and a fragment shader of drifting
 * noise in the theme's colours. Framework-free: the React wrapper owns the canvas, the frame loop and the fallback.
 */

export interface AuroraColors {
  base: string
  a: string
  b: string
  c: string
}

export interface AuroraRenderer {
  render(seconds: number): void
  resize(cssWidth: number, cssHeight: number): void
  setColors(colors: AuroraColors): void
  dispose(): void
}

/** The device pixel ratio is capped: the effect is soft, so extra pixels cost GPU time for nothing. */
export const MAX_DPR = 1.5

const VERTEX = `
attribute vec2 a_pos;
void main() { gl_Position = vec4(a_pos, 0.0, 1.0); }
`

const FRAGMENT = `
precision mediump float;
uniform vec2 u_res;
uniform float u_time;
uniform vec3 u_base;
uniform vec3 u_a;
uniform vec3 u_b;
uniform vec3 u_c;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

float noise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
  float v = 0.0;
  float amp = 0.5;
  for (int i = 0; i < 4; i++) {
    v += amp * noise(p);
    p *= 2.0;
    amp *= 0.5;
  }
  return v;
}

void main() {
  vec2 uv = gl_FragCoord.xy / u_res;
  vec2 p = uv * vec2(u_res.x / u_res.y, 1.0) * 1.6;
  float t = u_time * 0.06;
  float n1 = fbm(p + vec2(t, -t * 0.7));
  float n2 = fbm(p * 1.3 + vec2(-t * 0.8, t) + n1);
  float band = smoothstep(0.25, 0.85, fbm(vec2(p.x * 0.8 + n2, p.y * 2.2 - t)));
  vec3 col = u_base;
  col = mix(col, u_a, smoothstep(0.35, 0.9, n2) * 0.85);
  col = mix(col, u_b, band * 0.55);
  col = mix(col, u_c, smoothstep(0.55, 1.0, n1) * 0.5);
  gl_FragColor = vec4(col, 1.0);
}
`

/** "#2f5bf5" or "#fff" to linear-ish 0..1 RGB; anything else is black, which shows up at once if a token changes. */
export function hexToRgb(hex: string): [number, number, number] {
  const h = hex.trim().replace('#', '')
  const full = h.length === 3 ? [...h].map((ch) => ch + ch).join('') : h
  if (!/^[0-9a-fA-F]{6}$/.test(full)) return [0, 0, 0]
  return [0, 2, 4].map((i) => parseInt(full.slice(i, i + 2), 16) / 255) as [number, number, number]
}

function compile(gl: WebGLRenderingContext, type: number, source: string): WebGLShader | null {
  const shader = gl.createShader(type)
  if (!shader) return null
  gl.shaderSource(shader, source)
  gl.compileShader(shader)
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    gl.deleteShader(shader)
    return null
  }
  return shader
}

/** Returns null when WebGL or the shader is unavailable; the caller then shows the CSS gradient. */
export function createAuroraRenderer(canvas: HTMLCanvasElement, colors: AuroraColors): AuroraRenderer | null {
  const gl = canvas.getContext('webgl', { antialias: false, alpha: false, powerPreference: 'low-power' })
  if (!gl) return null
  const vertex = compile(gl, gl.VERTEX_SHADER, VERTEX)
  const fragment = compile(gl, gl.FRAGMENT_SHADER, FRAGMENT)
  const program = gl.createProgram()
  const buffer = gl.createBuffer()
  if (!vertex || !fragment || !program || !buffer) return null
  gl.attachShader(program, vertex)
  gl.attachShader(program, fragment)
  gl.linkProgram(program)
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) return null
  gl.useProgram(program)

  // One triangle that covers the viewport.
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer)
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW)
  const position = gl.getAttribLocation(program, 'a_pos')
  gl.enableVertexAttribArray(position)
  gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0)

  const uniform = (name: string) => gl.getUniformLocation(program, name)
  const res = uniform('u_res')
  const time = uniform('u_time')
  const slots = { base: uniform('u_base'), a: uniform('u_a'), b: uniform('u_b'), c: uniform('u_c') }

  const setColors = (next: AuroraColors) => {
    for (const key of ['base', 'a', 'b', 'c'] as const) gl.uniform3fv(slots[key], hexToRgb(next[key]))
  }
  setColors(colors)

  return {
    render(seconds) {
      gl.uniform1f(time, seconds)
      gl.drawArrays(gl.TRIANGLES, 0, 3)
    },
    resize(cssWidth, cssHeight) {
      const dpr = Math.min(window.devicePixelRatio || 1, MAX_DPR)
      const width = Math.max(1, Math.round(cssWidth * dpr))
      const height = Math.max(1, Math.round(cssHeight * dpr))
      if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width
        canvas.height = height
        gl.viewport(0, 0, width, height)
      }
      gl.uniform2f(res, width, height)
    },
    setColors,
    dispose() {
      gl.deleteBuffer(buffer)
      gl.deleteProgram(program)
      gl.deleteShader(vertex)
      gl.deleteShader(fragment)
      gl.getExtension('WEBGL_lose_context')?.loseContext()
    },
  }
}
