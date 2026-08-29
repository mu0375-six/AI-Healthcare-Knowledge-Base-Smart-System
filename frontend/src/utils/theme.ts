const KEY = 'theme.v2'

export type Theme = 'light' | 'dark'
export type ThemeMode = Theme | 'system'

let mode: ThemeMode = 'light'
let systemQuery: MediaQueryList | null = null
let systemListenerBound = false

function isThemeMode(value: string | null): value is ThemeMode {
  return value === 'light' || value === 'dark' || value === 'system'
}

function querySystemTheme() {
  if (!systemQuery && typeof window !== 'undefined' && window.matchMedia) {
    systemQuery = window.matchMedia('(prefers-color-scheme: dark)')
  }
  return systemQuery
}

function resolveTheme(preference: ThemeMode): Theme {
  if (preference !== 'system') return preference
  return querySystemTheme()?.matches ? 'dark' : 'light'
}

function renderTheme(preference: ThemeMode, notify = true) {
  const resolved = resolveTheme(preference)
  document.documentElement.classList.toggle('dark', resolved === 'dark')
  document.documentElement.dataset.themeMode = preference
  if (notify) {
    window.dispatchEvent(
      new CustomEvent('theme-change', { detail: { mode: preference, theme: resolved } }),
    )
  }
}

function bindSystemListener() {
  const query = querySystemTheme()
  if (!query || systemListenerBound) return
  query.addEventListener('change', () => {
    if (mode === 'system') renderTheme(mode)
  })
  systemListenerBound = true
}

export function currentTheme(): Theme {
  return document.documentElement.classList.contains('dark') ? 'dark' : 'light'
}

export function currentThemeMode(): ThemeMode {
  return mode
}

export function applyThemeMode(preference: ThemeMode) {
  mode = preference
  localStorage.setItem(KEY, preference)
  bindSystemListener()
  renderTheme(preference)
}

/** Backwards-compatible fixed-theme setter for existing callers. */
export function applyTheme(theme: Theme) {
  applyThemeMode(theme)
}

export function initTheme() {
  const saved = localStorage.getItem(KEY)
  // The default remains light; following the system is an explicit third choice.
  mode = isThemeMode(saved) ? saved : 'light'
  bindSystemListener()
  renderTheme(mode, false)
}

/** Backwards-compatible shortcut. Leaving system mode pins the opposite theme. */
export function toggleTheme(): Theme {
  const next: Theme = currentTheme() === 'dark' ? 'light' : 'dark'
  applyThemeMode(next)
  return next
}
