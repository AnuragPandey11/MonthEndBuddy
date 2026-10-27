// The chosen theme, or the system's, applied before first paint so the page never flashes the other one.
// lib/theme.ts owns the same storage key.
;(function () {
  var theme = null
  try {
    theme = localStorage.getItem('meb-theme')
  } catch (e) {
    // Storage can be blocked; fall back to the system preference.
  }
  if (theme !== 'light' && theme !== 'dark') {
    theme = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }
  document.documentElement.dataset.theme = theme
})()
